(ns gogolica.gen.model-test
  (:require [clojure.test :refer :all]
            [gogolica.gen.model :as model]))

(def parameters {:fooBar {:required true
                          :location "path"}
                 :bar {:required true
                       :location "path"}
                 :baz {:location "query"}})

(def model
  {:resources
   {:foo {:methods
          {:get :COME_HERE
           :put :TAKE_THAT
           :delete :GO_AWAY}}
    :bar {:methods
          {:drink :TODO
           :cheer :TODO}}
    :kitchen {:resources
              {:drinks
               {:methods
                {:make_coffee :COFFEE
                 :brew_tea :TEA}}}}}})

(deftest split-required-params
  (testing "Splitting of method parameters between required and not."
    (is (= (model/split-required-params parameters)
           [{:fooBar {:required true
                      :location "path"}
             :bar {:required true
                   :location "path"}}
            {:baz {:location "query"}}]))))

(deftest select-resource-methods
  (testing "Restricting to only certain resources/methods"
    (is (= (model/select-resource-methods
            model
            {:kitchen {:drinks [:brew_tea]}
             :bar     [:drink]})
           {:resources
            {:kitchen {:resources {:drinks {:methods {:brew_tea :TEA}}}}
             :bar     {:methods   {:drink :TODO}}}}))))

(deftest all-methods
  (testing "Gets all the methods out of a model"
    (is (= (->> model model/all-methods (into #{}))
           #{:COME_HERE :TAKE_THAT :GO_AWAY :COFFEE :TEA :TODO}))))
