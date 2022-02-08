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
