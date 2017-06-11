(ns clurichaun.core
 (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
 (:require
   [clurichaun.entity-system :as clurichaun :refer [create-world create-system run-system create-entity add-trait]]
   [cljs.core.async :as async]))

(enable-console-print!)

(defonce event-channel (atom (async/chan)))
(defonce global-config (atom nil))

(defn do-tick
  [time-delta]
  ; time-delta should always be 0.033 as a tick is locked at 30 FPS
  (swap! global-config
         update
         :world
         (fn [world]
           (reduce
             #(run-system %1 %2 time-delta)
             world
             (keys (:systems world))))))

(defn configure!
  [config]
  (let [channel (swap! event-channel (fn [ch]
                                       (async/close! ch) 
                                       (async/chan)))]
    (reset! global-config config)
    (go-loop [prev-time (.now js/Date)]
      (let [[value ch] (async/alts! [channel (async/timeout 1)])]
        (if-not (and (= ch channel)
                     (nil? value))
          (let [current-time (.now js/Date)
                time-delta (- current-time prev-time)]
            (if (<= time-delta 32)
              (recur prev-time)
              (do
                (do-tick (* time-delta 0.001))
                (recur current-time))))))))

  (println "Configured worker"))


