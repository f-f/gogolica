(ns gogolica.gen.uri-template-test
  (:gen-class)
  (:require [gogolica.gen.uri-template :as uri-template]
            [clojure.test :refer :all]))

(deftest parse
  (testing "Uri template to path vector conversion."
    (is (= (uri-template/parse "b/{fooBar}/c/{bar}" ["fooBar" "bar"])
           '("b/" foo-bar "/c/" bar))))
  (testing "Uri template conversion with no vars."
    (is (= (uri-template/parse "b" [])
           '("b"))))
  (testing "Uri template conversion with no vars at the end"
    (is (= (uri-template/parse "b/{fooBar}/o" ["fooBar"])
           '("b/" foo-bar "/o")))))
