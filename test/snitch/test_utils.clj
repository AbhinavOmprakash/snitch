(ns snitch.test-utils
  (:require
    [clojure.string :as s]
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


(defn binding-form?
  [form]
  (boolean
    (when (list? form)
      (some #{(first form)}
            ['let 'if-let 'when-let]))))


(defn autogensym->sym
  "Converts autogensymed symbols to simple symbols.
  This makes it simple to compare two macro expansions.
  
  (autogensym->sym 'end_result__12833__auto__) 
  ;=> end_result
  "
  [autogensym-symbol]
  (let [sym (str autogensym-symbol)]
    (symbol (s/replace sym #"__\d.*__auto__" ""))))


(defn autogensym?
  "Returns true if symbol is an autogensym symbol"
  [x]
  (boolean (re-matches #".*__\d.*__auto__" (str x))))


(def seq-or-list-or-vector? (some-fn vector? list? seq?))


(defn replace-autogensym-symbols
  "Replaces all the gensymed symbols in form"
  [form]
  (reduce (fn [acc x]
            (let [tail (if (seq-or-list-or-vector? x)
                         (replace-autogensym-symbols x)
                         (if (autogensym? x)
                           (autogensym->sym x)
                           x))]
              ;; ugly, find more elegant solution
              (if (seq? acc)
                (concat acc [tail])
                (conj acc tail))))
          (if (seq? form)
            ()
            [])
          form))


(defmethod assert-expr 'macro-valid? [msg form]
  (let [[_ macro expected-expansion] form
        _ (def form form)]
    ;; (is (expansion-valid? (macro arg) expansion)) 
    ;; (is (expansion-valid? (fn arg) expansion))

    `(let [result# ~(= (replace-autogensym-symbols (macroexpand-1 macro))
                       (replace-autogensym-symbols expected-expansion))]
       (if result#
         (do-report {:actual '~(replace-autogensym-symbols (macroexpand-1 macro))
                     :expected '~(replace-autogensym-symbols expected-expansion)
                     :message ~msg
                     :type :pass})
         (do-report {:actual '~(replace-autogensym-symbols (macroexpand-1 macro))
                     :expected '~(replace-autogensym-symbols expected-expansion)
                     :message ~msg
                     :type :fail})))))
