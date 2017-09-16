(ns googlica.core
  (:gen-class)
  (:require [cheshire.core :as json]
            [clojure.string :as s]
            [clojure.set :as st]
            [me.raynes.fs :as fs]
            [clj-http.client :as http]))

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

(defn get-required-params
  "Returns a map with only the required params"
  [method]
  (->> (:parameters method)
       (filter (fn [[k v]] (:required v)))
       (into {})))

(defn generate-method-name [method]
  'get) 

(defn generate-docs [method]
  "Some docs") 

(defn generate-args [method]
  '[arg1]) 

(defn generate-request [method]
  {:method "get"
   :url "whatever"}) 

(defn generate-function-from-method
  [method]
  `(defn ~(generate-method-name method)
     ~(generate-docs method)
     ~(generate-args method)
     (http/request ~(generate-request method)))) 
