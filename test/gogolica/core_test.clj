(ns gogolica.core-test
  (:require [clojure.test :refer :all]
            [gogolica.core :as g]))

(def parameters {:fooBar {:required true
                          :location "path"}
                 :bar {:required true
                       :location "path"}
                 :baz {:location "query"}})

(deftest split-required-params
  (testing "Splitting of method parameters between required and not."
    (is (= (g/split-required-params parameters)
           [{:fooBar {:required true
                      :location "path"}
             :bar {:required true
                   :location "path"}}
            {:baz {:location "query"}}]))))

(deftest generate-function-name
  (testing "Generation of function names"
    (is (= (g/generate-function-name "storage.objects.get")
           'objects-get))))

(deftest generate-args
  (testing "Generation of function arguments."
    (is (= (g/generate-args parameters ["bar" "fooBar"] nil)
           '[bar foo-bar {:as optional-params :keys [baz]}])))
  (testing "Generation of function arguments, when request body is present."
    (is (= (g/generate-args parameters ["bar" "fooBar"] {:$ref "Bucket"})
           '[bucket bar foo-bar {:as optional-params :keys [baz]}]))))

(deftest template->path-vector
  (testing "Uri template to path vector conversion."
    (is (= (g/template->path-vector "b/{fooBar}/c/{bar}" ["fooBar" "bar"])
           '("b/" foo-bar "/c/" bar))))
  (testing "Uri template conversion with no vars."
    (is (= (g/template->path-vector "b" [])
           '("b")))))

(deftest generate-path
  (testing "Uri template to path vector conversion, with raw parameters."
    (is (= (g/generate-path "b/{fooBar}/c/{bar}" parameters)
           '("b/" foo-bar "/c/" bar)))))

;; TODO: generate-args
