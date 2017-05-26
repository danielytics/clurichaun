(ns clurichaun.core
  (:require-macros
    [cljs.core.async.macros :refer [go go-loop]])
  (:require
    [cljsjs.pixi]
    [cljsjs.nprogress]
    [clurichaun.helpers :refer [debug?] :refer-macros [<-]]
    [cljs.core.async :as async]))

(when debug?
  (enable-console-print!))

(defonce loaded-resources (atom #{}))
(defonce pixi (atom nil))

(defn install-timer!
  [timeout timer]
  (swap!
    pixi
    (fn [pixi]
      (update
        pixi
        :timers
        (comp (partial sort-by first) conj)
        [(+ (:previous-time pixi) timeout) timer]))))

(declare game-loop)
(defonce command-channel (atom nil))

(defn action!
  [action]
  (async/put! @command-channel [:advance action]))

(defn make-sprites
  [resource-base stage sprites old-sprites]
  (into {}
    (for [[id {:keys [img x y w h]}] sprites]
      (let [sprite (or (get old-sprites id) (new js/PIXI.Sprite nil))
            texture (.-texture (->> img name (str resource-base) (aget js/PIXI.loader.resources)))]
        (<- (.-texture sprite) texture)
        (<- (.-x sprite) x)
        (<- (.-y sprite) y)
        (<- (.-width sprite) (or w 50))
        (<- (.-height sprite) (or h 50))
        (.addChild stage sprite)
        [id sprite]))))

(defn setup-game
  [resource-base resources sprites]
  (let [new-resources (for [resource-alias (clojure.set/difference resources @loaded-resources)]
                        (str resource-base (name resource-alias)))
        game-looper   (fn game-looper [] (game-loop) (js/requestAnimationFrame game-looper))
        init          (fn []
                        (js/NProgress.done)
                        (when debug? (println "Available resources" (clojure.string/join " " @loaded-resources)))
                        (when-not (and debug? (= sprites (::sprites-data @pixi)))
                          (when debug?
                            (swap! pixi assoc ::sprites-data sprites))
                          (swap! pixi update-in [:sprites] #(make-sprites resource-base (:stage @pixi) sprites %)))
                        (when-not (:running @pixi)
                          (action! :init))
                        (swap! pixi update-in [:running] #(or % (do (js/requestAnimationFrame game-looper) true))))]

    (reset! loaded-resources resources)

    (if (seq new-resources)
      (doto js/PIXI.loader
        (.add (clj->js new-resources))
        (.on "progress" (fn [loader res]
                          (when debug? (println "Loading" (.-url res)))
                          (js/NProgress.set (/ (.-progress loader) 100))))
        (.load (comp init #(when debug? (println "Loaded" (clojure.string/join " " new-resources))))))
      (init))))

(defonce global-config (atom {}))
(declare advance-fsm)

(defn configure!
  "Set the globally accessible configuration"
  [config]
  (reset! global-config config))

(defn select-changed
  "Determines which keys have changed and returns a map of only those kv pairs"
  [old-map new-map]
  (->> new-map
       (map (fn [old [k v]] (when (not= v (get old k)) [k v])))
       (filter identity)
       (into {})))

(defn proxy-world
  [f & args]
  (fn [world & _]
    (apply f args)
    world))

(def default-intro-screen-enter
  (proxy-world
    (fn []
      (println "Initialising screens")
      (install-timer! 5000 (proxy-world action! :skip)))))

(defn init-engine []
  (js/NProgress.start)
  (let [conf @global-config]
    (setup-game
      (:asset-path conf)
      (:global-assets conf)
      (:sprites conf))

    (if-let [commands @command-channel]
      (async/put! commands [:update-world nil])
      (let [commands (async/chan)]
        (reset! command-channel commands)
        (go-loop [old-world {:screen-enter-map (merge {:intro default-intro-screen-enter}
                                                  (:screen-enter conf))
                             :screen-leave-map (:screen-leave conf)
                             :current :init
                             :prev nil}]
          (when-let [[command value] (async/<! commands)]
            (let [pixi-state @pixi
                  world (dissoc
                          (assoc old-world :time (:previous-time pixi-state)))]
              (recur
                (merge
                  old-world
                  (condp = command
                    :advance        (advance-fsm world value)
                    :update-world   (merge world (select-changed conf @global-config))
                    :run-timers     (reduce (fn [world timer] (timer world)) (dissoc world :screen-enter-map :screen-leave-map) value)
                    world))))))))))


(defn start []
  (when debug?
    (println "Development mode enabled."))
  (let [renderer (js/PIXI.autoDetectRenderer 800 800 #js {:antiAlias false 
                                                          :transparent false
                                                          :resolution 1})
        stage    (new js/PIXI.Container)]
    (.appendChild (.getElementById js/document "clurichaun") (.-view renderer))
    (reset! pixi {:renderer renderer
                  :stage stage
                  :running false
                  :sprites {}
                  :timers []
                  :previous-time (js/Date.now)}))
  (init-engine))

(when debug?
 (def reload init-engine))


(defn game-loop
  []
  (let [current-time    (js/Date.now)
        pixi            (swap! pixi
                              (fn [p]
                                (let [timers (:timers p)
                                      expired-timers  (take-while #(< (first %) current-time) timers)]
                                  (assoc p
                                    :timers (drop (count expired-timers) timers)
                                    :previous-time current-time
                                    :expired-timers expired-timers))))
        {:keys [renderer stage sprites previous-time expired-timers]} pixi
        time-delta      (- current-time previous-time)
        sprites         (vals sprites)]
    (when (seq expired-timers)
      (async/put! @command-channel [:run-timers (map second expired-timers)]))

    (doseq [sprite sprites]
      (set! (.-x sprite) (if (< (.-x sprite) 850) (+ 25 (.-x sprite)) -50)))
    (.render renderer stage)))

; Define the global screen state chart
(defonce screen-state-chart
  {[nil :init]        {:init :intro}
   [:init :intro]     {:skip :title}
   [:intro :title]    {:play :game
                       :options :options}
   [:paused :title]   {:play :game
                       :options :options}
   [:title :game]     {:pause :paused}
   [:paused :game]    {:pause :paused}
   [:game :paused]    {:resume :game
                       :options :options
                       :quit :title}
   [:paused :options] {:back :game}
   [:title :options]  {:back :title}})

(defn advance-fsm
 "Advance the main screen finite state machine by one transition"
 [state action]
 (let [world          (dissoc state :current :prev :screen-leave-map :screen-enter-map)
       current-screen (:current state)
       prev-screen    (:prev state)
       next-screen    (get-in screen-state-chart [[prev-screen current-screen] action])
       ignore         (fn [w s] w)]
  (println "Advancing state from" current-screen "to" next-screen)
  (-> world
      ((get (:screen-leave-map state) current-screen ignore) next-screen)
      ((get (:screen-enter-map state) next-screen ignore) current-screen)
      (assoc :current next-screen :prev current-screen)
      (->> (merge state)))))

(defn valid-actions
  "Get a list of valid actions from the current screen"
  [state]
 (let [world          (dissoc state :current :prev :screen-leave-map :screen-enter-map)
       current-screen (:current state)
       prev-screen    (:prev state)
       transitions    (get screen-state-chart [prev-screen current-screen])]
  (set (keys transitions))))





