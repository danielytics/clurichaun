(ns clurichaun.entity-system
  (:require
    [clojure.string]
    [clojure.set]))

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

(defn get-all-entities
  [world]
  (:all-entities world))

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
        data        (merge (trait-ctor) default-data)] ;(into {} (map #(vector %1 %2) default-data)))]
    (-> world
        (assoc-in [:index-et->t entity-id trait] data)
        (update-in [:index-t->e trait] conj entity-id)
        (vector entity-id))))

(defn create-system
  [world system-name traits update-fn]
  (update world :systems assoc system-name [traits update-fn]))

(defn entities-with-traits
  [world traits]
  (reduce
     clojure.set/intersection
     (map #(get-in world [:index-t->e %]) traits)))

(defn get-traits
  [world entity]
  (get-in world [:index-et->t entity]))

(defn run-system
  [world system-name params]
  (let [[deps f]  (get-in world [:systems system-name])]
    (update
      world
      :index-et->t
      merge
      (into {}
        (for [entity (entities-with-traits world deps)]
          (let [traits (get-traits world entity)]
            (->> deps
                 (mapv traits)
                 (f params entity)
                 (merge traits)
                 (vector entity))))))))

