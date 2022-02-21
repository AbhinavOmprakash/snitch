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


(defn binding-form?
  [form]
  (boolean
    (when (list? form)
      (some #{(first form)}
            ['let 'if-let 'when-let 'fn]))))


(defn arg->def-args
  ([arg]
   (cond
     (symbol? arg)
     `(def ~(concat-symbols (->simple-symbol arg))
        ~(->simple-symbol arg))

     (vector? arg)
     (map arg->def-args arg)

     (seq? arg)
     (map arg->def-args arg)

     (map? arg)
     (let [keys* (remove keyword? (keys arg))
           map-name (arg->def-args (:as arg))
           map-name* (if (nil? map-name)
                       nil
                       (list map-name))]
       (concat (arg->def-args keys*)
               (arg->def-args (:keys arg))
               map-name*)))))


(defn define-args
  ([args]
   (reduce (fn [body arg]
             (if (symbol? arg)
               (concat body (list (arg->def-args arg)))
               (concat body (arg->def-args arg))))
           ()
           args)))


(defn insert-into-let
  ([bindings]
   (reduce (fn [acc [var* value]]
             (let [defined-args (define-args [var*])
                   defined-args* (interleave (repeat '_) defined-args)]
               (apply conj (conj acc var* value) defined-args*)))
           []
           (partition 2 bindings))))


(declare define-in-binding-forms define-in-variadic-forms )


(defmulti define-binding-form (fn [body]
                                (first body)))


(defmethod define-binding-form 'let
  ([[l* bindings & inner-body]]
   `(~l* ~(insert-into-let bindings)
         ~@(define-in-binding-forms inner-body))))


(defmethod define-binding-form 'if-let
  ([[l* bindings & inner-body]]
   `(~l* ~bindings
         (def ~(first bindings) ~(first bindings))
         ~@(define-in-binding-forms inner-body))))


(defmethod define-binding-form 'when-let
  ([[l* bindings & inner-body]]
   `(~l* ~bindings
         (def ~(first bindings) ~(first bindings))
         ~@(define-in-binding-forms inner-body))))


(defmethod define-binding-form 'fn
  ([body]
   `(fn ~@(define-in-variadic-forms 'fn* (rest body)))))


(defn define-in-binding-forms
  ([body]
   (cond
     ((complement list?) body)
     body

     (binding-form? body)
     (define-binding-form body)

     :else
     (map (partial define-in-binding-forms) body))))




(defn define-in-variadic-forms
  ([name form]
   (let [params (first form)
         prepost-map? (when (map? (second form))
                        (second form))
         body (if (nil? prepost-map?)
                (second form)
                (last form))]

     (if (some? prepost-map?)
       `(~params ~@(define-args params)
                 ~prepost-map?
                 (let [result# ~(define-in-binding-forms body)]
                   (def ~(concat-symbols name '>) result#)
                   result#))
       `(~params ~@(define-args params)
                 (let [result# ~(define-in-binding-forms body)]
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
                (define-in-binding-forms body))

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


(defmacro defmethod*
  [name dispatch-value forms]
  `(defmethod ~name ~dispatch-value
     ~(define-in-variadic-forms name forms)))

