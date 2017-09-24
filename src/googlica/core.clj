(ns googlica.core
  (:gen-class)
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.set :as set]
            [me.raynes.fs :as fs]
            [clj-http.client :as http]
            [fipp.clojure :as f]
            [camel-snake-kebab.core :refer :all]))

(def model-paths
  (->> (fs/find-files "model" #".*\.json")
       (mapv #(.getPath %))))

(defn read-model [path]
  (-> path
      slurp
      (json/decode true)))

(def model-maps (mapv read-model model-paths))

(def storage-model (first (filter #(= (:id %) "storage:v1") model-maps)))

(def storage-object-get (-> storage-model :resources :objects :methods :get))

(defn generate-ns-sexp
  "Generates the ns declaration for an API.
  Takes name, version, description and docs link, all strings."
  [name version desc docs-link]
  `(~'ns
    ~(symbol (str "googlica." name "." version)) ;; HACK: instead of having the ns as string, we should probably read it from the current one
    ~(str desc "\n\nDocumentation link: " docs-link)
    (:gen-class)
    (:require [clj-http.client :as http]
              [clojure.string :as str])))

;; TODO move util generation to another function or a dedicated namespace
(defn generate-global-vars
  "Generates global variables that are used throughout the generated namespace"
  [root-url service-path]
  `[(def ~'base-url ~(str root-url service-path))
    (def ~'*api-key* nil)
    ~'(defn ?assoc
        "Same as assoc, but skip the assoc if v is nil"
        [m & kvs]
        (->> kvs
             (partition 2)
             (filter second)
             (map vec)
             (into m)))])


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
    (-> (str method-name "-" resource-name)
        ->kebab-case-symbol)))

(defn generate-docs [method]
  (str (:description method)
       "\n")) ;; TODO: add description for parameters

(defn generate-args
  "Given a list of parameter maps and the parameterOrder vector,
  generates the arguments vector."
  [parameters parameterOrder]
  (let [[required optional] (->> parameters
                                 split-required-params
                                 (mapv (comp (partial mapv ->kebab-case-symbol) keys)))
        ;; parameterOrder also contains the required params, so we get them from there
        required (mapv ->kebab-case-symbol parameterOrder)]
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
            (let [[pre post] (str/split template
                                      (re-pattern (str "\\{" arg "\\}")))]
              (recur (concat result-acc [pre (get args->symbols arg)])
                     post
                     (rest matches)))
            result-acc))]
    (template->path-vector' [] path-template matches)))

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
  [http-method path parameters]
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
  [{:keys [id httpMethod parameters path parameterOrder] :as method}]
  `(~'defn ~(generate-function-name id)
     ~(generate-docs method)
     ~(generate-args parameters parameterOrder)
     (http/request ~(generate-request httpMethod path parameters))))

(defn generate-ns-file
  "Given a service model, generates a clojure namespace with the implementation
  of all the API methods as functions, and writes it to file."
  [{:keys [name version description documentationLink rootUrl servicePath]
    :as model}]
  (let [symbols->str #(-> %
                          (f/pprint {:width 100}) ;; Fipp pretty prints clojure code nicely
                          with-out-str)]
    (->> [[(generate-ns-sexp name version description documentationLink)]
          (generate-global-vars rootUrl servicePath)]
          ;;(mapv generate-function-from-method ;; TODO get all the methods)])))
         (apply concat) ;; <- this is for flattening the methods
         (mapv symbols->str)
         (str/join "\n\n"))))
