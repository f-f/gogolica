(ns gogolica.gen.generate-test
  (:require [clojure.test :refer :all]
            [gogolica.gen.generate :as g]))

(def parameters {:fooBar {:required true
                          :location "path"}
                 :bar {:required true
                       :location "path"}
                 :baz {:location "query"}})

(deftest generate-args
  (testing "Generation of function arguments."
    (is (= (g/generate-args {:parameters parameters
                             :parameterOrder ["bar" "fooBar"]})
           '[bar foo-bar {:as optional-params :keys [baz]}])))
  (testing "Generation of function arguments, when request body is present."
    (is (= (g/generate-args {:parameters parameters
                             :parameterOrder ["bar" "fooBar"]
                             :request {:$ref "Bucket"}})
           '[bucket bar foo-bar {:as optional-params :keys [baz]}]))))

;; TODO: generate-args
