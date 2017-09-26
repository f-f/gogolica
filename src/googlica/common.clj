(ns googlica.common
  (:gen-class)
  (:require [googlica.auth :as auth]
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
  [req-map scope]
  ;; TODO accept multiple scopes and try requesting multiple of them
  (-> req-map
      (auth/wrap-auth scope)
      (http/request)
      :body ; TODO: handle exceptions and retry here
      (parse-string true)))
