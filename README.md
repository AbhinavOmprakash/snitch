# snitch

[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.abhinav/snitch.svg)](https://clojars.org/org.clojars.abhinav/snitch)

> Snitch is inline-defs on steroids

Or:

> Highly enhanced ergonomics for repl-driven development.

Snitch is how I debug and understand data flowing through my system. I use it as my sole debugger (in rare cases I reach for print statements).

It's not a replacement for a full-fledged debugger, but it's pretty close and will be useful in 90% of the cases. However, unlike a debugger, Snitch runs while your application is running, which is both better and worse than a traditional debugger. Better because a debugger freezes your application while you inspect the state. Better because the Clojure REPL will let you modify the code you are debugging, while it is running. Most debuggers do not let you do that. Worse because Snitch will only show you the latest value from a series of invocations, whereas a debugger will let you step to the invocation you are interested in. (BTW. There are ways to collect state from all invocations, even if Snitch doesn't help with it.)

# Features
- `defn*`, `*let`, `*fn`, and `defmethod*` macros that inject inline `def`s for function arguments and let bindings.
- Gives access to the return value of called functions
- Lets you modify and then rerun the function with the snitched invokation arguments
- Support for Clojurescript.
- Editor agnostic (use it along with [CIDER](https://cider.mx), [Conjure](https://conjure.oli.me.uk/), [Calva](https://calva.io), [Cursive](https://cursive-ide.com/), or any REPL enhanced [Clojure editor](https://clojure.org/guides/editors!).

# What people have to say ❤️
> Very handy with those variants of the regular macros. Just add a `*` and you are inline def-ing the args! - Peter Strömberg (co-author of Calva)

# Talk
I gave a [talk](https://www.youtube.com/watch?v=WqilQulsJQc) about snitch at clojure Asia you can watch it
here https://www.youtube.com/watch?v=WqilQulsJQc.

# Usage
## Installation
I recommend adding snitch to your `~/.lein/profiles.clj` or `deps.edn`.
An example file would be
```clojure
; Leiningen profiles.clj
{:user {:dependencies [[org.clojars.abhinav/snitch "0.1.16"]]}}

{:dev {:dependencies [[org.clojars.abhinav/snitch "0.1.16"]]}}
```

Once your repl is running you can evaluate:

```clojure
(require '[snitch.core :refer [defn* defmethod* *fn *let]])
```

in the Clojure REPL and the macros will be interned inside clojure.core & cljs.core, so you don't have to import them in every namespace. It will also make the macros available for your ClojureScript namespaces if your ClojureScript REPL is cloned/spawned off of the Clojure REPL.

## Overview
There are four macros `defn*`, `defmethod*` `*fn`, `*let`.
They are drop-in replacements for `defn`, `defmethod`, `fn`, and `let`, respectively.

These macros create inline defs of the parameters passed to the functions,
and also inside the let bindings of the functions.
This makes it very ergonomic for repl-driven development.
Calling functions defined using the `defn*`, and `*fn` macros will also define symbols that:
1. Give access to the return value of the functions. `(defn* foo [x y z] ...)` will define `foo<` which holds the return value of calling `foo`.
2. Reconstructs the function call. Defining `(defn* foo [x y z] ...)` and then calling `(foo 1 2 3)` will let you reconstruct the call by evaluating `foo>`, you can then evaluate this reconstructed list to repeat the function call. This may not seem like a big deal, but then remember that `foo` may be called indirectly from a chain of function calls, so being able to update the `foo` function and re-call it with the last arguments it was called with is quite convenient.

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

;; And you can reconstruct the call to foo:

foo>
=> (foo 15 85)

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

Snitch works with clojurescript as well. As noted above, if your ClojureScript REPL is spawned/cloned off of the Clojure REPL, you can start your session by requiring the macros in the Clojure REPL, and this will make them available to ClojureScript. If you don't have easy access to the Clojure REPL, you need to include:

```clojure
(:require-macros [snitch.core :refer [defn* defmethod* *fn *let]])
```

in the `ns` form of a namespace.

(That said, usually there is some way to inject code in the Clojure REPL.)

# Integrating snitch in your workflow.

Using Clojure 1.12, you can avoid adding Snitch dependencies to your project and instead do it dynamically, by evaluating:

```clojure
;; Add the dependency dynamically
(require '[clojure.repl.deps :refer [add-libs]])
(add-libs '{org.clojars.abhinav/snitch {:mvn/version "0.1.16"}})
;; While at it, intern the Snitch macros in clojure.core
(require '[snitch.core :refer [defn* defmethod* *fn *let]])
```

## Vim/Neovim

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

## Calva
Here's a [demo](https://www.youtube.com/watch?v=jb1BcYpyOAs) showing how calva and snitch work together by the author of Calva [Peter Strömberg](https://github.com/PEZ) himself :)
Calva has several ways you can evaluate code in the Clojure REPL automatically when it has been connected. But you can also consider doing it a bit more on demand, for increased control, via a custom repl command snippet like so in your user `settings.json` file:

```jsonc
  "calva.customREPLCommandSnippets": [
    ...
    {
      "name": "qol: Add Snitch dependency",
      "repl": "clj",
      "snippet": "(require '[clojure.repl.deps :refer [add-libs]])\n\n(add-libs '{ org.clojars.abhinav/snitch {:mvn/version \"0.1.16\"}})\n\n(require '[snitch.core :refer [defn* defmethod* *fn *let]])"
    },
    ...
  ],
```

Then press <kbd>cmd+enter</kbd> to get a quick-pick menu which will have the **qol: Add Snitch dependency** option. (If you're not on a mac, use some other key, e.g. <kbd>ctrl+alt+s</kbd> <kbd>enter</kbd>.)

For instrumenting a function definition to use `defn*` instead of `defn` you can define a keyboard shortcut like so:

```jsonc
  {
    // Instrument as snitched defn*
    "key": "cmd+enter",
    "when": "editorLangId == 'clojure' && calva:connected",
    "command": "calva.runCustomREPLCommand",
    "args": {
      "snippet": "${top-level-form|replace|^\\(defn-?|(defn*}"
    }
  },
```

This will evaluate the funcion using `defn*` without editing the file. NB: If you are using some hot-reload tool like [shadow-cljs](https://github.com/thheller/shadow-cljs), saving the file will re-evaluate the function as it is written in the file, removing the snitching. This may or may not be what you want to happen. (Control that save-reflex! 😀)

You can also bind keys for checking the results of snitched functions and reconstruct the calls to snitched functions:

```jsonc
  {
    // Check snitched defn* result
    "key": "ctrl+alt+s r",
    "when": "editorLangId == 'clojure' && calva:connected",
    "command": "calva.runCustomREPLCommand",
    "args": {
      "snippet": "${top-level-defined-symbol|replace|$|<}"
    }
  },
  {
    // Reconstruct last call to snitched defn* to clipboard
    "key": "ctrl+alt+s c",
    "when": "editorLangId == 'clojure' && calva:connected",
    "command": "runCommands",
    "args": {
      "commands": [
        {
          "command": "calva.runCustomREPLCommand",
          "args": {
            "snippet": "${top-level-defined-symbol|replace|$|>}"
          }
        },
        "calva.copyLastResults"
      ]
    }
  },
```

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
