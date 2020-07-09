(defproject chat-server "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :source-paths ["src/clj" "src/cljc" ]
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [clojure.java-time "0.3.2"]
                 [http-kit "2.3.0"]
                 [org.clojure/core.match "0.3.0"]
                 [com.taoensso/encore "2.120.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [jumblerg/ring-cors "2.0.0"]
                 [com.taoensso/sente "1.15.0"]
                 [ring/ring-defaults "0.3.2"]
                 [compojure          "1.6.1"]
                 [com.fazecast/jSerialComm "[2.0.0,3.0.0)"]
                 [javax.xml.bind/jaxb-api "2.3.1"]
                 [org.clojure/core.async "1.2.603"]]
  :repl-options {:init-ns ws}
  :main ws
  :omit-source true
  :jar-name "chat-server.jar"
  :profiles {:uberjar {:aot :all}})
