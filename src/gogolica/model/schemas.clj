(ns gogolica.model.schemas
  "Conversion of json schemas from the discovery models to clojure specs."
  (:gen-class)
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [spec-tools.core :as st]
            [spec-tools.data-spec :as ds]
            [spec-tools.conform :as conform]
            [spec-tools.spec :as spec]
            [camel-snake-kebab.core :refer :all]))

(def test-item
  (->> (gogolica.gen.model/model-for :storage :v1)
       :schemas
       first))

;; Google adds some keys to the ones defined in
;; https://tools.ietf.org/html/draft-zyp-json-schema-03
;; They are: `enumDescriptions`, and `annotations`
;; Details here: https://developers.google.com/discovery/v1/reference/apis

(defn json-schema->data-spec
  "Given a json schema object, returns a data spec for it."
  [obj]
  nil) ;; TODO
