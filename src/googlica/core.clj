(ns googlica.core
  (:gen-class)
  (:require [cheshire.core :as json]
            [clojure.string :as s]
            [clojure.set :as st]
            [me.raynes.fs :as fs]
            [clj-http.client :as http]
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

(defn split-required-params
  "Returns a vector of two maps: first with the required params, second with the optional"
  [method]
  (->> (:parameters method)
       ((juxt filter remove) (fn [[k v]] (:required v)))
       (mapv #(into {} %)))) 

(defn generate-function-name
  "Generates a symbol in the form of 'verb-resource', from a method map."
  [method]
  (let [[_ resource-name method-name] (s/split (:id method) #"\.")]
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

(defn template->path-vector
  ;;  "/b/{fooBar}/c/{bar} => ["/b/" foo-bar "/c/" bar ]"
  [path-template arg-names]
  (loop [result []
         vars arg-names
         template path-template]
    (println {:result result})
    (println {:vars vars})
    (println {:template template})    
    (if-let [var (first vars)]
      (let [placeholder-re (re-pattern (str "\\{" var "\\}"))
            value-symbol (symbol (->kebab-case var))
            [pre post] (s/split template placeholder-re)]
        (recur
         ;; result
         (concat
          result
          [pre value-symbol])
         ;; vars
         (rest vars)
         ;; template
         post
         ))
      result)))

;; > (replace-path-vars "b/{bucket}/o/{object}")
;; => (str "b/" bucket "/o/" object)
(defn generate-path [method]
  (let [path-vector (template->path-vector
                     (:path method)
                     (->> method
                          :parameters
                          (filter (fn [[k v]](= "path" (:location v))))
                          keys
                          (map name)))]
    `(str ~@path-vector)))


(defn generate-request [base-url method]
  {:method (-> method :httpMethod s/lower-case keyword)
   :url (str base-url (generate-path method))}) 

(defn generate-function-from-method
  [base-url method]
  `(defn ~(generate-function-name method)
     ~(generate-docs method)
     ~(generate-args method)
     (http/request ~(generate-request base-url method))))

(generate-function-from-method base-url storage-object-get)
