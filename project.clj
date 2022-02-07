(defproject org.clojars.abhinav/snitch "0.0.1"
  :description "Tools to gain insight into the data inside functions."
  :url "https://github.com/AbhinavOmprakash/snitch"
  :license {:name "MIT"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 ]
  :plugins [[lein-auto "0.1.3"]
            [lein-cloverage "1.0.9"]]
  :repl-options {:init-ns snitch.core}
  :repositories [["releases" {:url "https://repo.clojars.org"
                            :sign-releases false}]])
