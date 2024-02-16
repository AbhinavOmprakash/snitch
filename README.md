# snitch

[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.abhinav/snitch.svg)](https://clojars.org/org.clojars.abhinav/snitch)

> Snitch is inline-defs on steroids


Snitch is how I debug and understand data flowing through my system.
It's not a replacement for a full-fledged debugger, but it's pretty close and will be useful in 90% of the cases. 
I use it as my sole debugger (in rare cases I reach for print statements). 

# Features 
- Support for Clojurescript. 
- Editor agnostic (use it along with cider, conjure, calva, or cursive!).
- Highly ergonomic for repl-driven development.

# What people have to say ❤️ 
> Very handy with those variants of the regular macros. Just add a `*` and you are inline def-ing the args! - Peter Strömberg (co-author of Calva)

# Talk
I gave a [talk](https://www.youtube.com/watch?v=WqilQulsJQc) about snitch at clojure Asia you can watch it 
here https://www.youtube.com/watch?v=WqilQulsJQc.

# Usage
## Installation
I recommend adding snitch to your `~/.lein/profiles.clj`.
An example file would be
```clojure
; profiles.clj
{:user {:dependencies [[org.clojars.abhinav/snitch "0.1.15"]]}}

{:dev {:dependencies [[org.clojars.abhinav/snitch "0.1.15"]]}}
```
If you add it to your project's dev dependencies, you could add this line to your dev/user.clj
```
(require '[snitch.core :refer [defn* defmethod* *fn *let]])
```
requiring these macros once, will intern these macros inside clojure.core & cljs.core, so you don't have to import them in every namespace.

### Exclude clojurescript dependency in clojure only projects 
If you are using snitch exclusively for your clojure project, you can exclude the clojurescript dependency from snitch (version 0.0.15 onwards). 

```clojure
{:user {:dependencies [[org.clojars.abhinav/snitch "0.1.15" :exclusions [org.clojure/clojurescript]]]}}
```

## Overview
There are four macros `defn*`, `defmethod*` `*fn`, `*let`.
They are drop-in replacements for `defn`, `defmethod`, `fn`, and `let`.

These macros creates inline defs of the parameters passed to the functions,
and also inside the let bindings of the functions.
This makes it very "ergonomic" for repl-driven development.

## defn*

defn* walks your clojure form and injects inline defs for all the bindings in the form.
This includes the arguments as well as bindings inside a let body, and any lambda function. 

```clojure
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

;; it roughly expands to a form that looks like this. 
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

A more complex example

```clojure
(require '[snitch.core :refer [defn*]])

(defn* foo1 [{:keys [a/b1 c2]
                dee3 :d3
                :as m4}
               [x5 [y6 [z7]]]]
    [b1 c2 dee3 m4 x5 y6 z7])

;; there is some crazy destructuring going on in the function parameters.    
;; snitch handles this with ease

(foo1 {:a/b1 1 :c2 2 :d3 3 :e 100 :f 200}
      [5 [6 [7]]]) 
;=> [1 2 3 {:a/b1 1, :c2 2, :d3 3} 5 6 7]

;; we can evaluate each var.

b1 ; 1
dee3 ; 3
m4 ; {:a/b1 1, :c2 2, :d3 3}
z7 ; 7

foo1< ; [1 2 3 {:a/b1 1, :c2 2, :d3 3} 5 6 7]

;; now for the coolest feature (IMO).
;; imagine a case where foo1 was called in some namespace ( you don't really know what was passed to it)
;; but there is a bug in foo1 that you want to fix
;; how do you reconstruct the function call?
;; we have the parameters defined, but you can't do this

(foo1 b1 dee3....)
;; because it needs to be passed a map and a vector. 
;; constructing the map is very painful and time consuming.
;; snitch provides functionality for that too.
;; just evaluate foo1> 

foo1> ; (foo1 {:a/b1 1, :c2 2, :d3 3} [5 [6 [7]]])

;; foo1> returns a list that can be evaluated 
;; notice that when snitch reconstructed the function call, 
;; it left out the keys :e and :f? 
;; the original map passed was {:a/b1 1 :c2 2 :d3 3 :e 100 :f 200}

;; snitch is smart that way and only constructs the arguments absolutely necessary 
;; for the function call. 
```

injecting inline defs inside let forms
```clojure
(defn* foobar []
  (let [a 1] a))

(foobar) ; 1
a ; 1

```

injecting inline defs inside lambda forms
```clojure
(defn* foobar [a]
  ((fn [b] b) a))

(foobar 4) ; 1
a ; 4
b ; 4
```

## *let 
*let will recursively inject inline defs for the all binding forms including any lambda forms.
```clojure
(*let [a 1]
      (let [b 2]   ; this isn't a *let but the top-level *let injects inline defs for this as well
        (let [c 3] ; and for this too!
          [a b c])))

a ; 1
b ; 2
c ; 3
```

## *fn 
Similar to `defn*`, will consider the name of the lambda function as `this` if not provided.

```clojure
((*fn [a]
      (let [b :b]
        [a b]))
 :a)  ; [:a :b]
a ; :a
b ; :b
this< ; [:a :b]
```

# Clojurescript support

Snitch works with clojurescript as well.

## shadow-cljs.edn
you can add the dependency to `~/.shadow-cljs/config.edn`

```clojure
{:dependencies 
 [[org.clojars.abhinav/snitch "0.1.15"]]}
```
The import for clojurescript looks different. 

```clojure
(:require [snitch.core :refer-macros [defn*]])
```

# Integrating snitch in your workflow.

I have two vim macros that inject `(require '[snitch.core :refer [defn* defmethod* *fn *let]])` at the top of my ns, and evaluate it.


```vimrc
"for clj
let @s = "m8gg0)o(require 'jkf'xi[snitch.core :refer [defn* defmethod* *fn *let]])jk;ee`8"

"for cljs
let @n = "m8gg0>I(:require [snitch.core :refer-macros [defn* defmethod* *fn *let]])jk;er`8"
```
the macro does the following things.
1. mark the current position.
2. go to the start of the file.
3. go to the end of the top-level s-exp (ns-form).
4. insert newline and add the require.
5. evaluate it using `;ee` (conjure's evaluation command)
6. go back to the marked position.

the macro is specific to my config, but the steps will give you an idea to create your own macros.

if you have created any specific macros/workflows for your editor of choice, you can open a PR and add it to the readme :)


# License 
Parts of the code in snitch is taken from clojure.core and cljs.core.

```
 *   Copyright (c) Rich Hickey, Abhinav Omprakash. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file license.md at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
```
