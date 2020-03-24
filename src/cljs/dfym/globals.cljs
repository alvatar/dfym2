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

(defonce window (atom {:width (aget js/window "innerWidth")
                       :height (aget js/window "innerHeight")}))
(defonce display-type (atom (width->display-type (:width @window))))
(defonce mouse (atom {:x 0 :y 0}))

(defonce _resize-display
  (. js/window addEventListener "resize"
     #(let [width (aget js/window "innerWidth")
            height (aget js/window "innerHeight")]
        (swap! window assoc :width width)
        (swap! window assoc :height height)
        (reset! window {:width width :height height})
        (reset! display-type (width->display-type width)))))

(defonce _mouse-position
  (. js/document addEventListener "mousemove"
     #(reset! mouse {:x (.-clientX %) :y (.-clientY %)})))
