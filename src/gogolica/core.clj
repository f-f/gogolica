(ns gogolica.core
  (:gen-class)
  (:require [gogolica.generate :as generate]
            [gogolica.model    :as model]))

(-> (model/model-for :storage :v1)
    (model/select-resource-methods
     {:buckets [:insert :list]
      :objects [:insert :list]})
    (generate/generate-ns)
    println)
