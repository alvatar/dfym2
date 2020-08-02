(defproject dfym "0.1.0-SNAPSHOT"
  :description "dfym"
  :url "http://dfym.org"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.773"]
                 ;; Architecture
                 [com.stuartsierra/component "1.0.0"]
                 [environ "1.2.0"]
                 ;; Core
                 [com.taoensso/encore "2.122.0"]
                 [com.taoensso/timbre "4.10.0"]
                 ;; [com.rpl/specter "1.1.2"] ; Immutable data structure manipulation
                 ;; [traversy "0.5.0"] ; Simply put, multilenses are generalisations of sequence and update-in
                 ;; Data format
                 [com.cognitect/transit-clj "1.0.324"]
                 [com.cognitect/transit-cljs "0.8.264"]
                 [cheshire "5.10.0"]
                 ;; Web
                 [ring "1.8.1"]
                 [ring/ring-defaults "0.3.2"]
                 [bk/ring-gzip "0.3.0"]
                 [prone "2020-01-17"]
                 [aleph "0.4.6"]
                 [clj-http "3.10.1"]
                 [compojure "1.6.1"]
                 [com.taoensso/sente "1.15.0"]
                 ;; Database
                 [org.clojure/java.jdbc "0.7.11"]
                 [org.postgresql/postgresql "42.2.14"]
                 [postgre-types "0.0.4"]
                 [buddy/buddy-core "1.6.0"]
                 [buddy/buddy-auth "2.2.0"]
                 [buddy/buddy-hashers "1.4.0"]
                 [digest "1.4.9"]
                 [mvxcvi/alphabase "2.1.0"]
                 [com.mchange/c3p0 "0.9.5.5"]
                 ;; HTML
                 [hiccup "1.0.5"]
                 [garden "1.3.10"]
                 ;; Specific
                 [camel-snake-kebab "0.4.1"]
                 ;; Cljs
                 [binaryage/oops "0.7.0"]
                 [binaryage/devtools "1.0.2"]
                 [rum "0.12.3"]
                 [datascript "1.0.0"]
                 [datascript-transit "0.3.0"
                  :exclusions [com.cognitect/transit-clj
                               com.cognitect/transit-cljs]]
                 [butler "0.2.0"]]

  :plugins [[lein-cljsbuild "1.1.8"]
            [lein-environ "1.2.0" :hooks false]]

  ;;:jvm-opts ["--add-modules" "java.xml.bind"]

  :min-lein-version "2.9.0"

  :source-paths ["src/clj" "src/cljs" "src/cljc"]

  :test-paths ["test/clj" "test/cljc"]

  :clean-targets ^{:protect false} [:target-path
                                    :compile-path
                                    "resources/public/js/dfym.js"
                                    "resources/public/js/worker.js"
                                    "resources/public/js/worker.js.map"]

  :uberjar-name "dfym.jar"

  ;; Use `lein run` if you just want to start a HTTP server, without figwheel
  :main dfym.core

  ;; nREPL by default starts in the :main namespace, we want to start in `user`
  ;; because that's where our development helper functions like (run) and
  ;; (browser-repl) live.
  :repl-options {:init-ns user}

  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src/cljs/dfym" "src/cljc"]
                :figwheel true
                :jar true
                ;; Alternatively, you can configure a function to run every time figwheel reloads.
                ;; :figwheel {:on-jsload "dfym.core/on-figwheel-reload"}
                :compiler {:main dfym.core
                           :preloads [devtools.preload]
                           :external-config {:devtools/config
                                             {:features-to-install [:formatters :hints]
                                              :fn-symbol "F"
                                              :print-config-overrides true}}
                           :asset-path "js/dfym_out"
                           :output-dir "resources/public/js/dfym_out"
                           :output-to "resources/public/js/dfym.js"
                           :optimizations :none
                           :pretty-print true}}
               {:id "dev-worker"
                :source-paths ["src/cljs/worker" "src/cljc"]
                :jar true
                ;; Alternatively, you can configure a function to run every time figwheel reloads.
                ;; :figwheel {:on-jsload "dfym.core/on-figwheel-reload"}
                :compiler {:main worker.core
                           :asset-path "js/worker_out"
                           :output-dir "resources/public/js/worker_out"
                           :output-to "resources/public/js/worker.js"
                           :source-map "resources/public/js/worker.js.map"
                           :optimizations :whitespace
                           :pretty-print true}}
               {:id "test"
                :source-paths ["src/cljs" "test/cljs" "src/cljc" "test/cljc"]
                :compiler {:output-to "resources/public/js/testable.js"
                           :main dfym.test-runner
                           :optimizations :none}}
               {:id "production"
                :source-paths ["src/cljs" "src/cljc"]
                :jar true
                :compiler {:main dfym.core
                           :output-to "resources/public/js/dfym.js"
                           :source-map "resources/public/js/dfym.js.map"
                           :output-dir "target"
                           :source-map-timestamp false
                           :optimizations :advanced
                           :pretty-print false}}]}
  ;; When running figwheel from nREPL, figwheel will read this configuration
  ;; stanza, but it will read it without passing through leiningen's profile
  ;; merging. So don't put a :figwheel section under the :dev profile, it will
  ;; not be picked up, instead configure figwheel here on the top level.
  :figwheel {;; :http-server-root "public"       ;; serve static assets from resources/public/
             ;; :server-port 3449                ;; default
             ;; :server-ip "127.0.0.1"           ;; default
             :css-dirs ["resources/public/css"]  ;; watch and update CSS
             ;; Instead of booting a separate server on its own port, we embed
             ;; the server ring handler inside figwheel's http-kit server, so
             ;; assets and API endpoints can all be accessed on the same host
             ;; and port. If you prefer a separate server process then take this
             ;; out and start the server with `lein run`.
             :ring-handler user/http-handler
             ;; Start an nREPL server into the running figwheel process. We
             ;; don't do this, instead we do the opposite, running figwheel from
             ;; an nREPL process, see
             ;; https://github.com/bhauman/lein-figwheel/wiki/Using-the-Figwheel-REPL-within-NRepl
             ;; :nrepl-port 7888
             ;; To be able to open files in your editor from the heads up display
             ;; you will need to put a script on your path.
             ;; that script will have to take a file path and a line number
             ;; ie. in  ~/bin/myfile-opener
             ;; #! /bin/sh
             ;; emacsclient -n +$2 $1
             ;;
             ;; :open-file-command "myfile-opener"
             :server-logfile "log/figwheel.log"}

  :doo {:build "test"}

  :profiles {:dev
             {:dependencies [[figwheel "0.5.20"]
                             [figwheel-sidecar "0.5.20"]
                             [cider/piggieback "0.5.0"]
                             [org.clojure/tools.nrepl "0.2.13"]
                             [midje "1.9.9"]]
              :plugins [[lein-figwheel "0.5.20"]
                        [lein-doo "0.1.11"]
                        [lein-ancient "0.6.15"]
                        [lein-midje "3.2.2"]]
              :source-paths ["dev"]
              :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}
              :env {:env "dev"}}
             :test
             {:dependencies [[midje "1.9.9"]]
              :plugins [[lein-midje "3.2.2"]]
              :env {:env "test"}}
             :uberjar
             {:source-paths ^:replace ["src/clj" "src/cljc"]
              :prep-tasks ["compile" ["cljsbuild" "once" "min"]]
              :omit-source true
              :aot :all
              :env {:env "uberjar"}}})
