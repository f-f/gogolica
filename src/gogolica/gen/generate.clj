(ns gogolica.gen.generate
  "A namespace for functions that do most of the actual codegen of gogolica."
  (:gen-class)
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.set :as set]
            [me.raynes.fs :as fs]
            [clj-http.client :as http]
            [fipp.clojure :as f]
            [cheshire.core :refer [generate-string parse-string]]
            [camel-snake-kebab.core :refer :all]
            [gogolica.gen.model :as model]))

(defn generate-ns-declaration
  "Generates the ns declaration for the given API model."
  [{name :name
    version :version
    description :description
    documentation-link :documentationLink}]
  `(~'ns
    ~(symbol (str "gogolica." name "." version)) ;; HACK: instead of having the ns as string, we should probably read it from the current one
    ~(str description "\n\n"
          "Documentation link: " documentation-link)
    (:gen-class)
    (:require [gogolica.core.common :refer [~'?assoc ~'exec-http] :as ~'common]
              [gogolica.core.auth
               :refer [~'authenticated?
                       ~'read-application-credentials]
               :as ~'auth]
              [cheshire.core :refer [~'generate-string]]
              [clojure.string :as ~'str])))

(defn generate-global-vars
  "Generates global variables that are used throughout the generated
   namespace for the given API model"
  [{root-url :rootUrl
    service-path :servicePath}]
  `[(def ~'root-url ~root-url)
    (def ~'base-url ~(str root-url service-path))])

(defn generate-function-name
  "Generates a symbol in the form of 'verb-resource', from a method id."
  [id]
  (let [[_ resource-name method-name] (str/split id #"\.")]
    (-> (str resource-name "-" method-name)
        ->kebab-case-symbol)))

(defn generate-docs [method]
  (str (:description method)
       "\n")) ;; TODO: add description for parameters

(defn generate-args
  "Given a method model, uses a list of parameter maps and the parameterOrder vector,
  to generate the arguments vector.
  If the request is not nil, then the method also takes a request object,
  that gets specified as the first parameter."
  [{parameters :parameters
    parameter-order :parameterOrder
    request :request
    media-upload :mediaUpload
    media-download :supportsMediaDownload}]
  (let [[required optional] (->> parameters
                                 (merge (when media-download
                                          {:alt {:position "query"}}))
                                 model/split-required-params
                                 (mapv (comp (partial mapv ->kebab-case-symbol) keys)))
        ;; parameter-order also contains the required params, so we get them from there
        required (mapv ->kebab-case-symbol parameter-order)
        request-sym (when request
                      (->kebab-case-symbol (get request :$ref)))
        required (if request-sym
                   (cons request-sym required)
                   required)
        required (if media-upload
                   (cons 'file-path required)
                   required)]
    `[~@required ~(hash-map :keys optional :as 'optional-params)]))


(defn template->path-vector
  [path-template arg-names]
  (let [args->symbols (->> arg-names
                           (mapv #(hash-map % (->kebab-case-symbol %)))
                           (apply merge))
        ;; Returns a list of vectors, where the first element is the match including
        ;; the curly brackets, and the second element is without.
        matches (re-seq #"\{(.+?)\}" path-template)
        ;; Helper function in which we iterate on the template string,
        ;; matching on the first pair of curly braces and adding the
        ;; match to the accumulator vector, recurring on more matches
        template->path-vector'
        (fn [result-acc template matches]
          (if-some [[match arg] (first matches)]
            (let [[pre post] (str/split template (re-pattern (str "\\{" arg "\\}")))]
              (recur (concat result-acc [pre (get args->symbols arg)])
                     post
                     (rest matches)))
            (if template ;; we have no matches anymore, but there might be some string still
              (concat result-acc [template])
              result-acc)))]
    (template->path-vector' [] path-template matches)))

(defn generate-path
  [template-uri parameters]
  (template->path-vector template-uri
                         (->> parameters
                              (filter (fn [[k v]](= "path" (:location v))))
                              keys
                              (mapv name))))

;; TODO: refactor this crap
(defn generate-request
  "Generates the request map to be passed to the http library.
  NB: uses the `base-url` symbol, it should be generated in the ns including the method."
  [{http-method :httpMethod
    path :path
    parameters :parameters
    request :request
    media-upload :mediaUpload
    media-download :supportsMediaDownload}]
  (let [query-params (->> parameters
                          (merge (when media-download
                                   {:alt {:location "query"}}))
                          (filter (fn [[_ v]] (= (:location v) "query")))
                          (mapv   (fn [[k _]] (name k))))
        method (-> http-method str/lower-case keyword)
        ;; If there's a :request key in the model, we take the clojure map
        ;; that should be passed as the object, convert it to json, and
        ;; attach it to the body.
        ;; But if there's a mediaUpload key then we should upload a file, so the
        ;; :request key here indicates the schema of the file metadata.
        ;; FIXME: we currently ignore the file metadata and just require the `name`
        ;; of the object, and its `path`. Therefore the body is going to be an input-stream
        ;; reading from `path`. To be fixed when we 1. start using schemas, and
        ;; 2. implement Multipart or Resumable upload.
        body (cond
               media-upload `(clojure.java.io/input-stream ~'file-path)
               request `(~'generate-string ~(->kebab-case-symbol (get request :$ref)))
               :else "")]
    {:method method
     ;; Generate code to build the query params map with only the parameters
     ;; that are not nil (so have been passed in)
     :query-params `(~'?assoc ~(if media-upload
                                 {"uploadType" "media"}
                                 {})
                              ~@(mapcat (fn [p] [p (->kebab-case-symbol p)])
                                        query-params))
     :url (if media-upload
            `(~'str ~'root-url
                    ~@(generate-path (-> media-upload :protocols :simple :path)
                                     parameters))
            `(~'str ~'base-url
                    ~@(generate-path path parameters)))
     :content-type (if media-upload
                     `(java.net.URLConnection/guessContentTypeFromStream ~body)
                     :json)
     ;; Auto coercion of return types nicely provided by clj-http
     :as (if media-download
           `(~'if (~'= ~'alt "media")
             :byte-array
             :json)
           :json)
     :body body}))

(defn generate-function-from-method
  [{:keys [id scopes] :as method}]
  `(~'defn ~(generate-function-name id)
     ~(generate-docs method)
     ~(generate-args method)
     ;(~'println ~(generate-request method))
     (~'exec-http ~(generate-request method)
                  ~scopes)))

(defn pprint-symbols
  [symbols]
  (-> symbols
      (f/pprint {:width 100}) ;; Fipp pretty prints clojure code nicely
      with-out-str))

(defn generate-ns
  "Given a service model, generates a clojure namespace with the implementation
   of all the API methods as functions and returns it as a pretty string."
  [model]
  (let [ns-declaration (generate-ns-declaration model)
        global-vars (generate-global-vars model)
        functions (->> model
                       model/all-methods
                       (map generate-function-from-method))]
    (->> (concat
          [ns-declaration]
          global-vars
          functions)
         (map pprint-symbols)
         (str/join "\n\n"))))
