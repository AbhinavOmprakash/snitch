(ns snitch.core)


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
   (let [prefix* (if (seq prefix) (concat-symbols prefix "-") prefix)]
     (cond
       (symbol? arg)
       `(def ~(concat-symbols prefix* arg suffix) ~arg)

       (vector? arg)
       (map (partial arg->def-args prefix* suffix) arg)

       (seq? arg)
       (map (partial arg->def-args prefix* suffix) arg)

       (map? arg)
       (let [keys* (remove keyword? (keys arg))
             map-name (arg->def-args prefix* suffix (:as arg))
             map-name* (if (nil? map-name)
                         nil
                         (list map-name))]
         (concat (arg->def-args prefix* suffix keys*)
                 (arg->def-args prefix* suffix (:keys arg))
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
   `(~bindings ~@(define-args prefix suffix (extract-bindings bindings)))))


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
       `(~l* ~@(insert-into-let prefix suffix bindings)
             ~@inner-body*))

     :else
     (map (partial define-let-bindings prefix suffix) body))))


(defmacro defn*
  ([name params* body]
   `(defn ~name ~params*
      ~@(define-args params*)
      ~(define-let-bindings body)))
  ([name doc-string? params* body]
   `(defn ~name ~doc-string? ~params*
      ~@(define-args params*)
      ~(define-let-bindings body)))

  ([name doc-string? attr-map? params* body]
   `(defn ~name ~doc-string? ~attr-map? ~params*
      ~@(define-args params*)
      ~(define-let-bindings body)))

  ([name doc-string? attr-map? params* prepost-map? body]
   `(defn ~name ~doc-string? ~attr-map? ~params* ~prepost-map?
      ~@(define-args params*)
      ~(define-let-bindings body))))


(comment
  (hey "you")


  (macroexpand-1 '(defn* hey [x]
                    (let [a 1 b 2]
                      (print a)
                      x)))
; (clojure.core/defn
;  hey
;  [x]
;  (def x x)
;  (let [a 1 b 2] (def a a) (def b b) (print a) x))


;; (clojure.core/defn
;;  hey
;;  [x]
;;  (def x x)
;;  (let [a 1 b 2] (def a a) (def b b) (print a)))

  (macroexpand-1 '(defn* hey [x y [a1 a2] {idex i :keys [k1 k2] :as m}]
                    x))


;; (clojure.core/defn
;;  hey
;;  [x y [a1 a2] {idex i, :keys [k1 k2], :as m}]
;;  (def x x)
;;  (def y y)
;;  (def a1 a1)
;;  (def a2 a2)
;;  (def idex idex)
;;  (def k1 k1)
;;  (def k2 k2)
;;  (def m m)
;;  x)


;; (clojure.core/defn
;;  hey
;;  [x y [a1 a2] {:keys [k1 k2], :as m}]
;;  (def x x)
;;  (def y y)
;;  ((def a1 a1) (def a2 a2))
;;  ((def m m) (def k1 k1) (def k2 k2))
;;  x)


  (seq 'a)

  (define-let-bindings '(do  (print d) (if-let [x 1 y 2] y)))

  (define-let-bindings '(let [d 1 e 2] (do  (print d) (let [x 1 y 2] y))))


;; (let
;;  [d 1 e 2]
;;  (def d d)
;;  (def e e)
;;  (do (print d) (let [x 1 y 2] (def x x) (def y y) y)))


  (let-form? (if (seq '(let [d 1 e 2]))
               `(~@(macroexpand '(let [d 1 e 2])))
               '(let [d 1 e 2])))


  (define-let-bindings '(if-let [d 1 e 2] (do  (print d) (let [x 1 y 2] y))))
; (if-let
;  ""
;  (def dclojure.lang.LazySeq@1 d)
;  (def eclojure.lang.LazySeq@1 e)
;  ())

  (define-let '(let [d 1 e 2]))

  (arg->def-args 'a)

  (arg->def-args ['a 'b])

  (arg->def-args {:keys ['a 'b] :as 'alphamap})

  a

  (hey "heyllooooooo" "bae")


  (let-form? '(let [d 1 e 2])) ; 

  (=  (first '(let* [d 1 e 2])) 'let*)


  (defn* hey [x]
    (print x))


  (extract-bindings '(let [a b
                           c d]))


  (insert-into-let '[a 1 b 2])
  (define-args (extract-bindings '[a 1 b 2]))


  (macroexpand-1 '(defn* arg->def-args
                    [suffix arg]
                    (cond
                      (symbol? arg)
                      `(def ~(concat-symbols arg suffix) ~arg)

                      (vector? arg)
                      (map (partial arg->def-args suffix) arg)

                      (seq? arg)
                      (map arg->def-args arg suffix)

                      (map? arg)
                      (let [keys* (remove keyword? (keys arg))]
                        (concat (arg->def-args suffix keys*)
                                (arg->def-args suffix (:keys arg))
                                (list (arg->def-args suffix (:as arg)))))))) ; (clojure.core/defn arg->def-args [suffix arg] () () ())
  (arg->def-args "" {'idx 'index :keys ['a 'b]})

  suffix)
