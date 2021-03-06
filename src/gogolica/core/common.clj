(ns gogolica.core.common
  (:gen-class)
  (:require [gogolica.core.auth :as auth]
            [cheshire.core :refer [parse-string generate-string]]
            [clj-http.client :as http]))

(defn ?assoc
  "Same as assoc, but skip the assoc if v is nil"
  [m & kvs]
  (->> kvs
       (partition 2)
       (filter second)
       (map vec)
       (into m)))

(defn exec-http
  "Given a request map, executes it while taking care of authentication
  and retries"
  [req-map scopes]
  (when-not auth/authenticated?
    (auth/read-application-credentials))
  (-> req-map
      (auth/wrap-auth scopes)
      (http/request) ; TODO: handle exceptions and retry here
      (:body)))

;; TODO: handle 409 exception, when bucket name is already taken
