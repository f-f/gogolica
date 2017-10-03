(ns gogolica.gen.model
  "Loading and processing of the API discovery models."
  (:gen-class)
  (:require [cheshire.core :as json]
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
                      (-> resource
                          (update :methods
                                  #(select-keys % wanted-methods)))])))
                (into {})))))))

(defn all-methods
  "Given API model walks it and returns a list of methods."
  [model]
  (->> model
       :resources
       vals
       (map :methods)
       (mapcat vals)))

(defn split-required-params
  "Returns a vector of two maps: first with the required params,
   second with the optional."
  [parameters]
  (->> parameters
       ((juxt filter remove) (fn [[k v]] (:required v)))
       (mapv #(into {} %))))

(def storage-model (model-for :storage :v1))

(def storage-object-get (-> storage-model :resources :objects :methods :get))
