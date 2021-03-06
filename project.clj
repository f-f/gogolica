(defproject gogolica "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.cache "0.6.5"]
                 [cheshire "5.8.0"]
                 [me.raynes/fs "1.4.6"]
                 [clj-http "3.7.0"]
                 [fipp "0.6.10"]
                 [buddy/buddy-sign "2.2.0"]
                 [buddy/buddy-core "1.4.0"]
                 [clj-time "0.14.0"]
                 [camel-snake-kebab "0.4.0"]]
  :main ^:skip-aot gogolica.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
