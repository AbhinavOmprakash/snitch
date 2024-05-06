(ns snitch.analyzer
  "Mocking the clojurescript.analyzer ns. 
  Required if you want to exclude cljs deps for a clj only project
  "
  (:refer-clojure :exclude [macroexpand-1]))


(try
  (require '[cljs.analyzer :as ana])
  (catch Exception _))


(defn macroexpand-1
  [env form]
  (if (resolve 'ana)
    (eval '(ana/macroexpand-1 env form))
    form))
