(ns pixi-talk.helpers)

#?(:clj (defmacro <- [field value]
         `(let [v# ~value]
           (when-not (= ~field v#)
             (set! ~field v#)))))

#?(:cljs (def debug?  ^boolean goog.DEBUG))
