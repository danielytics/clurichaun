(ns example.core
  (:require-macros [example.core :refer [create-trait]])
  (:require
    [clurichaun.core :as clurichaun]
    [clurichaun.entity-system :as clurichaun-es :refer [create-world create-system run-system create-entity add-trait]]))

(def assoc-trait clurichaun-es/assoc-trait)

(defrecord Position [x y z])
(defrecord Movement [x y])
(defrecord Visual [image])
(defrecord SimpleAnimated [frames speed remaining current-frame])


(println "Configuring example")
(clurichaun/configure!
  {:asset-path  "assets/"
   :start-screen :intro

   :global-assets
      #{:Heart.png :Star.png :Tree_Short.png :Gem_Blue.png :Gem_Green.png}

   :world
      (-> (create-world)
          (create-trait Position)
          (create-trait Movement)
          (create-trait Visual)
          (create-trait SimpleAnimated)

          (create-system :mover
            [:Position :Movement]
            (fn [time-delta id [p m]]
              {:Position
                (Position.
                  (+ (:x p) (* time-delta (:x m)))
                  (+ (:y p) (* time-delta (:y m)))
                  (:z p))}))

          (create-system :animator
            [:SimpleAnimated :Visual]
            (fn [time-delta id [a v]]
              (let [remaining (- (:remaining a) time-delta)
                    new-frame (mod (inc (:current-frame a))
                                   (count (:frames a)))]
                (if (<= remaining 0)
                  {:SimpleAnimated
                    (SimpleAnimated.
                      (:frames a)
                      (:speed a)
                      (:speed a)
                      new-frame)
                   :Visual
                    (Visual.
                      (get (:frames a) new-frame (:image v)))}
                  {:SimpleAnimated
                    (SimpleAnimated.
                     (:frames a)
                     (:speed a)
                     remaining
                     (:current-frame a))}))))

          (create-entity)
          (add-trait :Position {:x 0 :y 0 :z 0})
          (add-trait :Visual {:image :Star.png})
          first

          (create-entity)
          (add-trait :Position {:x 0 :y 0 :z 0})
          (add-trait :Movement {:x 1 :y 0})
          first

          (create-entity)
          (add-trait :Position {:x 0 :y 0 :z 0})
          (add-trait :Movement {:x 15 :y 40})
          (add-trait :Visual {})
          (add-trait :SimpleAnimated {:speed 0.16
                                      :remaining 0
                                      :current-frame -1
                                      :frames [:Gem_Green.png :Gem_Blue.png]})
          first)

   :screen-enter
      {:intro (fn [world previous]
               world)}

   :screen-leave
      {:intro   (fn [world next]
                  (println "Leaving intro and going to" next)
                  world)}})


