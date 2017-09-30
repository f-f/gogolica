(ns gogolica.core.auth
  (:gen-class)
  (:require [clj-http.client :as http]
            [buddy.sign.jwt :as jwt]
            [clj-time.core :as time]
            [clojure.core.cache :as cache]
            [buddy.core.keys :as keys]
            [cheshire.core :refer [parse-string generate-string]]
            [clojure.string :as str]))


;;;; Key management

(def ^:dynamic *service-account* nil)

(defn set-key!
  "Updates the dynamic var *service-account* with the provided data"
  [new-key]
  (alter-var-root #'*service-account* (constantly new-key)))

(defn key-from-file
  "Given a file path containing the json service account key,
  read it, convert it to a clojure map, and save it in the *service-account* var"
  [path]
  (-> (slurp path)
      (parse-string true)
      (set-key!)
      (select-keys [:client_email :project_id])))

(defn read-application-credentials
  "Google specification for effortless auth on their services:
  https://developers.google.com/identity/protocols/application-default-credentials
  Supporting only reading from the standard environment variable for now;
  this function reads the path from the variable, and loads the service account key
  into the *service-account* var"
  []
  (try (key-from-file (System/getenv "GOOGLE_APPLICATION_CREDENTIALS"))
       (catch Exception e nil)))

(defn authenticated?
  "If we are authenticated, return the email of the loaded service account"
  []
  (:client_email *service-account*))

;;;; JWT and OAuth2

(def access-request-url
  "URL for requesting new OAuth2 tokens"
  "https://www.googleapis.com/oauth2/v4/token")

(def C
  "Cache for role:token so we don't have to get a new token for every request"
  (atom (cache/ttl-cache-factory {} :ttl 3500000))) ;; 3500 seconds

(defmacro with-cache
  "Given a key and an expression, will set the key in the cache to the result
  of evaluating it if that key is expired."
  [key body-expr]
  `(let [new-cache# (if (cache/has? @C ~key)
                      (cache/hit @C ~key)
                      (cache/miss @C ~key ((fn [] ~body-expr))))]
     (reset! C new-cache#)
     (get new-cache# ~key)))

(defn sign-jwt
  "Given a scope string, generates a new signed JWT claim"
  [scope]
  (let [privkey (keys/str->private-key (:private_key *service-account*))
        claim {:iss (:client_email *service-account*)
               :scope scope
               :aud access-request-url
               :exp (time/plus (time/now) (time/minutes 2))
               :iat (time/now)}]
    (jwt/sign claim privkey {:alg :rs256})))

;; TODO: now the caching happens for each scope string, which may contain more than
;; one scope. We should instead cache scopes singularly.
(defn get-oauth2-token
  "Given a scope string, returns a cached OAuth2 token, or requests a new one
  if the cached one (for that scope) is expired"
  [scope]
  (with-cache scope
    (-> {:method :post
         :url access-request-url
         :form-params {:grant_type "urn:ietf:params:oauth:grant-type:jwt-bearer"
                       :assertion (sign-jwt scope)}}
        (http/request)
        :body ;; TODO: handle http exceptions in here
        (parse-string true)
        :access_token)))


;;;; Requests utils

(defn wrap-auth
  "Given a request map and a vector of scopes, enriches it with a new OAuth2 token."
  [req-map scopes]
  (assoc-in req-map
            [:headers "Authorization"]
            (str "Bearer " (get-oauth2-token (str/join " " scopes)))))
