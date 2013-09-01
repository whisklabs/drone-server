(defproject clj-server "0.1"
  :description "clojure server"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/data.json "0.2.3"]
                 [com.taoensso/carmine "2.2.0"]
                 [http-kit "2.1.10"]
                 [compojure "1.1.5"]
                 [clj-pusher "0.3.1" :exclusions [[clj-http]]]
                 [clj-http "0.6.3"]
                 [org.clojure/data.xml "0.0.7"]]
  :jvm-opts ["-Xmx768m"]
  :min-lein-version "2.0.0"
  :aot [server.core]
  :main server.core)
