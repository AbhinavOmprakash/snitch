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
    (def foo> result__12589__auto__)
    result__12589__auto__))
```

not only does `defn*` inject inline defs, it stores the past values of 
each local variable. 
Here's an example

```clj
(require '[snitch.core :refer [defn*]])


(defn* foo [a b]
  (+ a b)
  nil)

;; call foo 4 times
(foo 1 2)
(foo 3 4)
(foo 5 6)
(foo 7 8)

;; if we eval a and b
a ; 7
b ; 8 

;; now try evaluating a> and b>

a> ; (7 5 3)
b> ; (8 6 4)

;; you get the last 3 values of a and b

;; if you want to look back further you can even do that.

(a>> 4) ; (7 5 3 1)
(b>> 4) ; (8 6 4 2)

;; note that the most recent value is the first element, and the least recent value is the 
;; last element in the list.

;; you can even configure the default number values shown when calling var>.
(alter-default-count! 5)
=> 5
;; now if you call a> you'll be shown 5 values by default

;; if you want to see all the past values of all the local variables you can call foo>

foo> 
; {a (7 5 3 1), 
;  b (8 6 4 2)}

;; it's a minor inconvenience, but you will have to clean up the garbage :(
;; the atom foo_ will not be cleared out on its own, and it will keep accumulating values.
;; you can reset the atom by calling 

(foo!)
=> {}
```
That's pretty much all the features that you need to know about.


# *fn

Anonymous functions can be given names, `(fn this [x] x)`
and one convention is calling it `this`. 
if you don't name your Anonymous fn then snitch calls it `this` by default

```clj
(map (*fn [x]
       (do (print x)
           x))
     [1 2 3 4])


;; eval x
x ; 4
;; evaling the history of x

x> ; (4 3 2)

this> ; {x (3 2 1 4)}


;; if you name your Anonymous fn.
(map (*fn print-and-return [x]
       (do (print x)
           x))
     [1 2 3 4])

print-and-return>  ; {x (3 2 1 4)}
```
temporarily naming your lambdas can help with debugging, especially if you
have a lot of lambdas 

# Some caveats.



