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

(defn generate-docs [method]
  (str (:description method)
       "\n")) ;; TODO: add description for parameters

(defn generate-args
  "Given a method model, uses a list of parameter maps and the parameterOrder vector,
  to generate the arguments vector.
  If the request is not nil, then the method also takes a request object,
  that gets specified as the first parameter."
  [{parameter-order :parameterOrder
    request :request
    :as method}]
  (let [[required optional] (->> method model/method-parameters
                                 model/split-required-params
                                 (mapv (comp (partial mapv ->kebab-case-symbol) keys)))
        ;; parameter-order also contains the required params, so we get them from there
        required (mapv ->kebab-case-symbol parameter-order)
        request-sym (when request
                      (->kebab-case-symbol (get request :$ref)))
        required (if request-sym
                   (cons request-sym required)
                   required)
        required (if (model/media-upload? method)
                   (cons 'file-path required)
                   required)]
    `[~@required ~(hash-map :keys optional :as 'optional-params)]))


;; TODO: refactor this crap
(defn generate-request
  "Generates the request map to be passed to the http library.
  NB: uses the `base-url` symbol, it should be generated in the ns including the method."
  [{request :request :as method}]
  (let [query-params (->> method model/method-query-parameters
                          (mapv  (comp name key)))
        http-method (model/method-http-method method)
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
               (model/media-upload? method) `(clojure.java.io/input-stream ~'file-path)
               request `(~'generate-string ~(->kebab-case-symbol (get request :$ref)))
               :else "")]
    {:method http-method
     ;; Generate code to build the query params map with only the parameters
     ;; that are not nil (so have been passed in)
     :query-params `(~'?assoc ~(if (model/media-upload? method)
                                 {"uploadType" "media"}
                                 {})
                              ~@(mapcat (fn [p] [p (->kebab-case-symbol p)])
                                        query-params))
     :url (if (model/media-upload? method)
            `(~'str ~'root-url
                    ~@(model/method-simple-upload-path method))
            `(~'str ~'base-url
                    ~@(model/method-path method)))
     :content-type (if (model/media-upload? method)
                     `(java.net.URLConnection/guessContentTypeFromStream ~body)
                     :json)
     ;; Auto coercion of return types nicely provided by clj-http
     :as (if (model/media-download? method)
           `(~'if (~'= ~'alt "media")
             :byte-array
             :json)
           :json)
     :body body}))

(defn generate-function-from-method
  [{:keys [scopes] :as method}]
  `(~'defn ~(model/method-ident method)
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
