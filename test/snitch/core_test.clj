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
    (is (expansion-valid? (arg->def-args "" 'a) (def a a)))
    (is (expansion-valid? (arg->def-args "*" 'a) (def a* a)))
    (is (expansion-valid? (arg->def-args  "fname" "*" 'a) (def fname-a* a))))


  (testing "Vector Destructuring"
    (is (expansion-valid? (arg->def-args "" ['a 'b])
                          ((def a a) (def b b))))

    (is (expansion-valid? (arg->def-args "*" ['a 'b])
                          ((def a* a) (def b* b)))))

  (testing "Map Destructuring"
    (is (expansion-valid? (arg->def-args "" {:keys ['a 'b]})
                          ((def a a) (def b b))))
    (is (expansion-valid? (arg->def-args "*" {:keys ['a 'b]})
                          ((def a* a) (def b* b))))

    (is (expansion-valid? (arg->def-args "" {:keys ['a 'b] :as 'c})
                          ((def a a) (def b b) (def c c))))
    (is (expansion-valid? (arg->def-args "*" {:keys ['a 'b] :as 'c})
                          ((def a* a) (def b* b) (def c* c))))

    (is (expansion-valid? (arg->def-args "" {'idx 'index :keys ['a 'b]})
                          ((def idx idx) (def a a) (def b b))))
    (is (expansion-valid? (arg->def-args "*" {'idx 'index :keys ['a 'b]})
                          ((def idx* idx) (def a* a) (def b* b))))))


(deftest test-define-args
  (testing "Params list with simple symbols"
    (is (expansion-valid? (define-args ['a 'b])
                          ((def a a) (def b b))))
    (is (expansion-valid? (define-args "*" ['a 'b])
                          ((def a* a) (def b* b)))))

  (testing "Params list with vector destructuring"
    (is (expansion-valid? (define-args ['a ['b 'c]])
                          ((def a a) (def b b) (def c c))))
    (is (expansion-valid? (define-args "*" ['a ['b 'c]])
                          ((def a* a) (def b* b) (def c* c)))))

  (testing "Params list with map destructuring"
    (is (expansion-valid? (define-args ['x {:keys ['a 'b] :as 'c}])
                          ((def x x) (def a a) (def b b) (def c c))))
    (is (expansion-valid? (define-args "*" ['x {:keys ['a 'b] :as 'c}])
                          ((def x* x) (def a* a) (def b* b) (def c* c)))))

  (testing "Params list with simple symbols, vector and map destructuring"
    (is (expansion-valid? (define-args ['x ['j 'k] {:keys ['a 'b] :as 'c}])
                          ((def x x) (def j j) (def k k) (def a a) (def b b) (def c c))))
    (is (expansion-valid? (define-args "*" ['x ['j 'k] {:keys ['a 'b] :as 'c}])
                          ((def x* x) (def j* j) (def k* k) (def a* a) (def b* b) (def c* c))))))


(deftest test-define-let-bindings
  (testing "body with let form as first form"
    (is (expansion-valid? (define-let-bindings '(let [a 1 b 2] a))
                          (let [a 1 b 2] (def a a) (def b b) a)))
    (is (expansion-valid? (define-let-bindings '(let [a 1 b 2] (print a)))
                          (let [a 1 b 2] (def a a) (def b b) (print a)))))

  (testing "body with let form not as first form"
    (is (expansion-valid? (define-let-bindings '((print "x")
                                                 (let [a 1]
                                                   a)))
                          ((print "x") (let [a 1] (def a a) a))))
    (is (expansion-valid? (define-let-bindings '((print "x")
                                                 (let [a 1]
                                                   (print a))))
                          ((print "x") (let [a 1] (def a a) (print a))))))

  (testing "body with nested let forms"
    (is (expansion-valid? (define-let-bindings '(let [a 1
                                                      b 2]
                                                  (print a)
                                                  (let [x 3
                                                        y 4]
                                                    x)))
                          (let [a 1 b 2]
                            (def a a)
                            (def b b)
                            (print a)
                            (let [x 3 y 4]
                              (def x x)
                              (def y y)
                              x))))
    (is (expansion-valid? (define-let-bindings '(let [a 1]
                                                  (print a)
                                                  (let [x 2]
                                                    (str x)
                                                    (let [i 3]
                                                      i))))
                          (let [a 1]
                            (def a a)
                            (print a)
                            (let [x 2]
                              (def x x)
                              (str x)
                              (let [i 3]
                                (def i i)
                                i))))))

  (testing "body with multiple non-nested lets"
    (is (expansion-valid? (define-let-bindings '(do (let [a 1] (print a))
                                                    (let [b 2] (str b))
                                                    (let [c 3] (symbol c))))

                          (do
                            (let [a 1] (def a a) (print a))
                            (let [b 2] (def b b) (str b))
                            (let [c 3] (def c c) (symbol c))))))
  (testing "let with suffixes"
    (is (expansion-valid? (define-let-bindings "-" '(let [a 1] (print a)))
                          (let [a 1] (def a- a) (print a))))
    (is (expansion-valid? (define-let-bindings "-" '(let [a 1] (print a) (let [b 2] b)))
                          (let [a 1] (def a- a) (print a) (let [b 2] (def b-- b) b)))
        "each nested let should have an additional suffix to it. 
                            a is in level one so it is a-
                            b is in level 2 so it is b--")
    (is (expansion-valid? (define-let-bindings "-" '(let [a 1]
                                                      (print a)
                                                      (let [b 2]
                                                        (str b)
                                                        (let [b 2] b))))
                          (let
                            [a 1]
                            (def a- a)
                            (print a)
                            (let [b 2]
                              (def b-- b)
                              (str b)
                              (let [b 2]
                                (def b--- b)
                                b)))))
    (is (expansion-valid? (define-let-bindings "-" '(do (let [a 1] (print a))
                                                        (let [b 2] (str b))
                                                        (let [c 3] (symbol c))))

                          (do
                            (let [a 1] (def a- a) (print a))
                            (let [b 2] (def b- b) (str b))
                            (let [c 3] (def c- c) (symbol c))))
        "This asserts that when let has the same level (non nested) then they should have the same suffix."))







  (testing "let with prefixes and suffixes"
    (is (expansion-valid? (define-let-bindings "fname" "-" '(let [a 1] (print a)))
                          (let [a 1] (def fname-a- a) (print a))))
    (is (expansion-valid? (define-let-bindings "fname" "-" '(let [a 1] (print a) (let [b 2] b)))
                          (let [a 1] (def fname-a- a) (print a) (let [b 2] (def fname-b-- b) b)))
        "each nested let should have an additional suffix to it, but all should have only one suffix
                            a is in level one so it is fname-a-
                            b is in level 2 so it is  fname-b--")
    (is (expansion-valid? (define-let-bindings "fname"  "-" '(do (let [a 1] (print a))
                                                                 (let [b 2] (str b))
                                                                 (let [c 3] (symbol c))))

                          (do
                            (let [a 1] (def fname-a- a) (print a))
                            (let [b 2] (def fname-b- b) (str b))
                            (let [c 3] (def fname-c- c) (symbol c)))))))


(deftest test-defn*
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
                      (def x x) 
                      (let [y 2] 
                        (def y y) 
                        (print x))))))


;; test with variadic defns
