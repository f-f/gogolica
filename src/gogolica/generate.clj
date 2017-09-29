(ns gogolica.generate
  "A namespace for functions that do most of the actual codegen of gogolica."
  (:gen-class)
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.set :as set]
            [me.raynes.fs :as fs]
            [clj-http.client :as http]
            [gogolica.auth :as auth]
            [gogolica.common :as common]
            [gogolica.model :as model]
            [fipp.clojure :as f]
            [cheshire.core :refer [generate-string parse-string]]
            [camel-snake-kebab.core :refer :all]))

(defn generate-ns-declaration
  "Generates the ns declaration for the given API model."
  [{:keys [name version description documentation-link]}]
  `(~'ns
    ~(symbol (str "gogolica." name "." version)) ;; HACK: instead of having the ns as string, we should probably read it from the current one
    ~(str description "\n\n"
          "Documentation link: " documentation-link)
    (:gen-class)
    (:require [gogolica.common :refer [~'?assoc ~'exec-http] :as ~'common]
              [clojure.string :as ~'str])))

(defn generate-global-vars
  "Generates global variables that are used throughout the generated
   namespace for the given API model"
  [{:keys [root-url service-path]}]
  `[(def ~'base-url ~(str root-url service-path))])

(defn split-required-params
  "Returns a vector of two maps: first with the required params, second with the optional"
  [parameters]
  (->> parameters
       ((juxt filter remove) (fn [[k v]] (:required v)))
       (mapv #(into {} %))))

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
  [{:keys [parameters parameter-order request]}]
  (let [[required optional] (->> parameters
                                 split-required-params
                                 (mapv (comp (partial mapv ->kebab-case-symbol) keys)))
        ;; parameter-order also contains the required params, so we get them from there
        required (mapv ->kebab-case-symbol parameter-order)
        request-sym (when request
                      (->kebab-case-symbol (get request :$ref)))
        required (if request-sym
                   (cons request-sym required)
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
            result-acc))]
    ;; When path template does not have vars to substitute, shortcircuit
    (if (seq matches)
      (template->path-vector' [] path-template matches)
      (list path-template))))

(defn generate-path
  [template-uri parameters]
  (template->path-vector template-uri
                         (->> parameters
                              (filter (fn [[k v]](= "path" (:location v))))
                              keys
                              (mapv name))))

(defn generate-request
  "Generates the request map to be passed to the http library.
  NB: uses the `base-url` symbol, it should be generated in the ns including the method."
  [{:keys [http-method path parameters]}]
  (let [query-params (->> parameters
                          (filter (fn [[_ v]] (= (:location v) "query")))
                          (mapv   (fn [[k _]] (name k))))
        method (-> http-method str/lower-case keyword)
        body {}] ;; TODO implement body for POSTs
    {:method method
     :url `(~'str ~'base-url
            ~@(generate-path path parameters))
     :content-type :json
     :body body
     ;; Generate code to build the query params map with only the parameters
     ;; that are not nil (so have been passed in)
     :query-params `(~'?assoc
                     ~'{"key" *api-key*}
                     ~@(mapcat (fn [p]
                                 [p (->kebab-case-symbol p)])
                               query-params))}))


(defn generate-function-from-method
  [{:keys [id] :as method}]
  `(~'defn ~(generate-function-name id)
     ~(generate-docs method)
     ~(generate-args method)
     (~'println ~(generate-request method))
    (~'http/request ~(generate-request method))))

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
        functions (->> (:resources model)
                       (mapcat
                        (fn [[resource-id resource]]
                          (for [[method-id method] (:methods resource)]
                            (generate-function-from-method method)))))]
    (->> (concat
          [ns-declaration]
          global-vars
          functions)
         (map pprint-symbols)
         (str/join "\n\n"))))
