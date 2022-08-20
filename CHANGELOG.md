# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [0.0.11] - 2022-08-20 
### Fixed
- fix handling of funtions with keyword arguments.
- fix defmethod* .
- fix handling of if-let and when-let.
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
