(ns snitch.core)


(def default-history-count (atom 3))


(defn alter-default-count!
  [n]
  (swap! default-history-count (constantly n)))


(defn concat-symbols
  [& symbols]
  (symbol (apply str symbols)))


(defn binding-form?
  [form]
  (boolean
    (when (seq? form)
      (some #{(first form)}
            ['let 'if-let 'when-let
             'clojure.core/let]))))


(defn update-atom
  [arg coll]
  (cons arg coll))


(defn arg->def-args
  ([arg]
   (if (symbol? arg)
     `(def ~arg ~arg)
     arg))
  ([name* arg]
   (if (symbol? arg)
     `(do (def ~arg ~arg)
          (swap! ~name* update '~arg  (partial update-atom ~arg))
          (def ~(concat-symbols arg '>) (take @default-history-count (get (deref ~name*) '~arg)))
          (def ~(concat-symbols arg '>>) #(take % (get (deref ~name*) '~arg))))
     arg)))


(defn define-args
  ([args]
   (reduce (fn [body arg]
             (if (symbol? arg)
               (concat body (list (arg->def-args arg)))
               (concat body (arg->def-args arg))))
           ()
           args))
  ([name* args]
   (reduce (fn [body arg]
             (if (symbol? arg)
               (concat body (list (arg->def-args name* arg)))
               (concat body (arg->def-args name* arg))))
           ()
           args)))


(defn autogensym?
  "Returns true if symbol is an autogensym or gensym symbol"
  [x]
  (boolean (or (re-matches #".*__\d.*__auto__" (str x))
               (re-matches #".*__\d*" (str x)))))


(defn insert-into-let
  ([bindings]
   (reduce (fn [acc [var* value]]
             ;; if the var is introduced by 
             ;; clojure's destructuring it makes 
             ;; no sense to globally define it, since no one
             ;; will know about it, and its wasteful.
             (if (autogensym? var*)
               (conj acc var* value)
               (let [defined-args (define-args [var*])
                     defined-args* (interleave (repeat '_) defined-args)]
                 (apply conj (conj acc var* value) defined-args*))))
           []
           (partition 2 bindings)))
  ([name* bindings]
   (reduce (fn [acc [var* value]]
             ;; if the var is introduced by 
             ;; clojure's destructuring it makes 
             ;; no sense to globally define it, since no one
             ;; will know about it, and its wasteful.
             (if (autogensym? var*)
               (conj acc var* value)
               (let [defined-args (define-args name* [var*])
                     defined-args* (interleave (repeat '_) defined-args)]
                 (apply conj (conj acc var* value) defined-args*))))
           []
           (partition 2 bindings))))


(defn define-let-bindings
  ([body]
   (cond
     (not (seq? body))
     body

     (binding-form? body)
     (let [[l* bindings & inner-body] body
           inner-body* (define-let-bindings inner-body)]
       `(~l* ~(insert-into-let (destructure bindings))
             ~@inner-body*))

     :else
     (map define-let-bindings body)))
  ([name* body]
   (cond
     (not (seq? body))
     body

     (binding-form? body)
     (let [[l* bindings & inner-body] body
           inner-body* (define-let-bindings name* inner-body)]
       `(~l* ~(insert-into-let name* (destructure bindings))
             ~@inner-body*))

     :else
     (map #(define-let-bindings name* %) body))))


(defn maybe-destructured
  "Taken from clojure core.
  Defining it here so the code can be compatible with cljs."
  [params body]
  (if (every? symbol? params)
    (cons params body)
    (loop [params params
           new-params (with-meta [] (meta params))
           lets []]
      (if params
        (if (symbol? (first params))
          (recur (next params) (conj new-params (first params)) lets)
          (let [gparam (gensym "p__")]
            (recur (next params) (conj new-params gparam)
                   (-> lets (conj (first params)) (conj gparam)))))
        `(~new-params
          (let ~lets
            ~@body))))))


(defn atom-for-fn
  [atom-name]
  `(if (instance? clojure.lang.Atom ~atom-name)
     (def ~atom-name ~atom-name)
     (def ~atom-name (atom {}))))


(defn function-to-reset-atom
  [fn-name atom-name]
  (let [fn-name* (concat-symbols fn-name '!)]
    `(def ~fn-name*
       (fn []
         (swap! ~atom-name (constantly {}))))))


(defn define-in-variadic-forms
  ([name form]
   (let [params (first form)
         prepost-map? (when (map? (second form))
                        (second form))
         body (if (nil? prepost-map?)
                (rest form)
                (rest (rest form)))
         [params* & body*] (maybe-destructured params body)
         atom-name (concat-symbols name '_)
         params-def (define-args params*)
         params-def* (cons `(declare ~atom-name)
                           (cons (atom-for-fn atom-name)
                                 (cons (function-to-reset-atom name atom-name)
                                       params-def)))]


     (if (some? prepost-map?)
       `(~params* ~@params-def*
                  ~prepost-map?
                  (let [result# (do ~@(define-let-bindings atom-name body*))]
                    (def ~(concat-symbols name '<) result#)
                    (def ~(concat-symbols name '>) (deref ~atom-name))
                    result#))
       `(~params* ~@params-def*
                  (let [result# (do ~@(define-let-bindings atom-name body*))]
                    (def ~(concat-symbols name '<) result#)
                    (def ~(concat-symbols name '>) (deref ~atom-name))
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
        [params** & body*] (when (every? some? [body params*])
                             (maybe-destructured params* body))
        atom-name (concat-symbols name '_)
        params-def (when (some? params**)
                     (define-args atom-name params**))
        params-def* (cons `(declare ~atom-name)
                          (cons (atom-for-fn atom-name)
                                (cons (function-to-reset-atom name atom-name)
                                      params-def)))
        body** (when (some? body*)
                 (define-let-bindings atom-name body*))
        variadic-defs* (map #(define-in-variadic-forms name %) variadic-defs)

        args-to-defn (list doc-string? attr-map? params** prepost-map?)
        args-to-defn* (remove nil? args-to-defn)]

    (if (some? variadic-defs)
      `(defn ~name ~@args-to-defn*
         ~@variadic-defs*)

      `(defn ~name ~@args-to-defn*
         ~@params-def*
         (let [result#
               (do ~@body**)]
           (def ~(concat-symbols name '<) result#)
           (def ~(concat-symbols name '>) (deref ~atom-name))
           result#)))))


;; FIXME: defmethod's arg history should be namespaced.
;; method-name+dispatch-value 
(defmacro defmethod*
  [name dispatch-value & forms]
  `(defmethod ~name ~dispatch-value
     ~@(map #(define-in-variadic-forms name %) forms)))


(defmacro *let
  [bindings & body]
  (define-let-bindings (cons 'let (cons bindings body))))


(defmacro *fn
  [& forms]
  (let [[name* forms] (if (symbol? (first forms))
                        [(first forms) (rest forms)]
                        ['this forms])
        exp (macroexpand-1 `(defn* ~name* ~@forms))]
    (cons 'fn (rest exp))))
