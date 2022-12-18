(defproject org.clojars.abhinav/snitch "0.1.13"
  :description
  "Snitch injects inline defs in your functions and multimethods.
                This enables a repl-based, editor-agnostic, clojure and clojurescript debugging workflow. 
                It is inline-defs on steroids."
  :url "https://github.com/AbhinavOmprakash/snitch"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/clojurescript "1.11.60"]]
  :plugins [[lein-auto "0.1.3"]
            [lein-cloverage "1.0.9"]
            [lein-eftest "0.5.9"]]
  :repositories [["releases" {:url "https://repo.clojars.org"
                              :sign-releases false}]])
