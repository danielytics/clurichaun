(ns example.core
 (:require
   [clurichaun.core :as clurichaun]))

(clurichaun/configure!
  {:asset-path  "assets/"
   :start-scren :intro

   :global-assets
      #{:Heart.png :Star.png :Tree_Short.png :Gem_Blue.png :Gem_Green.png}

   :sprites
      {:heart {:img :Gem_Green.png :x 0 :y 50 :h 80}
       :star  {:img :Gem_Blue.png :x 100 :y 250 :h 80}}

    :screen-leave
      {:intro   (fn [world next]
                  (println "Leaving intro and going to" next)
                  (println "World:" world)
                  world)}

    :screen-enter
      {:intro (fn [world previous]
                (let [world (clurichaun/default-intro-screen-enter world previous)]
                  (println "Entering intro from" previous)
                  (println "World:" world)
                  world))}})

