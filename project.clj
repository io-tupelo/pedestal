(defproject io.tupelo/pedestal "21.09.10"
  :description "Pedestal With A Spoonful of Honey"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.9.1"

  :global-vars {*warn-on-reflection* false}

  :excludes [org.clojure/clojure
             org.clojure/clojurescript]

  :dependencies
  [
   [http-kit "2.5.3"]
   [io.pedestal/pedestal.jetty "0.5.9"]
   [io.pedestal/pedestal.route "0.5.9"]
   [io.pedestal/pedestal.service "0.5.9"]
   [prismatic/schema "1.1.12"]
   [tupelo "21.09.07"]
   ]

  ; Using `lein-ancient check :all` checks plugins
  :plugins [[lein-codox "0.10.7"]
            [com.jakemccrary/lein-test-refresh "0.24.1"]]

  :test-refresh {:quiet true ; true => suppress printing namespaces when testing
                 }

  :profiles {:provided {:dependencies [[org.clojure/clojure "1.8.0" :scope "provided"]]}
             :dev      {:dependencies [[org.clojure/clojure "1.10.3"]]}
             }
  :source-paths ["src/clj" "src/cljc"]
  :test-paths ["test/clj" "test/cljc"]
  :target-path "target/%s"

  ; need to add the compliled assets to the :clean-targets
  :clean-targets ^{:protect false} ["out"
                                    :target-path]

  ; :main ^:skip-aot tupelo.core
  ; :uberjar      {:aot :all}

  :deploy-repositories {"snapshots"    :clojars
                        "releases"     :clojars
                        :sign-releases false}

  :jvm-opts ["-Xms500m" "-Xmx2g"
            ]
  )
















