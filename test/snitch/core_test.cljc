(ns snitch.core-test
  #?(:clj
     (:require
      [snitch.test-utils :refer [contains-no-duplicate-inline-defs?]]
      [clojure.test :refer [deftest is testing]]
      [snitch.core :refer [concat-symbols define-args defn* define-let-bindings defmethod* *let *fn]])
     :cljs
     (:require
      [cljs.test :refer [deftest is testing run-tests]]))
  #?(:cljs
     (:require-macros
      [snitch.core]
      [snitch.test-utils :refer [contains-no-duplicate-inline-defs?]]
      [snitch.core-test :refer [let-to-fn]])))


(deftest test-behaviour-of-defn*
  (testing "Destructuring in parameters"
    (let [_ (defn* foo1 [{:keys [a/foo1-b1 foo1-c2]
                          foo1-dee3 :d
                          :as foo1-m4}
                         [foo1-x5 [foo1-y6 [foo1-z7]]]]
              [foo1-b1 foo1-c2 foo1-dee3 foo1-m4 foo1-x5 foo1-y6 foo1-z7])
          _ (foo1 {:a/foo1-b1 1 :foo1-c2 2 :d 3} [5 [6 [7]]])

          expected-b 1
          expected-c 2
          expected-d 3
          expected-x 5
          expected-y 6
          expected-z 7
          expected-m {:a/foo1-b1 1 :foo1-c2 2 :d 3}
          expected-output [1 2 3 {:a/foo1-b1 1 :foo1-c2 2 :d 3} 5 6 7]]
 ;;     b1, c2 etc are globally defined by the macro
  ;;    defn*
      (is (= expected-b foo1-b1))
      (is (= expected-c foo1-c2))
      (is (= expected-d foo1-dee3))
      (is (= expected-m foo1-m4))
      (is (= expected-x foo1-x5))
      (is (= expected-y foo1-y6))
      (is (= expected-z foo1-z7))
      (is (= expected-output foo1<))))

  (testing "funtion with docstrings"
    (let [_ (defn* foo13
              "this be a docstring"
              [a]
              (if-let [x a] x "a"))
          _ (foo13 1)]
      (is (= 1 x))))

  (testing "funtion with let-like binding forms"
    (let [_ (defn* foo13 [a]
              (if-let [x a] x "a"))
          _ (foo13 1)]
      (is (= 1 x)))

    (let [_ (defn* foo13 [a]
              (when-let [x a] x))
          _ (foo13 1)]
      (is (= 1 x)))

    (let [_ (defn* foo13 []
              (doseq [x (range 10)]
                x))
          _ (foo13)]
      (is (= 9 x))))

  (testing "lambdas inside defn"
    (let [_ (defn* foo-14 [a]
              ((fn [x] (inc x)) a))
          _ (foo-14 3)]
      (is (= 3 x))))


  (testing "Destructuring namespaced keywords with ns/keys syntax"
    (let [_ (defn* foo6 [{:a/keys [foo6-b1 foo6-c2]}]
              [foo6-b1 foo6-c2])
          _ (foo6 {:a/foo6-b1 1 :a/foo6-c2 2})
          expected-foo-6> '(foo6 #:a{:foo6-b1 1, :foo6-c2 2})
          expected-b 1
          expected-c 2]
      (is (= expected-b foo6-b1))
      (is (= expected-c foo6-c2))
      (is (= expected-foo-6> '(foo6 #:a{:foo6-b1 1, :foo6-c2 2})))))

  (testing "map destructuring and default args"
    (let [_ (defn* foo107 [{:keys [foo107-a] :as foo107-m  :or {foo107-a 1}}]
              foo107-a)
          _ (foo107 {:foo107-a 2})
          expected-foo107-a 2
          expected-foo107-m  {:foo107-a 2}
          expected-foo107>  '(foo107 {:foo107-a 2})]
      (is (= expected-foo107-a foo107-a))
      (is (= expected-foo107-m foo107-m))
      (is (= expected-foo107>  foo107>))))

  (testing "destructuring in let body"
    (let [_ (defn* foo2 [foo2-p1]
              (let  [{:keys [a/foo2-b1 foo2-c2]
                      foo2-dee3 :d
                      :as foo2-m4}
                     {:a/foo2-b1 1 :foo2-c2 2 :d 3}

                     [foo2-x5 [foo2-y6 [foo2-z7]]]
                     [5 [6 [7]]]]
                [foo2-p1 foo2-b1 foo2-c2 foo2-dee3 foo2-m4 foo2-x5 foo2-y6 foo2-z7]))
          _ (foo2 "pee")

          expected-b 1
          expected-c 2
          expected-d 3
          expected-x 5
          expected-y 6
          expected-z 7
          expected-m {:a/foo2-b1 1 :foo2-c2 2 :d 3}
          expected-p "pee"
          expected-output ["pee" 1 2 3 {:a/foo2-b1 1 :foo2-c2 2 :d 3} 5 6 7]]
      ;;     bb1, cc2 etc are globally defined by the macro
      ;;    defn*
      (is (= expected-b foo2-b1))
      (is (= expected-c foo2-c2))
      (is (= expected-d foo2-dee3))
      (is (= expected-m foo2-m4))
      (is (= expected-x foo2-x5))
      (is (= expected-y foo2-y6))
      (is (= expected-z foo2-z7))
      (is (= expected-p foo2-p1))
      (is (= expected-output foo2<))))

  ;;  just want to test that the function compiles
  ;;  and runs
  (testing "function with keyword arguments"
    (let [_ (defn* foo10 [a__ & {:keys [x]}] a__)
          _ (foo10 "ace")]
      (is (= foo10>  '(foo10 "ace" {:x nil})))
      (is (= foo10< "ace"))))

  (testing "reconstructing function call"
    (let [_ (defn* foo3 [foo3-1] foo3-1)
          _ (foo3 "ace")
          expected-foo3> '(foo3 "ace")]
      (is (= expected-foo3> foo3>) "simple case with no destructuring"))

    (let [_ (defn* foo4 [[foo4-1]] foo4-1)
          _ (foo4 [1])
          expected-foo4> '(foo4 [1])]
      (is (= expected-foo4> foo4>) "simple case with vector destructuring"))

    (let [_ (defn* foo5 [{:keys [foo4-1 foo4/a2] foo-4-three :foo4-3}] nil)
          _ (foo5 {:foo4-1 1
                   :foo4/a2 2
                   :foo4-3 3})
          expected-foo5> '(foo5 {:foo4-1 1, :foo4/a2 2, :foo4-3 3})] ;
      (is (= expected-foo5> foo5>) "Map destructuring"))

    (let [_ (defn* foo6 [a & more] [a more])
          _ (foo6 1 2 3 4 5)
          expected-foo6> '(clojure.core/apply foo6 1 [2 3 4 5])] ;
      (is (= expected-foo6> foo6>)
          "reconstructing a function with variadic args")))



  ;;   FIXME commenting out the history feature because it doesn't work in cljs yet.
  #_(testing "defn* stores history of the values.
            Calling var> returns the last 3 values."
      (let [_ (defn* foo3 [foo3-p]
                (let [foo3-a (inc foo3-p)]
                  #{foo3-p foo3-a}))
            _ (foo3 1)
            _ (foo3 2)
            _ (foo3 3)
            expected-foo3-p> '(3 2 1)
            expected-foo3-a> '(4 3 2)
            expected-foo3> {'foo3-p expected-foo3-p>
                            'foo3-a  expected-foo3-a>}]

        (is (= expected-foo3-p> foo3-p>))
        (is (= expected-foo3-a> foo3-a>))
        (is (= expected-foo3> foo3>))))
  ;;   FIXME commenting out the history feature because it doesn't work in cljs yet.
  #_(testing "defn* stores history of the values.
            Calling var>> with a number returns the last n values."
      (let [_ (defn* foo4 [foo4-p]
                foo4-p)
            _ (foo4 1)
            _ (foo4 2)
            _ (foo4 3)
            _ (foo4 4)
            _ (foo4 5)
            _ (foo4 6)
          ;; history is stored from most recent to least recent.
            all-foo4-p-values '(6 5 4 3 2 1)]
        (is (= (foo4-p>> 4)
               (take 4 all-foo4-p-values)))
        (is (= (foo4-p>> 5)
               (take 5 all-foo4-p-values)))
        (is (= (foo4-p>> 0)
               (take 0 all-foo4-p-values)))))
  ;;  FIXME commenting out the history feature because it doesn't work in cljs yet.
  #_(testing "calling fn-name! resets the atom"
      (let [_ (defn* foo5 [foo5-p]
                foo5-p)
            _ (foo5 1)
            _ (foo5 2)
            _ (foo5 3)
            _ (foo5 4)
            _ (foo5 5)
            _ (foo5 6)
            atom-value-before-reset @foo5_
            _ (foo5!) ; calling fn-name! to reset the atom
            atom-value-after-reset  @foo5_]

        (is (false? (empty? atom-value-before-reset)))
        (is (= atom-value-after-reset
               {})))))


;; FIXME: rename vars to follow convention.
(deftest test-behaviour-of-defmethod*
  (let [_ (defmulti foomethod1 (fn [a11 _]
                                 a11))
        - (defmethod* foomethod1 "a"
            [a11 {:keys [d11] :as b11}] b11)
        _ (foomethod1 "a" {:d11 :foomethod})]
    (is (= "a" a11))
    (is (= {:d11 :foomethod} b11))
    (is (= :foomethod d11)))

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
    (is (= 2 c11)))

  (let [_ (defmulti foomethod2 (fn [a11 _]
                                 a11))
        - (defmethod* foomethod2 "a" method-name
            [a11 {:keys [d11] :as b11}] b11)
        _ (foomethod2 "a" {:d11 :foomethod})]
    (is (= "a" a11))
    (is (= {:d11 :foomethod} b11))
    (is (= :foomethod d11))))

(deftest test-*let
  (let [_ (*let [a 1
                 [b c] [2 3]]
                [a b c])]
    (is (= 1 a))
    (is (= 2 b))
    (is (= 3 c)))

  (let [_ (*let [some-thing (or (get-in {:a {:b 1}} [:a :b])
                                (and true
                                     :c))])]
    (is (= some-thing 1))))

;; TODO: add more comprehensive tests
(deftest test-*fn
  (let [_ ((*fn [a b]
                [a b])
           1 2)]
    (is (= a 1))
    (is (= b 2)))
  (let [_ ((*fn [a b]
                ((fn [x y]
                   [x y]) a b))
           1 2)]
    (is (= a 1))
    (is (= b 2))
    (is (= x 1))
    (is (= y 2)))
  (let [_ ((*fn [a b]
                ((fn ([x] x)
                   ([x y] [x y])) a b))
           1 2)]
    (is (= a 1))
    (is (= b 2))
    (is (= x 1))
    (is (= y 2))))




(defmacro let-to-fn [bindings & body]
  (->> (partition 2 bindings)
       reverse
       (reduce (fn [acc [binding-sym val]]
                 (if (= acc body)
                   `((fn [~binding-sym] ~@acc) ~val)
                   `((fn [~binding-sym] ~acc) ~val)))
               body)))

(deftest custom-let-macros
  (let [_ (defn* fn-with-custom-let-macro [x]
            (let-to-fn [y x
                        z (inc x)]
                       (+ x z)))
        _ (fn-with-custom-let-macro 10)]
    (is (= y 10))
    (is (= z 11))))


;; commenting out because this fails
#_(deftest nested-lambda-functions-with-let-bindings-dont-have-duplicated-inline-defs
    (is (contains-no-duplicate-inline-defs? (defn* foo []
                                              (let [x 1]
                                                #_((fn [y]
                                                     (let [b 1]
                                                       ((fn [c]
                                                          b) 4))
                                                     y) 4)))))
    (is (true? (contains-no-duplicate-inline-defs? (defn* foo []
                                                     (let [x 1]
                                                       ((fn [y]
                                                          (let [b 1]
                                                            ((fn [c]
                                                               (let [x 1]
                                                                 ((fn [y]
                                                                    (let [b 1]
                                                                      ((fn [c]
                                                                         b) 4))
                                                                    y) 4))
                                                               b) 4))
                                                          y) 4)))))))



(comment
  (macroexpand-1 '(let-to-fn [a 1 b 2]
                             (+ a b))))






(comment
  (macroexpand-1 '(defn* foo [{:keys [a]}]
                    a))
  (foo 1)

  (macroexpand-1 '(defn* foo1 [{:keys [a/foo1-b1 foo1-c2]
                                foo1-dee3 :d
                                :as foo1-m4}
                               [foo1-x5 [foo1-y6 [foo1-z7]]]]
                    [foo1-b1 foo1-c2 foo1-dee3 foo1-m4 foo1-x5 foo1-y6 foo1-z7]))

  (defn* foo1> [{:keys [a/b1 c2]
                 dee3 :d3
                 :as m4}
                [x5 [y6 [z7]]]]
    [b1 c2 dee3 m4 x5 y6 z7])



  (defn ^:private unmap-vars-for-fn!
    "Will unmap vars that have been globally defined by defn*.
   This only works with the test functions, because the test functions
   follow the convention of fn-name-arg-name.
   for e.g
   (defn* bar [bar-arg1]
     (let [bar-arg2 2]
       [bar-arg1 bar-arg2]))

   `fn-name` is a symbol."
    [fn-name]
    (mapv (partial ns-unmap 'snitch.core-test)
          (filterv #(s/starts-with? (str %) (str fn-name))
                   (keys (ns-publics 'snitch.core-test)))))


  (filter #(clojure.string/starts-with? (str %) "foo3") (keys (ns-publics 'snitch.core-test)))
  (map #(ns-unmap 'snitch.core-test %) (keys (ns-publics 'snitch.core-test))))



