(ns snitch.test-utils
  (:refer-clojure :exclude [#?(:cljs macroexpand)])
  (:require
   [clojure.string :as s]
   [cljs.analyzer :as ana]
   [clojure.test :refer [assert-expr do-report]])
  #?(:cljs (:require [cljs.test :refer [assert-expr]])))


(defn walk
  "Like `clojure.walk/walk`, but preserves metadata."
  [inner outer form]
  (let [x (cond
            (list? form) (outer (with-meta (apply list (map inner form)) 
                                                      (meta form)))
            (instance? clojure.lang.IMapEntry form) (outer (vec (map inner form)))
            (seq? form) (outer (with-meta  (doall (map inner form)) 
                                          (meta form)))
            (instance? clojure.lang.IRecord form)
            (outer (reduce (fn [r x] (conj r (inner x))) form form))
            (coll? form) (outer (into (empty form) (map inner form)))
            :else (outer form))]
    (if (instance? clojure.lang.IObj x)
      (with-meta x (merge (meta form) (meta x)))
      x)))

(defn postwalk
  "Like `clojure.walk/postwalk`, but preserves metadata."
  [f form]
  (walk (partial postwalk f) f form))

(defn prewalk
  "Like `clojure.walk/prewalk`, but preserves metadata."
  [f form]
  (walk (partial prewalk f) identity (f form)))


#?(:clj (defn macroexpand*
          "Like macroexpand but works with cljs."
          [env form]
          (if (contains? env :js-globals)
      ;; cljs
            (loop [form form
                   form* (with-meta (ana/macroexpand-1 env form) (meta form))]
              (if-not (identical? form form*)
                (recur form* (ana/macroexpand-1 env form*))
                form*))
      ;; clj
            (with-meta (macroexpand form) (meta form)))))


#?(:clj (defn macroexpand-all
          "Like clojure.walk/macroexpand-all but works with cljs."
          [env form]
          (prewalk (fn [x]
                     (if (seq? x)
                       (macroexpand* env x)
                       x))
                   form)))


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
#?(:clj (defmethod assert-expr 'expansion-valid? [msg form]
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
                             :type :fail}))))))


#?(:clj (defmethod assert-expr 'macro-valid? [msg form]
          (let [[_ macro expected-expansion] form]
      ;; (is (macro-valid? (macro arg) expansion)) 
      ;; (is (macro-valid? (fn arg) expansion))

            `(let [result# ~(= (replace-autogensym-symbols (macroexpand-all {} macro))
                               (replace-autogensym-symbols expected-expansion))]
               (if result#
                 (do-report {:actual '~(replace-autogensym-symbols (macroexpand-all {} macro))
                             :expected '~(replace-autogensym-symbols expected-expansion)
                             :message ~msg
                             :type :pass})
                 (do-report {:actual '~(replace-autogensym-symbols (macroexpand-all {} macro))
                             :expected '~(replace-autogensym-symbols expected-expansion)
                             :message ~msg
                             :type :fail})))))


   :cljs (defmethod assert-expr 'macro-valid? [menv msg form]
           (let [[_ macro expected-expansion] form]
      ;; (is (macro-valid? (macro arg) expansion)) 
      ;; (is (macro-valid? (fn arg) expansion))

             `(let [result# ~(= (replace-autogensym-symbols (macroexpand-all menv macro))
                                (replace-autogensym-symbols expected-expansion))]
                (if result#
                  (do-report {:actual '~(replace-autogensym-symbols (macroexpand-all menv macro))
                              :expected '~(replace-autogensym-symbols expected-expansion)
                              :message ~msg
                              :type :pass})
                  (do-report {:actual '~(replace-autogensym-symbols (macroexpand-all menv macro))
                              :expected '~(replace-autogensym-symbols expected-expansion)
                              :message ~msg
                              :type :fail}))))))


(defn let-form?
  [form]
  (boolean
   (when (seq? form)
     (some #{(first form)}
           ['let
            'let*
            'clojure.core/let]))))



(defmacro contains-no-duplicate-inline-defs?
  "This is a special macro to test that a macroexpansion of 
  a snitch macro doesn't contain any duplicate let-bindings.
  This would happen in the case of nested lambda functions contain let forms.

  macro-form is a snitch macro like defn*, *fn, etc.

  The algorithm to do this is.
  1. walk the original form, when you encounter a let, expand it to let* so 
  destructuring can take place. 
  2. After destructuring, count the number of non-gensymed symbols. 
  (this is because snitch doesn't inject inline defs for auto-gensymed symbols.)
  3. Store the count along with the nested-level in a hash-map.
  4. do the same for the expanded form
  5. the hash-map of the non-expanded form should have a count that is exactly half that of the 
    expanded one.
  "
  [macro-form]
  (let [add-levels  (fn [level]
                      (fn [form]
                        (if (let-form? form)
                          (let [form* (with-meta form {:level @level})]
                            (swap! level inc)
                            form*)
                          form)))
        macro-form* (prewalk (add-levels (atom 1)) macro-form)
         ; _ (binding [*print-meta* true] (clojure.pprint/pprint macro-form*) )
        exp (macroexpand-all &env macro-form*)
         ; _ (binding [*print-meta* true] (clojure.pprint/pprint exp) )
        count-fn (fn [m env]
                   (fn [form]
                     (if (and (let-form? form)
                              (:level (meta form)))
                       (let [exp (macroexpand-all env form)
                             bindings (second exp)
                             parts (partition 2 bindings)
                             non-gensymed (remove #(-> % first autogensym?) parts)]
                         (swap! m update (:level (meta form)) (constantly (count non-gensymed)))
                         form)
                       form)))

        let-bindings-count (atom {})
        expanded-let-bindings-count (atom {})]
    (postwalk (count-fn let-bindings-count &env) macro-form*)
    (postwalk (count-fn expanded-let-bindings-count &env) exp)
    (prn @let-bindings-count)
    (prn @expanded-let-bindings-count)
    (= (map #(* 2 %)  (vals @let-bindings-count))
       (vals @expanded-let-bindings-count))))
