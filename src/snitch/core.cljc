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


;;  
(defn keyword-ns
  "naive function to get the ns part of a namespaced keyword"
  [k]
  (-> k
      str
      (s/split #"/")
      first
      (s/replace #":" "")
      symbol))


(defn concat-symbols
  [& symbols]
  (symbol (apply str symbols)))


(defn let-form?
  [form]
  (boolean
    (when (seq? form)
      (some #{(first form)}
            ['let
             'clojure.core/let]))))


(defn expands-to-let?
  [form]
  (boolean
    (when (seq? form)
      (some #{(first form)}
            ['if-let 'when-let]))))


(defn arg->def-args
  ([arg]
   (if (symbol? arg)
     `(def ~arg ~arg)
     arg)))


(defn cc-destructure
  "A slightly modified version of clj and cljs' destructure to 
  work with clj and cljs."
  [bindings]
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
                                                          gfirst `(~'first ~gseq)
                                                          gseq `(~'next ~gseq))
                                                    ret)
                                                  firstb
                                                  (if has-rest
                                                    gfirst
                                                    (list `~'nth gvec n nil)))
                                              (inc n)
                                              (next bs)
                                              seen-rest?))))
                           ret))))
                   pmap
                   (fn [bvec b v]
                     (let [gmap (gensym "map__")
                           gmapseq (with-meta gmap {:tag 'clojure.lang.ISeq})
                           defaults (:or b)]
                       (loop [ret (-> bvec (conj gmap) (conj v)
                                      (conj gmap) (conj `(~'if (~'seq? ~gmap)
                                                               (~'apply ~'hash-map (~'seq ~gmapseq))
                                                               ~gmap))
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
                                      (list 'get gmap bk (defaults local))
                                      (list 'get gmap bk))]
                             (recur (if (ident? bb)
                                      (-> ret (conj local bv))
                                      (pb ret bb bv))
                                    (next bes)))
                           ret))))]
               (cond
                 (symbol? b) (-> bvec (conj b) (conj v))
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


(defn define-args
  ([args]
   (reduce (fn [body arg]
             (if (symbol? arg)
               (concat body (list (arg->def-args arg)))
               (concat body (arg->def-args arg))))
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
           (partition 2 bindings))))


(defn define-let-bindings
  ([body]
   (cond
     (not (seq? body))
     body

     (expands-to-let? body)
     (recur (macroexpand-1 body))

     (let-form? body)
     (let [[l* bindings & inner-body] body
           inner-body* (define-let-bindings inner-body)]
       `(~l* ~(insert-into-let (cc-destructure bindings))
             ~@inner-body*))

     :else
     (map define-let-bindings body))))


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
          (~'let ~lets
                 ~@body))))))


;; find better name
(defn namespaced-destructuring
  "namespaced maps can be destructured in two ways.
  [{:keys [a/b]}] #:a{:b 1}
  or 
  [{:a/keys [b]}] #:a{:b 1}

  This function handles the 2nd case. 
  Converts the 2nd case to the first case 
  "
  [k v]
  (let [v* (mapv (fn [sym]
                   (concat-symbols (keyword-ns k) '/ sym))
                 v)]
    v*))


;; find better name
(defn values->hashmap
  [acc v]
  (into {} (concat acc
                   (map (fn [x] [(keyword x) (->simple-symbol x)])
                        v))))


(defn construct-map
  [m]
  (reduce-kv (fn [acc k v]
               (cond
                 (= :keys k)
                 (values->hashmap acc v)

                 ;; if using other destructuring syntax
                 ;; like [{:ns/keys [a b]}]
                 (and (= (keyword (name k)) :keys)
                      (not (= k :keys)))
                 (values->hashmap acc (namespaced-destructuring k v))

                 (#{:as :or} k)
                 acc

                 :else
                 (assoc acc v (cond
                                (map? k) (construct-map k)
                                (vector? k) k
                                :else (->simple-symbol k)))))
             {}
             m))


(defn restructure
  "Opposite of destructure. required for reconstructing a map.
  vectors can have maps inside them."
  [args]
  (reduce (fn [acc x]
            (cond
              (map? x) (conj acc (construct-map x))
              (vector? x) (conj acc (restructure x))
              (= '& x) acc
              :else (conj acc x)))

          []
          args))


(defn replay-function
  "Returns a list to define a var with the name
  of the function appended with a >. When evaluated,
 the var will return a list with the name of the function and args to be passed.
  "
  ;; There is some hairy quoting going on here so let me explain
  ;; let's say we have a function (defn* foo [x] x)
  ;; and I call it (foo 1)
  ;; what I want from `replay-function` is to define a 
  ;; var such that when I evaluate it, it returns me a list (foo 1)
  ;; that I can evaluate.
  ;; notice that here foo is a symbol but 1 is the value of the symbol x
  [name args]
  `(~'def ~(concat-symbols name '>)
          `(~'~name ~~@(restructure args))))


(defn insert-replay-function
  [name params body]
  (if (and (seq? (first body))
           (= 'let (ffirst body)))
    (let [[[l* bind* & b*]] body]
      (list (concat [l* bind*] (list (replay-function name params)) b*)))
    (cons (replay-function name params) body)))


(defn define-in-variadic-forms
  ([name form]
   (let [params (first form)
         prepost-map? (when (map? (second form))
                        (second form))
         body (if (nil? prepost-map?)
                (rest form)
                (rest (rest form)))
         [params* & body*] (maybe-destructured params body)
         body** (insert-replay-function name params body*)
         params-def (define-args params*)]
     (if (some? prepost-map?)
       `(~params* ~@params-def
                  ~prepost-map?
                  (~'let [result# (~'do ~@(define-let-bindings body**))]
                         (~'def ~(concat-symbols name '<) result#)
                         result#))
       `(~params* ~@params-def
                  (~'let [result# (~'do ~@(define-let-bindings body**))]
                         (~'def ~(concat-symbols name '<) result#)
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
        body** (insert-replay-function name params* body*)
        params-def (when (some? params**)
                     (define-args  params**))
        ;; should prolly check if params* is not nil
        body*** (when (some? body**)
                  (define-let-bindings body**))
        variadic-defs* (map #(define-in-variadic-forms name %) variadic-defs)

        args-to-defn (list doc-string? attr-map? params** prepost-map?)
        args-to-defn* (remove nil? args-to-defn)]

    (if (some? variadic-defs)
      `(~'defn ~name ~@args-to-defn*
               ~@variadic-defs*)

      `(~'defn ~name ~@args-to-defn*
               ~@params-def
               (~'let [result#
                       (~'do ~@body***)]
                      (~'def ~(concat-symbols name '<) result#)
                      result#)))))


(defmacro defmethod*
  [name dispatch-value & forms]
  (let [head (first forms)
        tail (rest forms)
        ;; forms* needs to be a list of lists.(([a] a) ([a b] #{a b}))
        forms* (cond
                 (list? head) forms
                 (symbol? head) (if (list? (first tail))
                                  tail
                                  (list tail))
                 :else (list forms))
        method-name (when (symbol? head) (first forms))]
    (if method-name
      `(defmethod ~name ~dispatch-value ~method-name
         ~@(map #(define-in-variadic-forms name %) forms*))
      `(defmethod ~name ~dispatch-value
         ~@(map #(define-in-variadic-forms name %) forms*)))))


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

(comment 

  (defmulti foo identity)

(macroexpand-1 '(defn* foo [x] 
                  (if-let [a x] 
                    a 
                    "x")) ) ; (defn foo [x] (def x x) (let [result__247__auto__ (do (def foo> (clojure.core/sequence (clojure.core/seq (clojure.core/concat (clojure.core/list (quote foo)) (clojure.core/list x))))) (clojure.core/let [temp__2062__auto__ x] (if temp__2062__auto__ (clojure.core/let [a temp__2062__auto__ _ (def a a)] a) x)))] (def foo< result__247__auto__) result__247__auto__))



(foo nil)
  (macroexpand-1 '(defmethod* foo :a name-of-metho [x] x) )
  (foo :a)



(foo :a)

(comment 

  (defmulti foo identity)

(macroexpand-1 '(defn* foo [x] 
                  (if-let [a x] 
                    a 
                    "x")) ) ; (defn foo [x] (def x x) (let [result__247__auto__ (do (def foo> (clojure.core/sequence (clojure.core/seq (clojure.core/concat (clojure.core/list (quote foo)) (clojure.core/list x))))) (clojure.core/let [temp__2062__auto__ x] (if temp__2062__auto__ (clojure.core/let [a temp__2062__auto__ _ (def a a)] a) x)))] (def foo< result__247__auto__) result__247__auto__))



(foo nil)
  (macroexpand-1 '(defmethod* foo :a name-of-metho [x] x) )
  (foo :a)



(foo :a)

(macroexpand-1 '(defn* foo [[ x ]] x ) ); 

(macroexpand-1 '(defn* foo [{:keys [a b] etch :h}] 
    [a b etch]) )

(restructure '[{{:keys [a]} :b}])

(let [{{:keys [a]} :b} {:b {:a 1}} ] a)

(macroexpand-1 '(defn* foo ([[x] ] x)
    ([[x] [y]] 
     [x y])) )

(foo [[1]]  )
foo* 
(defn* foo [x] 
     [ x ] )


(foo 1 )

(foo {:a 1 :b 2 :h 3 :d 4 :z 5 })

foo* ; (foo {:a 1, :b 2, :h 3})
(foo {:a 1, :b 2, :h 4})


foo* 
(let [[ [l* bind* & b*] ] body*]
  (concat [ l*  bind*] (list '(def x 1 ) ) b* ))

( (*fn [x]
       (if-let [y x]
         x)) 1)
)
