(ns gogolica.model
  "Loading and processing of the API discovery models."
  (:gen-class)
  (:require [cheshire.core :as json]
            [me.raynes.fs :as fs]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]))

(def model-paths
  (->> (fs/find-files "model" #".*\.json")
       (mapv #(.getPath %))))

(defn read-model [path]
  (-> path
      slurp
      (json/decode ->kebab-case-keyword)))

(def model-maps (mapv read-model model-paths))

(def storage-model (first (filter #(= (:id %) "storage:v1") model-maps)))

(def storage-object-get (-> storage-model :resources :objects :methods :get))
