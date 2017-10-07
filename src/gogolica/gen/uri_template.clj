(ns gogolica.gen.uri-template
  (:gen-class)
  (:require [clojure.string :as str]
            [cheshire.core :as json]
            [me.raynes.fs :as fs]
            [camel-snake-kebab.core :refer :all]))

(defn parse
  [path-template arg-names]
  (let [args->symbols (->> arg-names
                           (map name)
                           (map #(hash-map % (->kebab-case-symbol %)))
                           (apply merge))
        ;; Returns a list of vectors, where the first element is the match including
        ;; the curly brackets, and the second element is without.
        matches (re-seq #"\{(.+?)\}" path-template)
        ;; Helper function in which we iterate on the template string,
        ;; matching on the first pair of curly braces and adding the
        ;; match to the accumulator vector, recurring on more matches
        parse'
        (fn [result-acc template matches]
          (if-some [[match arg] (first matches)]
            (let [[pre post] (str/split template (re-pattern (str "\\{" arg "\\}")))]
              (recur (concat result-acc [pre (get args->symbols arg)])
                     post
                     (rest matches)))
            (if template ;; we have no matches anymore, but there might be some string still
              (concat result-acc [template])
              result-acc)))]
    (parse' [] path-template matches)))
