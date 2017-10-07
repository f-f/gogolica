(ns gogolica.gen.model.method-test
  (:require [clojure.test :refer :all]
            [gogolica.gen.model.method :as method]))

(deftest function-name
  (testing "Generation of function names"
    (is (= (method/function-name {:id "storage.objects.get"})
           'objects-get))))
