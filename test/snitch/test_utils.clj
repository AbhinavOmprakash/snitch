(ns snitch.test-utils
  (:require
    [clojure.test :refer [assert-expr do-report]]))

;; can be used with functions that return quoted lists or macros
(defmethod assert-expr 'expansion-valid? [msg form]
  (let [[_ macro expected-expansion] form]
   ;; (is (expansion-valid? (macro arg) expansion)) 
   ;; (is (expansion-valid? (fn arg) expansion))
  `(let [result# ~(= (eval macro) expected-expansion)]
       (if result# 
         (do-report {:actual '~(eval macro)
                     :expected '~expected-expansion
                     :message ~msg
                     :type :pass})
         (do-report {:actual '~(eval macro)
                     :expected '~expected-expansion
                     :message ~msg
                     :type :fail})))))

(defmethod assert-expr 'macro-valid? [msg form]
  (let [[_ macro expected-expansion] form]
   ;; (is (expansion-valid? (macro arg) expansion)) 
   ;; (is (expansion-valid? (fn arg) expansion))
  `(let [result# ~(= (macroexpand-1 macro) expected-expansion)]
       (if result# 
         (do-report {:actual '~(macroexpand-1 macro)
                     :expected '~expected-expansion
                     :message ~msg
                     :type :pass})
         (do-report {:actual '~(macroexpand-1 macro)
                     :expected '~expected-expansion
                     :message ~msg
                     :type :fail})))))
(defn concat-symbols
  [sym1 sym2]
  (symbol (str (str sym1) (str sym2))))
(defn arg->def-args
  ([arg suffix]
   (cond
     (symbol? arg)
     `(def ~(concat-symbols arg suffix) ~arg)

     (vector? arg)
     (map arg->def-args arg suffix)

     (seq? arg)
     (map arg->def-args arg suffix)

     (map? arg)
     (let [keys* (remove keyword? (keys arg))]
       (concat (arg->def-args keys* suffix)
               (arg->def-args (:keys arg) suffix)
               (list (arg->def-args (:as arg) suffix)))))))
(defmacro mv?
  [msg form]
  (let [macro (nth form 1)
        expected-expansion (nth form 2)]
  `(let [result# ~(= (macroexpand-1 macro) expected-expansion)]
     ~(macroexpand-1 macro)
       #_(if result# 
         (do-report {:actual '~(macroexpand-1 macro)
                     :expected '~expected-expansion
                     :message ~msg
                     :type :pass})
         (do-report {:actual '~(macroexpand-1 macro)
                     :expected '~expected-expansion
                     :message ~msg
                     :type :fail})))))


(macroexpand-1 '(mv? "" (expansion-valid? (arg->def-args 'a "") (def a a)))) 
; ; (clojure.core/let [result__14141__auto__ false] (arg->def-args 'a ""))



