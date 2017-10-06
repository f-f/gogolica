(ns gogolica.gen.model.method-test
  (:require [clojure.test :refer :all]
            [gogolica.gen.model.method :as method]))

(deftest ident
  (testing "Generation of function names"
    (is (= (method/ident {:id "storage.objects.get"})
           'objects-get))))
