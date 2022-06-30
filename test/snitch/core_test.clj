(ns snitch.core-test
  (:require
    [clojure.test :refer :all]
    [snitch.core :refer [concat-symbols define-args defn* define-let-bindings defmethod*]]
    [snitch.test-utils]))


(deftest test-concat-symbols
  (testing "concat a symbol and a symbol"
    (is (= (concat-symbols 'a 'b) 'ab)))

  (testing "concat a symbol and a string"
    (is (= (concat-symbols 'a "b") 'ab)))

  (testing "concat a string and a string to get a symbol"
    (is (= (concat-symbols "a" "b") 'ab))))


(deftest test-define-args
  (testing "Params list with simple symbols"
    (is (expansion-valid? (define-args ['a 'b])
                          ((def a a) (def b b))))))


(deftest test-define-let-bindings
  (testing "body with let form as first form"
    (is (expansion-valid? (define-let-bindings '(let [a 1 b 2] a))
                          (let [a 1
                                _ (def a a)
                                b 2
                                _ (def b b)]
                            a)))
    (is (expansion-valid? (define-let-bindings '(let [a 1 b 2] (print a)))
                          (let
                            [a 1
                             _ (def a a)
                             b 2
                             _ (def b b)]
                            (print a)))))

  (testing "body with let form not as first form"
    (is (expansion-valid? (define-let-bindings '((print "x")
                                                 (let [a 1]
                                                   a)))
                          ((print "x") (let [a 1 _ (def a a)] a)))))

  (testing "body with nested let forms"
    (is (expansion-valid? (define-let-bindings '(let [a 1
                                                      b 2]
                                                  (print a)
                                                  (let [x 3
                                                        y 4]
                                                    x)))
                          (let
                            [a 1 _ (def a a) b 2 _ (def b b)]
                            (print a)
                            (let [x 3 _ (def x x) y 4 _ (def y y)] x))))
    (is (expansion-valid? (define-let-bindings '(let [a 1]
                                                  (print a)
                                                  (let [x 2]
                                                    (str x)
                                                    (let [i 3]
                                                      i))))
                          (let
                            [a 1 _ (def a a)]
                            (print a)
                            (let
                              [x 2 _ (def x x)]
                              (str x)
                              (let [i 3 _ (def i i)] i))))))

  (testing "body with multiple non-nested lets"
    (is (expansion-valid? (define-let-bindings '(do (let [a 1] (print a))
                                                    (let [b 2] (str b))
                                                    (let [c 3] (symbol c))))

                          (do
                            (let [a 1 _ (def a a)] (print a))
                            (let [b 2 _ (def b b)] (str b))
                            (let [c 3 _ (def c c)] (symbol c))))))

  (testing "let with destructuring"
    (is (expansion-valid? (define-let-bindings '(let [[a b] [1 2]
                                                      c 3]))
                          (let
                            [vec__14298
                             [1 2]
                             a
                             (clojure.core/nth vec__14298 0 nil)
                             _
                             (def a a)
                             b
                             (clojure.core/nth vec__14298 1 nil)
                             _
                             (def b b)
                             c
                             3
                             _
                             (def c c)])))))


(deftest test-defn*
  (testing "defn* with name, params and body"
    (is (macro-valid? (defn* hey [x]
                        x)
                      (clojure.core/defn hey
                        [x]
                        (def x x)
                        (clojure.core/let
                          [result__182__auto__ (do x)]
                          (def hey> result__182__auto__)
                          result__182__auto__))))
    (is (macro-valid? (defn* hey [x]
                        (print x))
                      (clojure.core/defn hey
                        [x]
                        (def x x)
                        (clojure.core/let
                          [result__182__auto__ (do (print x))]
                          (def hey> result__182__auto__)
                          result__182__auto__))))
    (is (macro-valid? (defn* hey [x]
                        (let [y 2]
                          (print x)))
                      (clojure.core/defn hey
                        [x]
                        (def x x)
                        (clojure.core/let
                          [result__182__auto__ (do (let [y 2 _ (def y y)] (print x)))]
                          (def hey> result__182__auto__)
                          result__182__auto__)))))
  (testing "defn* with name, params and multiple forms in body"
    (is (macro-valid? (defn* hey [x]
                        (print x)
                        (print "x is" x)
                        x)
                      (clojure.core/defn hey
                        [x]
                        (def x x)
                        (clojure.core/let
                          [result__14118__auto__ (do (print x) (print "x is" x) x)]
                          (def hey> result__14118__auto__)
                          result__14118__auto__)))))
  (testing "defn* with variadic args"
    (is (macro-valid? (defn* hey
                        ([a] a)
                        ([a b] [a b]))
                      (clojure.core/defn hey
                        ([a]
                         (def a a)
                         (clojure.core/let
                           [result__13886__auto__ (do a)]
                           (def hey> result__13886__auto__)
                           result__13886__auto__))
                        ([a b]
                         (def a a)
                         (def b b)
                         (clojure.core/let
                           [result__13886__auto__ (do [a b])]
                           (def hey> result__13886__auto__)
                           result__13886__auto__)))))
    (is (macro-valid? (defn* hey
                        ([a] a)
                        ([a b]
                         (print [a b])
                         #{a b}))
                      (clojure.core/defn hey
                        ([a]
                         (def a a)
                         (clojure.core/let
                           [result__14624__auto__ (do a)]
                           (def hey> result__14624__auto__)
                           result__14624__auto__))
                        ([a b]
                         (def a a)
                         (def b b)
                         (clojure.core/let
                           [result__14624__auto__ (do (print [a b]) #{a b})]
                           (def hey> result__14624__auto__)
                           result__14624__auto__))))))

  (testing "defn* with docstrings"
    (is (macro-valid? (defn* hey
                        "prints x"
                        [x]
                        (let [y 2]
                          (print x)))
                      (clojure.core/defn hey
                        "prints x"
                        [x]
                        (def x x)
                        (clojure.core/let
                          [result__13892__auto__ (do (let [y 2 _ (def y y)] (print x)))]
                          (def hey> result__13892__auto__)
                          result__13892__auto__)))))

  (testing "defn* with docstrings, attr-maps, and prepost-maps."
    (is (macro-valid? (defn* hey
                        "prints x"
                        {:added 1.0}
                        [x]
                        (let [y 2]
                          (print x)))

                      (clojure.core/defn hey
                        "prints x"
                        {:added 1.0}
                        [x]
                        (def x x)
                        (clojure.core/let
                          [result__182__auto__ (do (let [y 2 _ (def y y)] (print x)))]
                          (def hey> result__182__auto__)
                          result__182__auto__))))
    (is (macro-valid? (defn* hey
                        "prints x"
                        {:added 1.0}
                        [x]
                        {:pre []
                         :post []}
                        (let [y 2]
                          (print x)))
                      (clojure.core/defn hey
                        "prints x"
                        {:added 1.0}
                        [x]
                        {:pre [], :post []}
                        (def x x)
                        (clojure.core/let
                          [result__182__auto__ (do (let [y 2 _ (def y y)] (print x)))]
                          (def hey> result__182__auto__)
                          result__182__auto__)))))

  (testing "defn* works when function returns a map, and does not mistake it for a pre post map"
    (is (macro-valid? (defn* hey
                        "prints x"
                        [x]
                        {:val x})
                      (clojure.core/defn hey
                        "prints x"
                        [x]
                        (def x x)
                        (clojure.core/let
                          [result__182__auto__ (do {:val x})]
                          (def hey> result__182__auto__)
                          result__182__auto__))))
    (is (macro-valid? (defn* hey
                        "prints x"
                        [x]
                        {:pre []}
                        {:val x})
                      (clojure.core/defn hey
                        "prints x"
                        [x]
                        {:pre []}
                        (def x x)
                        (clojure.core/let
                          [result__182__auto__ (do {:val x})]
                          (def hey> result__182__auto__)
                          result__182__auto__))))))


(deftest test-destructuring-in-defn*
  (is (macro-valid? (defn* hey
                      "prints x"
                      [{:keys [a b c]} [x [y [z]]]]
                      {:val x})

                    (clojure.core/defn hey
                      "prints x"
                      [p__14270 p__14271]
                      (def p__14270 p__14270)
                      (def p__14271 p__14271)
                      (clojure.core/let
                        [result__14172__auto__
                         (do
                           (clojure.core/let
                             [map__14272
                              p__14270
                              map__14272
                              (if
                                (clojure.core/seq? map__14272)
                                (clojure.lang.PersistentHashMap/create
                                  (clojure.core/seq map__14272))
                                map__14272)
                              a
                              (clojure.core/get map__14272 :a)
                              _
                              (def a a)
                              b
                              (clojure.core/get map__14272 :b)
                              _
                              (def b b)
                              c
                              (clojure.core/get map__14272 :c)
                              _
                              (def c c)
                              vec__14273
                              p__14271
                              x
                              (clojure.core/nth vec__14273 0 nil)
                              _
                              (def x x)
                              vec__14276
                              (clojure.core/nth vec__14273 1 nil)
                              y
                              (clojure.core/nth vec__14276 0 nil)
                              _
                              (def y y)
                              vec__14279
                              (clojure.core/nth vec__14276 1 nil)
                              z
                              (clojure.core/nth vec__14279 0 nil)
                              _
                              (def z z)]
                             {:val x}))]
                        (def hey> result__14172__auto__)
                        result__14172__auto__))))
  (is (macro-valid? (defn* hey
                      "prints x"
                      [x]
                      (let [{:keys  [a/b c d]} {:a/b 1 :c 2 :d 3}
                            [x [y [{:keys [m]}]]] [7 [8 [{:m 9}]]]]
                        #{b c d x y m}))
                    (clojure.core/defn hey
                      "prints x"
                      [x]
                      (def x x)
                      (clojure.core/let
                        [result__14172__auto__
                         (do
                           (let
                             [map__14284
                              {:a/b 1, :c 2, :d 3}
                              map__14284
                              (if
                                (clojure.core/seq? map__14284)
                                (clojure.lang.PersistentHashMap/create
                                  (clojure.core/seq map__14284))
                                map__14284)
                              b
                              (clojure.core/get map__14284 :a/b)
                              _
                              (def b b)
                              c
                              (clojure.core/get map__14284 :c)
                              _
                              (def c c)
                              d
                              (clojure.core/get map__14284 :d)
                              _
                              (def d d)
                              vec__14285
                              [7 [8 [{:m 9}]]]
                              x
                              (clojure.core/nth vec__14285 0 nil)
                              _
                              (def x x)
                              vec__14288
                              (clojure.core/nth vec__14285 1 nil)
                              y
                              (clojure.core/nth vec__14288 0 nil)
                              _
                              (def y y)
                              vec__14291
                              (clojure.core/nth vec__14288 1 nil)
                              map__14294
                              (clojure.core/nth vec__14291 0 nil)
                              map__14294
                              (if
                                (clojure.core/seq? map__14294)
                                (clojure.lang.PersistentHashMap/create
                                  (clojure.core/seq map__14294))
                                map__14294)
                              m
                              (clojure.core/get map__14294 :m)
                              _
                              (def m m)]
                             #{x y m c b d}))]
                        (def hey> result__14172__auto__)
                        result__14172__auto__)))))


(deftest test-behaviour-of-defn*
  (let [_ (defn* foo-test [{:keys [a/b1 c2]
                            dee3 :d
                            :as m4}
                           [x5 [y6 [z7]]]]
            [b1 c2 dee3 m4 x5 y6 z7])
        _ (foo-test {:a/b1 1 :c2 2 :d 3} [5 [6 [7]]])

        expected-b 1
        expected-c 2
        expected-d 3
        expected-x 5
        expected-y 6
        expected-z 7
        expected-m {:a/b1 1 :c2 2 :d 3}
        expected-output [1 2 3 {:a/b1 1, :c2 2, :d 3} 5 6 7]]
    ;; b1, c2 etc are globally defined by the macro
    ;; defn*
    (is (= expected-b b1))
    (is (= expected-c c2))
    (is (= expected-d dee3))
    (is (= expected-m m4))
    (is (= expected-x x5))
    (is (= expected-y y6))
    (is (= expected-z z7))
    (is (= expected-output foo-test>)))

  (let [_ (defn* foo-test- [p1]
            (let  [{:keys [a/bb1 cc2]
                    ddee3 :dd
                    :as mm4}
                   {:a/bb1 1 :cc2 2 :dd 3}

                   [xx5 [yy6 [zz7]]]
                   [5 [6 [7]]]]
              [p1 bb1 cc2 ddee3 mm4 xx5 yy6 zz7]))
        _ (foo-test- "pee")

        expected-b 1
        expected-c 2
        expected-d 3
        expected-x 5
        expected-y 6
        expected-z 7
        expected-m {:a/bb1 1 :cc2 2 :dd 3}
        expected-p "pee"
        expected-output ["pee" 1 2 3 {:a/bb1 1, :cc2 2, :dd 3} 5 6 7]]
    ;; bb1, cc2 etc are globally defined by the macro
    ;; defn*
    (is (= expected-b bb1))
    (is (= expected-c cc2))
    (is (= expected-d ddee3))
    (is (= expected-m mm4))
    (is (= expected-x xx5))
    (is (= expected-y yy6))
    (is (= expected-z zz7))
    (is (= expected-p p1))
    (is (= expected-output foo-test->))))


(deftest test-defmethod*
  (testing "defmethod* with name dispatch-value and a body"
    (is (macro-valid? (defmethod* foomethod :dispatch-value
                        ([_ arg]
                         arg))
                      (clojure.core/defmethod
                        foomethod
                        :dispatch-value
                        ([_ arg]
                         (def _ _)
                         (def arg arg)
                         (clojure.core/let
                           [result__15095__auto__ (do arg)]
                           (def foomethod> result__15095__auto__)
                           result__15095__auto__)))))

    (is (macro-valid? (defmethod* foomethod :dispatch-value
                        ([_ arg]
                         arg)
                        ([_ arg arg2]
                         [arg arg2]))
                      (clojure.core/defmethod
                        foomethod
                        :dispatch-value
                        ([_ arg]
                         (def _ _)
                         (def arg arg)
                         (clojure.core/let
                           [result__15095__auto__ (do arg)]
                           (def foomethod> result__15095__auto__)
                           result__15095__auto__))
                        ([_ arg arg2]
                         (def _ _)
                         (def arg arg)
                         (def arg2 arg2)
                         (clojure.core/let
                           [result__15095__auto__ (do [arg arg2])]
                           (def foomethod> result__15095__auto__)
                           result__15095__auto__)))))))


(deftest test-behaviour-of-defmethod*
  (let [_ (defmulti foomethod (fn
                                ([a11 _] a11)
                                ([a11 _ _] a11)))
        - (defmethod* foomethod "a"
            ([a11 {:keys [d11] :as b11}] b11)
            ([a11 {:keys [d11] :as b11} c11] c11))
        _ (foomethod "a" {:d11 :foomethod} 2)]
    (is (= "a" a11))
    (is (= {:d11 :foomethod} b11))
    (is (= :foomethod d11))
    (is (= 2 c11))))


(macroexpand-1 '(defmethod* foomethod "a"
    ([a11 {:keys [d11] :as b11}] b11)
    ([a11 {:keys [d11] :as b11} c11] c11)) )
