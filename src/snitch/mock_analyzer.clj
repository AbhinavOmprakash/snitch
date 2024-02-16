(ns snitch.mock-analyzer
  "Mocking the clojurescript.analyzer ns. 
  Required if you want to exclude cljs deps for a clj only project
  "
  (:refer-clojure :exclude [macroexpand-1]))


(defn macroexpand-1
  [_env form]
  form)
