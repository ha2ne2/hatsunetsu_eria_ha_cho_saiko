(defproject res_shower "0.1.1-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [seesaw "1.4.5"]]
  ;; :jvm-opts
  ;; ["-Xverify:none"
  ;;  "-XX:+TieredCompilation"
  ;;  "-XX:TieredStopAtLevel=1"]
  :resource-paths ["resources"]
  :main ^:skip-aot res-shower.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})