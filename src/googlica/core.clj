(ns googlica.core
  (:gen-class)
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.set :as set]
            [me.raynes.fs :as fs]
            [clj-http.client :as http]
            [fipp.clojure :as f]
            [camel-snake-kebab.core :refer :all]))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))

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

(def base-url (-> storage-model :baseUrl))

(defn generate-ns-sexp
  "Generates the ns declaration for an API.
  Takes name, version, description and docs link, all strings."
  [name version desc docs-link]
  `(ns
    ~(symbol (str "googlica." name "." version)) ;; HACK: instead of having the ns as string, we should probably read it from the current one
    ~(str desc "\n\nDocumentation link: " docs-link)
    (:gen-class)
    (:require [clj-http.client :as http])))

(defn generate-global-vars
  "Generates global variables that are used throughout the namespace"
  [root-url service-path]
  `[(def base-url ~(str root-url service-path))])

(defn split-required-params
  "Returns a vector of two maps: first with the required params, second with the optional"
  [method]
  (->> (:parameters method)
       ((juxt filter remove) (fn [[k v]] (:required v)))
       (mapv #(into {} %))))

(defn generate-function-name
  "Generates a symbol in the form of 'verb-resource', from a method map."
  [method]
  (let [[_ resource-name method-name] (str/split (:id method) #"\.")]
    (-> (str method-name "-" resource-name)
        ->kebab-case-symbol)))

(defn generate-docs [method]
  (str (:description method)
       "\n")) ;; TODO: add description for parameters

(defn generate-args [method]
  (let [[required optional] (->> method
                                 split-required-params
                                 (mapv (comp (partial mapv ->kebab-case-symbol) keys)))]
    `[~@required {:keys ~optional}]))

;; > ( template->path-vector "b/{fooBar}/o/{bar}" ["fooBar" "bar"])
;; => ["/b/" foo-bar "/c/" bar ]
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

;; > (replace-path-vars "b/{bucket}/o/{object}")
;; => (str "b/" bucket "/o/" object)
(defn generate-path [method]
  (template->path-vector (:path method)
                         (->> method
                              :parameters
                              (filter (fn [[k v]](= "path" (:location v))))
                              keys
                              (map name))))

(defn generate-request
  "Generates the request map to be passed to the http library.
  NB: uses the `base-url` symbol, it should be generated in the ns including the method."
  [method]
  {:method (-> method :httpMethod str/lower-case keyword)
   :url `(str base-url ~@(generate-path method))})

(defn generate-function-from-method
  [method]
  `(defn ~(generate-function-name method)
     ~(generate-docs method)
     ~(generate-args method)
     (http/request ~(generate-request method))))

(defn generate-ns-file
  "Given a service model, generates a clojure namespace with the implementation
  of all the API methods as functions, and writes it to file."
  [{:keys [name version description documentationLink rootUrl servicePath] :as model}]
  (let [symbols->str #(-> %
                          (f/pprint {:width 100}) ;; Fipp pretty prints clojure code nicely
                          with-out-str)]
    (->> [[(generate-ns-sexp name version description documentationLink)]
          (generate-global-vars rootUrl servicePath)]
          ;;(mapv generate-function-from-method ;; TODO get all the methods)])))
         (apply concat) ;; <- this is for flattening the methods
         (mapv symbols->str)
         (str/join "\n\n"))))
