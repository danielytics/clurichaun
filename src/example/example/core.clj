(ns example.core)

(defmacro create-trait
 [world trait]
 `(assoc-trait ~world ~(keyword trait) ~trait))

