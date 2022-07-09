# snitch

[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.abhinav/snitch.svg)](https://clojars.org/org.clojars.abhinav/snitch)

Snitch is how I debug and understand data flowing through my system.
It's not a replacement for a full-fledged debugger,
but it's pretty close and will be useful in 90% (or some other made up number close to 100) of the cases. 

## Usage
There are four macros `defn*`, `defmethod*` `*fn`, `*let`.
They are drop-in replacements for `defn`, `defmethod`, `fn`, and `let`.

These macros creates inline defs of the parameters (args) passed to the functions,
and also inside the let bindings of the functions.
This makes it very "ergonomic" for repl-driven development.

## defn*

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


;; we can get the return value of foo by appending a < to foo

foo< 
=> nil


;; it roughly expands to a from that looks like this. 
;; the actual macro expansion is more complex.
(clojure.core/defn
   foo
   [a b]
   (def a a)
   (def b b)
   (clojure.core/let
    [result__12589__auto__ (do (+ a b) nil)]
    (def foo< result__12589__auto__)
    result__12589__auto__))
```

I'd recommend adding snitch to your `~/.lein/profiles.clj`.
An example file would be
```clojure
; profiles.clj
{:user {:dependencies [[org.clojars.abhinav/snitch "0.0.8"]]}}

{:dev {:dependencies [[org.clojars.abhinav/snitch "0.0.8"]]}}
```
