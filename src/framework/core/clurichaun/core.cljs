(ns clurichaun.core
 (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
 (:require
   [cljsjs.pixi]
   [cljsjs.nprogress]
   [clurichaun.helpers :refer [debug?] :refer-macros [<-]]
   [clurichaun.entity-system :refer [run-system entities-with-traits get-traits get-all-entities]]
   [cljs.core.async :as async]))

(when debug?
  (enable-console-print!))

(defn start []
  (println "before worker")
  (defonce worker (js/Worker. "js/bootstrap_worker.js"))
  (println "after worker"))

(def PI 3.14159265)
(def PI2 6.2831853)

(defn rotation->radians
  "Converts an angle in the range 0->1 (0 degrees to 360 degrees) to radians"
  [rotation]
  (* PI2 rotation))

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

(defn make-sprite
  [stage entity-id]
  (let [sprite (new js/PIXI.Sprite nil)]
    (set! (.-width sprite) 80)
    (set! (.-height sprite) 80)
    (set! (.-visible sprite) false)
    (.addChild stage sprite)
    (vector
      entity-id
      sprite)))

(defn setup-game
  [resource-base resources world]
  (let [new-resources (for [resource-alias (clojure.set/difference resources @loaded-resources)]
                        (str resource-base (name resource-alias)))
        game-looper   (fn []) ;(fn game-looper [] (game-loop) (js/requestAnimationFrame game-looper))
        init          (fn []
                        (js/NProgress.done)
                        (when debug? (println "Available resources" (clojure.string/join " " @loaded-resources)))
                        (swap! pixi assoc :world world :sprites (into {} (map (partial make-sprite (:stage @pixi)) (get-all-entities world))))
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
  (println "Configuring core")
  (reset! global-config config))

(defn select-changed
  "Determines which keys have changed and returns a map of only those kv pairs"
  [old-map new-map]
  (->> new-map
       (map (fn [[k v]] (when (not= v (get old-map k)) [k v])))
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
      (:world conf))

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
                    :advance        (let [new-world (advance-fsm world value)
                                          sprites   (select-changed (:sprites world) (:sprites new-world))]
                                      (swap! pixi update ::sprites make-sprites)
                                      new-world)
                    :update-world   (merge world (select-changed conf @global-config))
                    :run-timers     (reduce (fn [world timer] (timer world)) (dissoc world :screen-enter-map :screen-leave-map) value)
                    world))))))))))


(defn start2 []
  (when debug?
    (println "Development mode enabled."))
  #_
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
                  :world {}
                  :previous-time (js/Date.now)}))
  (init-engine))

(when debug?
 ;(def reload init-engine))
  (def reload #(println "reloaded")))


(defn game-loop
  []
  (let [current-time    (js/Date.now)
        previous-time   (get @pixi :previous-time)
        time-delta      (/ (- current-time previous-time) 1000.0)
        pixi-           (swap! pixi
                              (fn [p]
                                (let [timers (:timers p)
                                      expired-timers  (take-while #(< (first %) current-time) timers)]
                                  (assoc p
                                    :timers (drop (count expired-timers) timers)
                                    :previous-time current-time
                                    :expired-timers expired-timers))))
        {:keys [renderer stage sprites expired-timers]} pixi-
        world           (:world
                          (swap! pixi update :world (fn [world] (reduce #(run-system %1 %2 time-delta) world (keys (:systems world))))))
        resource-base   (:asset-path @global-config)
        visual-entities (entities-with-traits world [:Visual :Position])
        last-visual     (::cached-visual @pixi)]
    (swap! pixi assoc ::cached-visual visual-entities)

    ; Hide all invisible sprites
    (doseq [entity (clojure.set/difference (set last-visual) visual-entities)]
      (let [sprite (get sprites entity)]
        (set! (.-visible sprite) false)))
    (doseq [entity (clojure.set/difference visual-entities (set last-visual))]
      (let [sprite (get sprites entity)]
        (set! (.-visible sprite) true)))

    ; Show all visible sprites
    (doseq [entity visual-entities]
      (let [sprite  (get sprites entity)
            traits  (get-traits world entity)
            v       (:Visual traits)
            p       (:Position traits)
            image   (:image v)]
        (when image
          (set! (.-texture sprite)
           (.-texture (->> (:image v) name (str resource-base) (aget js/PIXI.loader.resources)))))
        (set! (.-x sprite) (:x p))
        (set! (.-y sprite) (:y p))
        (set! (.-visible sprite) true)))

    (when (seq expired-timers)
      (async/put! @command-channel [:run-timers (map second expired-timers)]))

    (.render renderer stage)))


(defn destroy-world-
  "Destroy and cleanup the world state"
  [world]
  world)

(defn create-world-
  "Create and initialise the world state"
  [world]
  world)

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
      destroy-world-
      ((get (:screen-enter-map state) next-screen ignore) current-screen)
      create-world-
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



