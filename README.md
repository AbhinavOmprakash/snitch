# snitch

[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.abhinav/snitch.svg)](https://clojars.org/org.clojars.abhinav/snitch)

Snitch is how I debug and understand data flowing through my system.
It's not a replacement for a full-fledged debugger,
but it's pretty close and will be useful in 90% (or some other made up number close to 100) of the cases. 

## Usage
There are two macros `defn*` and `defmethod*`.
They are drop-in replacements for `defn` and `defmethod`.

`defn*` and `defmethod*` creates inline defs of the parameters passed to the functions,
and also inside the let bindings of the functions.
This makes it very "ergonomic" for repl-driven development.

```clj
(require '[snitch.core :refer [defn*]])


(defn* foo [a b]
  (+ a b)
  nil)

;;  calling foo with random integers
(foo (rand-int 100) (rand-int 100)) ; nil


;; we can evaluate the value of a and b
a ; 15

b ; 85


;; optionally we can get the return value of foo like so

foo> ; nil


;; If you want to see the macroexpansion


(macroexpand-1  '(defn* foo [a b]
  (+ a b)
  nil))

; (clojure.core/defn
;  foo
;  [a b]
;  (def a a)
;  (def b b)
;  (clojure.core/let
;   [result__12589__auto__ (do (+ a b) nil)]
;   (def foo> result__12589__auto__)
;   result__12589__auto__))
```
