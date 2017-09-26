(ns googlica.auth
  (:gen-class)
  (:require [clj-http.client :as http]
            [cheshire.core :refer [parse-string generate-string]]))


;;;; Key management

(def ^:dynamic *json-key* nil)

(defn set-key!
  "Updates the dynamic var *json-key* with the provided data"
  [new-key]
  (alter-var-root #'*json-key* (constantly new-key)))

(defn key-from-file
  "Given a file path containing the json service account key,
  read it, convert it to a clojure map, and save it in the *json-key* var"
  [path]
  (-> (slurp path)
      (parse-string true)
      (set-key!)
      (select-keys [:client_email :project_id])))
