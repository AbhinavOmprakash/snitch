(ns snitch.test-utils
  (:require
    [clojure.string :as s]
    [clojure.test :refer [assert-expr do-report]]))


(defn binding-form?
  [form]
  (boolean
    (when (list? form)
      (some #{(first form)}
            ['let 'if-let 'when-let]))))


(defn autogensym->sym
  "Converts autogensymed or gensymed symbols to simple symbols.
  This makes it simple to compare two macro expansions.
  
  (autogensym->sym 'end_result__12833__auto__) 
  ;=> end_result
  "
  [autogensym-symbol]
  ; the reason I'm doing the replace in 2 steps is because
  ; gensym generates a symbol like p__123123
  ; and it doesn't have the __auto__ part.
  (let [sym (str autogensym-symbol)]
    (-> sym
        (s/replace #"__auto__" "")
        (s/replace #"__\d.*" "")
    symbol)))


(defn autogensym?
  "Returns true if symbol is an autogensym or gensym symbol"
  [x]
  (boolean (or (re-matches #".*__\d.*__auto__" (str x))
               (re-matches #".*__\d*" (str x)))))


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


;; can be used with functions that return quoted lists or macros
(defmethod assert-expr 'expansion-valid? [msg form]
  (let [[_ macro expected-expansion] form]
    ;; (is (expansion-valid? (macro arg) expansion)) 
    ;; (is (expansion-valid? (fn arg) expansion))
    `(let [result# ~(= (replace-autogensym-symbols  (eval macro))
                       (replace-autogensym-symbols expected-expansion))]
       (if result#
         (do-report {:actual '~(replace-autogensym-symbols  (eval macro))
                     :expected '~(replace-autogensym-symbols   expected-expansion)
                     :message ~msg
                     :type :pass})
         (do-report {:actual '~(replace-autogensym-symbols  (eval macro))
                     :expected '~(replace-autogensym-symbols   expected-expansion)
                     :message ~msg
                     :type :fail})))))


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
