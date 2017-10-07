(ns gogolica.gen.model.method
  "Functions to access the data about API model methods."
  (:gen-class)
  (:require [clojure.string :as str]
            [cheshire.core :as json]
            [me.raynes.fs :as fs]
            [camel-snake-kebab.core :refer :all]
            [gogolica.gen.uri-template :as uri-template]))

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

(defn parameters
  [method]
  (merge
   (-> method :parameters)
   (when (media-download? method)
     {:alt {:position "query"}})))

(defn path-parameters
  [method]
  (->> method parameters
       (filter #(-> % val :location (= "path")))))

(defn query-parameters
  [method]
  (->> method parameters
       (filter #(-> % val :location (= "query")))))

(defn path
  "The path template of a given method.
   Represented as a vector of strings and symbols."
  [method]
  (uri-template/parse
   (-> method :path)
   (-> method path-parameters keys)))

(defn simple-upload-path
  "The path template for simple upload url of given method."
  [method]
  (uri-template/parse
   (some-> method media-upload :protocols :simple :path)
   (-> method path-parameters keys)))

(defn http-method
  "Returns the http method of a given method as a ring-compatible keyword."
  [method]
  (-> method :httpMethod str/lower-case keyword))

(defn function-name
  "Returns an identifier for a given method.
   Identifier is a symbol and has a form of 'resource-method'."
  [method]
  (let [[service-name resource-name name] (str/split (:id method) #"\.")]
    (->kebab-case-symbol
     (str resource-name "-" name))))

(defn body-ident
  "A suitable identifier for passing the body of a given method."
  [method]
  (some-> method :request :$ref ->kebab-case-symbol))
