(defproject clurichaun "0.1.0-SNAPSHOT"
  :description "Clurichaun is a WebGL game engine"
  :url "https://github.com/danielytics/clurichaun"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.542"]
                 [org.clojure/core.async "0.3.442"]
                 [bardo "0.1.2-SNAPSHOT"]
                 [cljsjs/pixi "4.4.3-0"]
                 [cljsjs/pixi-sound "1.4.1-0"]
                 [cljsjs/nprogress "0.2.0-1"]
                 [cljsjs/localforage "1.3.1-0"]]

  :plugins [[lein-cljsbuild "1.1.4"]]

  :min-lein-version "2.5.3"

  :source-paths ["src"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"
                                    "test/js"]

  :figwheel {:css-dirs ["resources/public/css"]}

  :profiles
  {:dev
   {:dependencies [[binaryage/devtools "0.9.4"]]

    :plugins      [[lein-figwheel "0.5.9"]
                   [lein-doo "0.1.7"]]}}

  :cljsbuild
  {:builds
   [{:id           "dev"
     :source-paths ["src/framework/common" "src/framework/core" "src/example"]
     :figwheel     {:on-jsload            "clurichaun.core/reload"}
     :compiler     {:main                 clurichaun.init
                    :output-to            "resources/public/js/compiled/app.js"
                    :output-dir           "resources/public/js/compiled/out"
                    :asset-path           "js/compiled/out"
                    :source-map-timestamp true
                    :preloads             [devtools.preload]
                    :external-config      {:devtools/config {:features-to-install :all}}}}

    {:id            "dev-worker"
     :source-paths ["src/framework/common" "src/framework/worker" "src/example"]
     :figwheel     true
     :compiler     {:output-to            "resources/public/js/compiled/worker.js"
                    :output-dir           "resources/public/js/compiled/out_worker"
                    :source-map-timestamp true
                    :optimizations        :none
                    :preloads             [devtools.preload]
                    :external-config      {:devtools/config {:features-to-install :all}}}}

    {:id           "min"
     :source-paths ["src"]
     :compiler     {:main            clurichaun.init
                    :output-to       "resources/public/js/compiled/app.js"
                    :optimizations   :advanced
                    :closure-defines {goog.DEBUG false}
                    :pretty-print    false}}

    {:id           "test"
     :source-paths ["src" "test"]
     :compiler     {:main          clurichaun.runner
                    :output-to     "resources/public/js/compiled/test.js"
                    :output-dir    "resources/public/js/compiled/test/out"
                    :optimizations :none}}]})
