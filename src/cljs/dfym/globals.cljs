(ns dfym.globals)

(goog-define ^:dynamic *server-ip* "127.0.0.1")
(goog-define ^:dynamic *env* "dev")
(goog-define ^:dynamic *enable-mobile-dev* true)

;;
;; UI
;;

(defn width->display-type [width]
  (cond (<= width 568) :xs
        (<= width 768) :sm
        (<= width 1024) :md
        (<= width 1280) :lg
        :else :xl))

(def window (atom {:width (aget js/window "innerWidth")
                   :height (aget js/window "innerHeight")}))

(def display-type (atom (width->display-type (:width @window))))

(def mouse (atom {:x 0 :y 0}))

(defn init! []
  ;; Window resize listener
  (. js/window addEventListener "resize"
     #(let [width (aget js/window "innerWidth")
            height (aget js/window "innerHeight")]
        (reset! window {:width width
                        :height height})
        (reset! display-type (width->display-type width))))
  ;; Mouse move listener for updating its position
  (. js/document addEventListener "mousemove"
     #(reset! mouse {:x (.-clientX %)
                     :y (.-clientY %)})))
