(ns snitch.core-test
  (:require
    [clojure.test :refer :all]
    [snitch.core :refer [concat-symbols arg->def-args define-args define-let-bindings defn*]]
    [snitch.test-utils]))


(deftest test-concat-symbols
  (testing "concat a symbol and a symbol"
    (is (= (concat-symbols 'a 'b) 'ab)))

  (testing "concat a symbol and a string"
    (is (= (concat-symbols 'a "b") 'ab)))

  (testing "concat a string and a string to get a symbol"
    (is (= (concat-symbols "a" "b") 'ab))))


(deftest test-arg->def-args
  (testing "simple symbol"
    (is (expansion-valid? (arg->def-args 'a) (def a a))))

  (testing "Vector Destructuring"
    (is (expansion-valid? (arg->def-args ['a 'b])
                          ((def a a) (def b b))))

    (testing "Map Destructuring"
      (is (expansion-valid? (arg->def-args {:keys ['a 'b]})
                            ((def a a) (def b b))))

      (is (expansion-valid? (arg->def-args {:keys ['a 'b] :as 'c})
                            ((def a a) (def b b) (def c c))))

      (is (expansion-valid? (arg->def-args {'idx 'index :keys ['a 'b]})
                            ((def idx idx) (def a a) (def b b))))
      (is (expansion-valid? (arg->def-args {:keys ['ns/foo]})
                            ((def foo foo)))
          "Should be able to def qualified symbols"))))


(deftest test-define-args
  (testing "Params list with simple symbols"
    (is (expansion-valid? (define-args ['a 'b])
                          ((def a a) (def b b)))))

  (testing "Params list with vector destructuring"
    (is (expansion-valid? (define-args ['a ['b 'c]])
                          ((def a a) (def b b) (def c c)))))

  (testing "Params list with map destructuring"
    (is (expansion-valid? (define-args ['x {:keys ['a 'b] :as 'c}])
                          ((def x x) (def a a) (def b b) (def c c)))))

  (testing "Params list with simple symbols, vector and map destructuring"
    (is (expansion-valid? (define-args ['x ['j 'k] {:keys ['a 'b] :as 'c}])
                          ((def x x) (def j j) (def k k) (def a a) (def b b) (def c c))))))


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
                          (let [[a b] [1 2]
                                _ (def a a)
                                _ (def b b)
                                c 3
                                _ (def c c)])))))


#_(deftest test-defn*
  (testing "defn* with name, params and body"
    (is (macro-valid? (defn* hey [x]
                        x)
                      (clojure.core/defn hey
                        [x]
                        (def x x)
                        x)))
    (is (macro-valid? (defn* hey [x]
                        (print x))
                      (clojure.core/defn hey
                        [x]
                        (def x x) (print x))))
    (is (macro-valid? (defn* hey [x]
                        (let [y 2]
                          (print x)))
                      (clojure.core/defn hey
                        [x]
                        (def x x) (let [y 2 _ (def y y)] (print x))))))
  (testing "defn* with variadic args"
    (is (macro-valid? (defn* hey
                        ([a] a)
                        ([a b] [a b]))
                      (clojure.core/defn hey
                        ([a]
                         (def a a)
                         a)
                        ([a b]
                         (def a a)
                         (def b b)
                         [a b])))))
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
                        (let [y 2 _ (def y y)] (print x)))))
    (is (macro-valid? (defn* hey
                        "prints x"
                        ([x]
                         x)
                        ([x y] [x y]))
                      (clojure.core/defn hey
                        "prints x"
                        ([x] (def x x) x)
                        ([x y] (def x x) (def y y) [x y])))))

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
                        (let [y 2 _ (def y y)] (print x)))))
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
                        (let [y 2] (def y y) (print x))))))
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
                          [result__12833__auto__ {:val x}]
                          (def hey> result__12833__auto__) ; test will fail because of autogensym
                          result__12833__auto__))))
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
                          [result__12833__auto__ {:val x}]
                          (def hey> result__12833__auto__) ; test will fail because of autogensym
                          result__12833__auto__))))))
