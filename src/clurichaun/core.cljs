(ns clurichaun.core
  (:require
    [pixi-talk.helpers :refer [debug?] :refer-macros [<-]]
    [cljsjs.pixi]))

(defonce loaded-resources (atom #{}))
(defonce pixi (atom nil))

(declare game-loop)

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
                        (when debug? (println "Available resources" (clojure.string/join " " @loaded-resources)))
                        (when-not @pixi
                          (let [renderer (js/PIXI.autoDetectRenderer 800 800 #js {:antiAlias false 
                                                                                  :transparent false
                                                                                  :resolution 1})
                                stage    (new js/PIXI.Container)]
                            (.appendChild (.getElementById js/document "game") (.-view renderer))
                            (reset! pixi {:renderer renderer
                                          :stage stage
                                          :running false
                                          :sprites {}})))

                        (when-not (and debug? (= sprites (::sprites-data @pixi)))
                          (when debug?
                            (swap! pixi assoc ::sprites-data sprites))
                          (swap! pixi update-in [:sprites] #(make-sprites resource-base (:stage @pixi) sprites %)))
                        (swap! pixi update-in [:running] #(or % (do (game-looper) true))))]

    (reset! loaded-resources resources)

    (if (seq new-resources)
      (doto js/PIXI.loader
        (.add (clj->js new-resources))
        (.on "progress" (fn [loader res]
                          (when debug? (println "Loading" (str (.-progress loader) "%")))))
        (.load (comp init #(when debug? (println "Loaded" (clojure.string/join " " new-resources))))))
      (init))))

(defn main []
  (when debug?
    (enable-console-print!)
    (println "Development mode enabled."))

  (setup-game
    "assets/"
    #{:Heart.png :Star.png :Tree_Short.png :Gem_Blue.png :Gem_Green.png}
    {:heart {:img :Heart.png :x 0 :y 50 :h 80}
     :star  {:img :Star.png :x 100 :y 250 :h 80}}))

(defn game-loop
  []
  (let [{:keys [renderer stage sprites]} @pixi
        sprites (vals sprites)]
    (doseq [sprite sprites]
      (set! (.-x sprite) (if (< (.-x sprite) 850) (+ 25 (.-x sprite)) -50)))
    (.render renderer stage)))


