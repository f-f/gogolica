(ns gogolica.gen.model
  "Loading and processing of the API discovery models."
  (:gen-class)
  (:require [clojure.string :as str]
            [cheshire.core :as json]
            [me.raynes.fs :as fs]
            [camel-snake-kebab.core :refer :all]))

(def model-paths
  (->> (fs/find-files "model" #".*\.json")
       (mapv #(.getPath %))))

(defn read-model [path]
  (-> path
      slurp
      (json/decode true)))

(def model-maps (mapv read-model model-paths))

(defn model-for
  "Given API identifier and version as keywords, finds the corresponding model."
  [id version]
  (->> model-maps
       (filter #(= (:id %) (str (name id) ":" (name version))))
       first))

(defn select-resource-methods
  "Restricts API model to a specified subset of resources and methods.
   Takes a map where the keys are the resources and values are
   the lists of methods, and selects only those resources with
   those methods."
  [model resource-methods]
  (-> model
      (update
       :resources
       (fn [resources]
         (let [wanted-resources (keys resource-methods)]
           (->> (select-keys resources wanted-resources)
                (map
                 (fn [[resource-id resource]]
                   (let [wanted-methods (resource-methods resource-id)]
                     [resource-id
                      (merge
                       resource
                       (when-let [methods (:methods resource)]
                         {:methods (select-keys methods wanted-methods)})
                       (when (map? wanted-methods)
                         (when-let [resources (:resources resource)]
                            (select-resource-methods
                             {:resources resources}
                             wanted-methods))))])))
                (into {})))))))

(defn all-methods
  "Given API model walks it and returns a list of methods."
  [model]
  (->> model
       :resources
       vals
       (mapcat
        #(cond
           (some? (:methods %))   (-> % :methods vals)
           (some? (:resources %)) (-> % all-methods)))))

(defn split-required-params
  "Returns a vector of two maps: first with the required params,
   second with the optional."
  [parameters]
  (->> parameters
       ((juxt filter remove) (fn [[k v]] (:required v)))
       (mapv #(into {} %))))

(defn media-upload?
  "Checks whether the given method supports uploading media."
  [method]
  (and (-> method :supportsMediaUpload)
       (-> method :mediaUpload)))

(defn media-upload
  "Get the details on what protocols are supported and what kind of media
   can be uploaded for given method."
  [method]
  (-> method :mediaUpload))

(defn media-download?
  "Checks whether a given method provides downloadable media."
  [method]
  (-> method :supportsMediaDownload boolean))

(defn media-download-service?
  [method]
  (-> method :useMediaDownloadService boolean))


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

(defn method-parameters
  [method]
  (merge
   (-> method :parameters)
   (when (media-download? method)
     {:alt {:position "query"}})))

(defn method-path-parameters
  [method]
  (->> method method-parameters
       (filter #(-> % val :location (= "path")))))

(defn method-query-parameters
  [method]
  (->> method method-parameters
       (filter #(-> % val :location (= "query")))))

(defn method-path
  "The path template of a given method.
   Represented as a vector of strings and symbols."
  [method]
  (-> method :path
      (template->path-vector
       (->> method
            method-path-parameters
            keys
            (mapv name)))))

(defn method-simple-upload-path
  [method]
  (some-> method media-upload :protocols :simple :path
          (template->path-vector
           (->> method
                method-path-parameters
                keys
                (mapv name)))))

(defn method-http-method
  "Returns the http method of a given method as a ring-compatible keyword."
  [method]
  (-> method :httpMethod str/lower-case keyword))

(defn method-ident
  "Returns an identifier for a given method.
   Identifier is a symbol and has a form of 'resource-method'"
  [method]
  (let [[service-name resource-name method-name] (str/split (:id method) #"\.")]
    (->kebab-case-symbol
     (str resource-name "-" method-name))))

(def storage-model (model-for :storage :v1))

(def storage-object-get (-> storage-model :resources :objects :methods :get))
