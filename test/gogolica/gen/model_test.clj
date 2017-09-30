(ns gogolica.gen.model-test
  (:require [clojure.test :refer :all]
            [gogolica.gen.model :as model]))

(def parameters {:fooBar {:required true
                          :location "path"}
                 :bar {:required true
                       :location "path"}
                 :baz {:location "query"}})

(deftest split-required-params
  (testing "Splitting of method parameters between required and not."
    (is (= (model/split-required-params parameters)
           [{:fooBar {:required true
                      :location "path"}
             :bar {:required true
                   :location "path"}}
            {:baz {:location "query"}}]))))
