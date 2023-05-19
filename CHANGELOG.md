# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## Current
### Added 
- Recursively inject inline defs inside lambda functions.
- Insert inline defs inside lambda functions contained in methods and other functions
- Preserve metadata .

## [0.1.13] 2022-12-18
### Added
- Support for binding forms like if-let, when-let, and other binding forms.
- Intern the macros in clojure.core or cljs.core so the user only has to import it once and it will be available in every namespace.
### Fixed
- Fix cljs support for certain binding forms.
- Support variadic arg functions when reconstructing function calls.
- When reconstructing a function call, don't evaluate the function, because it results in a "function object" that can't be read back. Preserves the symbol instead.

## [0.0.12] - 2022-08-20 
### Fixed
- fix reconstruction of nested maps

## [0.0.11] - 2022-08-20 
### Fixed
- fix handling of funtions with keyword arguments.
- fix defmethod* .
- fix handling of if-let and when-let for clj only (cljs has to be figured out)
- fix handle destructuring of namespaced keywords when reconstructing funtion.


## [0.0.10] - 2022-07-21
- fix handling of `:or` in funtion definiton 

## [0.0.9] - 2022-07-15
### Added
- add ability to reconstruct a function call.
### Fixed 
- fix destructuring issues in cljs
### Changed
- Change license to EPL-2.0

## [0.0.8] - 2022-07-09
### Added
- snitch is now cljc, and works with clojurescript as well.

### Fixed
- Fixed destructuring 

### Changed
- The var name for getting the result of the function.
If a function `foo` is defined with `defn*` then the result of the function call can be gotten by evaluating `foo<`.
Earlier this was `foo>`
