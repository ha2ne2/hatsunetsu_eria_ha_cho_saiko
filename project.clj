(defproject res_shower "1.0.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [seesaw "1.4.5"]
                 [environ "1.1.0"]
                 [clj-http "3.12.0"]
                 ]
  :plugins [[lein-environ "1.1.0"]]
  :resource-paths ["resources"]
  :main ^:skip-aot res-shower.core
  :target-path "target/%s"
  :profiles {:dev     {:env {:dev true}}
             :uberjar {:aot :all}})
