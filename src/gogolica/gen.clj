(ns gogolica.gen
  (:gen-class)
  (:require [gogolica.gen.generate :as generate]
            [gogolica.gen.model    :as model]))

(-> (model/model-for :storage :v1)
    (model/select-resource-methods
     {:buckets [:insert :list]
      :objects [:insert :list]})
    (generate/generate-ns)
    println)
