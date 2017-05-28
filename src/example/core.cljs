(ns example.core
  (:require-macros [example.core :refer [create-trait]])
  (:require
    [clurichaun.core :as clurichaun]))

(defrecord ECSWorld [traits systems index-et->t index-t->e all-entities next-entity-id])

(defn create-world
  "Create a new ECS world"
  []
  (map->ECSWorld
    {:traits {}
     :systems {}
     :index-et->t {}
     :index-t->e {}
     :all-entities #{}
     :next-entity-id 0}))

(defn assoc-trait
  [world trait-name record]
  (-> world
      (update :traits assoc trait-name #(new record))
      (update :index-t->e assoc trait-name #{})))

(defn create-entity
  "Create a new entity by returning a pair of its id and the world"
  [world]
  (let [entity-id (:next-entity-id world)]
    (vector (-> world
                (update :next-entity-id inc)
                (update :all-entities conj entity-id))
            entity-id)))

(defn add-trait
  [[world entity-id] trait default-data]
  (let [trait-ctor  (get-in world [:traits trait])
        data        (merge (trait-ctor) default-data)]
    (-> world
        (assoc-in [:index-et->t entity-id trait] data)
        (update-in [:index-t->e trait] conj entity-id)
        (vector entity-id))))

(defn create-system
  [world system-name traits update-fn]
  (update world :systems assoc system-name [traits update-fn]))

(defn run-system
  [world system-name params]
  (let [[deps f]  (get-in world [:systems system-name])
        entities  (apply
                    clojure.set/intersection
                    (map #(get-in world [:index-t->e %]) deps))]
    (update
      world
      :index-et->t
      merge
      (into {}
        (for [entity entities]
          (let [traits (get-in world [:index-et->t entity])]
            (->> deps
                 (mapv traits)
                 (f params entity)
                 (merge traits)
                 (vector entity))))))))

(defrecord Position [x y z])
(defrecord Movement [x y])
(defrecord Visual [image])
(defrecord SimpleAnimated [frames speed current-frame])

(-> (create-world)
    (create-trait Position)
    (create-trait Movement)
    (create-trait Visual)
    (create-trait SimpleAnimated)

    (create-system :mover
      [:Position :Movement]
      (fn [params id [p m]]
        {:Position
          (Position.
            (+ (:x p) (:x m))
            (+ (:y p) (:y m))
            (:z p))}))

    (create-system :animator
      [:SimpleAnimated :Visual]
      (fn [params id [a v]]
        (let [new-frame (mod (inc (:current-frame a)) 
                             (count (:frames a)))]
          {:SimpleAnimated
            (SimpleAnimated.
              (:frames a)
              (:speed a)
              new-frame)
           :Visual
            (Visual.
              (get (:frames a) new-frame (:image v)))})))

    (create-system :render
      [:Visual]
      (fn [params id [v]]
        (println "Rendering entity" id "image:" (:image v))
        {}))

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
    (add-trait :Movement {:x 0 :y 1})
    (add-trait :Visual {})
    (add-trait :SimpleAnimated {:speed 300
                                :current-frame -1
                                :frames [:Gem_Green.png :Gem_Blue.png]})
    first

    (run-system :mover {})
    (run-system :animator {})
    (run-system :render {})

    (run-system :mover {})
    (run-system :animator {})
    (run-system :render {})

    (run-system :mover {})
    (run-system :animator {})
    (run-system :render {})

    :index-et->t
    println)

(clurichaun/configure!
  {:asset-path  "assets/"
   :start-screen :intro

   :global-assets
      #{:Heart.png :Star.png :Tree_Short.png :Gem_Blue.png :Gem_Green.png}

    :screen-enter
      {:intro (fn [world previous]
                {:surfaces
                  {:background
                    {:x 0 :y 0 :z 0
                     :w 100 :h 100
                     :img :Star.png
                     :timeline
                      [{:start 1000 :duration 3000
                        :x 200 :y 0 :z 0
                        :scale 3.0
                        :rotation 0.5
                        :easing :cubic}]}}})}

    :screen-leave
      {:intro   (fn [world next]
                  (println "Leaving intro and going to" next)
                  world)}})


