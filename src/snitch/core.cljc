(ns snitch.core
  (:refer-clojure :rename {destructure cc-destructure}))


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
   ;; FIXME: commenting out the history feature since it doesn't work with cljs yet
   (arg->def-args arg)
   #_(if (symbol? arg)
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


(defn cljs-destructure
  "Taken from cljs/core.cljc"
  [bindings]
  (prn "I got called")
  (let [bents (partition 2 bindings)
        pb (fn pb
             [bvec b v]
             (let [pvec
                   (fn [bvec b val]
                     (let [gvec (gensym "vec__")
                           gseq (gensym "seq__")
                           gfirst (gensym "first__")
                           has-rest (some #{'&} b)]
                       (loop [ret (let [ret (conj bvec gvec val)]
                                    (if has-rest
                                      (conj ret gseq (list `seq gvec))
                                      ret))
                              n 0
                              bs b
                              seen-rest? false]
                         (if (seq bs)
                           (let [firstb (first bs)]
                             (cond
                               (= firstb '&) (recur (pb ret (second bs) gseq)
                                                    n
                                                    (nnext bs)
                                                    true)
                               (= firstb :as) (pb ret (second bs) gvec)
                               :else (if seen-rest?
                                       (throw #?(:clj (new Exception "Unsupported binding form, only :as can follow & parameter")
                                                 :cljs (new js/Error "Unsupported binding form, only :as can follow & parameter")))
                                       (recur (pb (if has-rest
                                                    (conj ret
                                                          gfirst `(first ~gseq)
                                                          gseq `(next ~gseq))
                                                    ret)
                                                  firstb
                                                  (if has-rest
                                                    gfirst
                                                    (list `nth gvec n nil)))
                                              (inc n)
                                              (next bs)
                                              seen-rest?))))
                           ret))))
                   pmap
                   (fn [bvec b v]
                     (let [gmap (gensym "map__")
                           defaults (:or b)]
                       (loop [ret (-> bvec (conj gmap) (conj v)
                                      (conj gmap) (conj `(--destructure-map ~gmap))
                                      ((fn [ret]
                                         (if (:as b)
                                           (conj ret (:as b) gmap)
                                           ret))))
                              bes (let [transforms
                                        (reduce
                                          (fn [transforms mk]
                                            (if (keyword? mk)
                                              (let [mkns (namespace mk)
                                                    mkn (name mk)]
                                                (cond (= mkn "keys") (assoc transforms mk #(keyword (or mkns (namespace %)) (name %)))
                                                      (= mkn "syms") (assoc transforms mk #(list `quote (symbol (or mkns (namespace %)) (name %))))
                                                      (= mkn "strs") (assoc transforms mk str)
                                                      :else transforms))
                                              transforms))
                                          {}
                                          (keys b))]
                                    (reduce
                                      (fn [bes entry]
                                        (reduce #(assoc %1 %2 ((val entry) %2))
                                                (dissoc bes (key entry))
                                                ((key entry) bes)))
                                      (dissoc b :as :or)
                                      transforms))]
                         (if (seq bes)
                           (let [bb (key (first bes))
                                 bk (val (first bes))
                                 local (if #?(:clj  (instance? clojure.lang.Named bb)
                                              :cljs (implements? INamed bb))
                                         (with-meta (symbol nil (name bb)) (meta bb))
                                         bb)
                                 bv (if (contains? defaults local)
                                      (list 'cljs.get gmap bk (defaults local))
                                      (list 'cljs.get gmap bk))]
                             (recur
                               (if (or (keyword? bb) (symbol? bb)) ; (ident? bb)
                                 (-> ret (conj local bv))
                                 (pb ret bb bv))
                               (next bes)))
                           ret))))]
               (cond
                 (symbol? b) (-> bvec (conj (if (namespace b) (symbol (name b)) b)) (conj v))
                 (keyword? b) (-> bvec (conj (symbol (name b))) (conj v))
                 (vector? b) (pvec bvec b v)
                 (map? b) (pmap bvec b v)
                 :else (throw
                         #?(:clj (new Exception (str "Unsupported binding form: " b))
                            :cljs (new js/Error (str "Unsupported binding form: " b)))))))
        process-entry (fn [bvec b] (pb bvec (first b) (second b)))]
    (if (every? symbol? (map first bents))
      bindings
      (if-let [kwbs (seq (filter #(keyword? (first %)) bents))]
        (throw
          #?(:clj (new Exception (str "Unsupported binding key: " (ffirst kwbs)))
             :cljs (new js/Error (str "Unsupported binding key: " (ffirst kwbs)))))
        (reduce process-entry [] bents)))))


(defn define-let-bindings
  ([body]
   (cond
     (not (seq? body))
     body

     (binding-form? body)
     (let [[l* bindings & inner-body] body
           inner-body* (define-let-bindings inner-body)]
       `(~l* ~(insert-into-let #?(:clj (cc-destructure bindings)
                                  :cljs (cljs-destructure bindings)))
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
       `(~l* ~(insert-into-let name* #?(:clj (cc-destructure bindings)
                                        :cljs (cljs-destructure bindings)))
             ~@inner-body*))

     :else
     (map #(define-let-bindings name* %) body))))


(defn cljs
  [a b]
  (prn "cljs"))


(defn clj
  [a b]
  (prn "clj"))


(defn foo-bar
  [x]
  #?(:clj (clj 1 2)
     :cljs (cljs 3 4)))


(foo-bar 3)


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
         ;; FIXME commenting out the history feature because it doesn't work in cljs yet.
         params-def* params-def #_(cons `(declare ~atom-name)
                           (cons (atom-for-fn atom-name)
                                 (cons (function-to-reset-atom name atom-name)
                                       params-def)))]


     (if (some? prepost-map?)
       `(~params* ~@params-def*
                  ~prepost-map?
                  (let [result# (do ~@(define-let-bindings atom-name body*))]
                    (def ~(concat-symbols name '<) result#)
                    ;; FIXME commenting out the history feature because it doesn't work in cljs yet.
                    #_(def ~(concat-symbols name '>) (deref ~atom-name))
                    result#))
       `(~params* ~@params-def*
                  (let [result# (do ~@(define-let-bindings atom-name body*))]
                    (def ~(concat-symbols name '<) result#)
                    ;; FIXME commenting out the history feature because it doesn't work in cljs yet.
                    #_(def ~(concat-symbols name '>) (deref ~atom-name))
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

        _ (def params* params*)
        _ (def body body)
        _ (def params** params**)
        _ (def body* body*)
        atom-name (concat-symbols name '_)
        params-def (when (some? params**)
                     (define-args #_atom-name params**))
        ;; FIXME commenting out the history feature because it doesn't work in cljs yet.
        params-def* params-def #_(cons `(declare ~atom-name)
                          (cons (atom-for-fn atom-name)
                                (cons (function-to-reset-atom name atom-name)
                                      params-def)))
        body** (when (some? body*)
                 (define-let-bindings #_atom-name body*))
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
           ;; FIXME commenting out the history feature because it doesn't work in cljs yet.
           #_(def ~(concat-symbols name '>) (deref ~atom-name))
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
