(ns snitch.core
  (:require
    [clojure.string :as s]))


(defn ->simple-symbol
  [sym]
  (if (simple-symbol? sym) sym
      (-> sym
          str
          (s/split #"/")
          last
          symbol)))


(defn concat-symbols
  [& symbols]
  (symbol (apply str symbols)))


(defn let-form?
  [form]
  (boolean
    (when (list? form)
      (some #{(first form)}
            ['let 'if-let 'when-let]))))


(defn extract-bindings
  [bindings]
  (keep-indexed (fn [i el]
                  (when (even? i) el))
                bindings))


(defn arg->def-args
  ([arg] (arg->def-args "" "" arg))
  ([suffix arg]
   (arg->def-args "" suffix arg))
  ([prefix suffix arg]
   ;; FIXME: inconsistent types of prefix and suffix, one is a symbol the other a string
   (let [prefix* (if (or (symbol? prefix)
                         (seq prefix))
                   (concat-symbols prefix "-")
                   prefix)
         arg->def-args* (partial arg->def-args prefix* suffix)]
     (cond
       (symbol? arg)
       `(def ~(concat-symbols prefix* (->simple-symbol arg) suffix)
          ~(->simple-symbol arg))

       (vector? arg)
       (map arg->def-args* arg)

       (seq? arg)
       (map arg->def-args* arg)

       (map? arg)
       (let [keys* (remove keyword? (keys arg))
             map-name (arg->def-args* (:as arg))
             map-name* (if (nil? map-name)
                         nil
                         (list map-name))]
         (concat (arg->def-args* keys*)
                 (arg->def-args* (:keys arg))
                 map-name*))))))


(defn define-args
  ([args]
   (define-args "" "" args))
  ([suffix args]
   (define-args "" suffix args))
  ([prefix suffix args]
   (reduce (fn [body arg]
             (if (symbol? arg)
               (concat body (list (arg->def-args prefix suffix arg)))
               (concat body (arg->def-args prefix suffix arg))))
           ()
           args)))



(defn insert-into-let
  ([prefix suffix bindings]
   (reduce (fn [acc [var* value]]
             (let [defined-args (define-args prefix suffix [var*])
                   defined-args* (interleave (repeat '_) defined-args)]
               (apply conj (conj acc var* value) defined-args*)))
           []
           (partition 2 bindings))))


(defn define-let-bindings
  ([body]
   (define-let-bindings "" "" body))
  ([suffix body]
   (define-let-bindings "" suffix body))
  ([prefix suffix body]
   (cond
     ((complement list?) body)
     body

     (let-form? body)
     (let [[l* bindings & inner-body] body
           suffix* (str suffix (str (first suffix))) ; FIXME: Hacky. since suffix is repeating the same char, we take the first char, since we want the suffix to grow one char at a time, and not double
           inner-body* (define-let-bindings prefix suffix* inner-body)]
       `(~l* ~(insert-into-let prefix suffix bindings)
             ~@inner-body*))

     :else
     (map (partial define-let-bindings prefix suffix) body))))


(defn define-in-variadic-forms
  ([name form]
   (define-in-variadic-forms name "" "" "" form))

  ([name suffix-for-def suffix-for-let form]
   (define-in-variadic-forms name "" suffix-for-def suffix-for-let form))

  ([name prefix suffix-for-def suffix-for-let form]
   (let [params (first form)
         prepost-map? (when (map? (second form))
                        (second form))
         body (if (nil? prepost-map?)
                (second form)
                (last form))]

     (if (some? prepost-map?)
       `(~params ~@(define-args prefix suffix-for-def params)
                 ~prepost-map?
                 (let [result# ~(define-let-bindings prefix suffix-for-let body)]
                   (def ~(concat-symbols name '>) result#)
                 result#))
       `(~params ~@(define-args prefix suffix-for-def params)
                 (let [result# ~(define-let-bindings prefix suffix-for-let body)]
                   (def ~(concat-symbols name '>) result#)
                 result#))))))


(defmacro defn*
  [name & forms]
  (let [[doc-string? forms] (if (string? (first forms))
                              [(first forms) (rest forms)]
                              [nil forms])
        [attr-map? forms] (if (map? (first forms))
                            [(first forms) (rest forms)]
                            [nil forms])
        [params* forms] (if (vector? (first forms))
                          [(first forms) (rest forms)]
                          [nil forms])
        [prepost-map? forms] (if (and (some? params*)
                                      (map? (first forms))
                                      (or
                                        (vector? (:pre (first forms)))
                                        (vector? (:post (first forms)))))
                               [(first forms) (rest forms)]
                               [nil forms])
        [variadic-defs forms] (if (and (nil? params*)
                                       (list? (first forms)))
                                [forms nil]
                                [nil forms])
        body (if (nil? variadic-defs)
               forms
               nil)
        params-def (when (some? params*)
                     (define-args params*))
        body* (when (some? body)
                (define-let-bindings body))

        variadic-defs* (map #(define-in-variadic-forms name %) variadic-defs)

        args-to-defn (list doc-string? attr-map? params* prepost-map?)
        args-to-defn* (remove nil? args-to-defn)]

    (if (some? variadic-defs)
      `(defn ~name ~@args-to-defn*
         ~@variadic-defs*)

      `(defn ~name ~@args-to-defn*
         ~@params-def
         (let [result#
               (do ~@body*)]
           (def ~(concat-symbols name '>) result#)

           result#)))))

