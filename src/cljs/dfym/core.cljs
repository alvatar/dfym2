(ns dfym.core
  (:require
   [taoensso.encore :as encore :refer-macros [have have?]]
   [taoensso.timbre :as timbre :refer-macros [tracef debugf infof warnf errorf]]
   [cljs.core.async :as async :refer [<! >! put! take! chan]]
   [taoensso.sente :as sente :refer [cb-success?]]
   [taoensso.sente.packers.transit :as sente-transit]
   [oops.core :refer [oget oset!]]
   [rum.core :as rum]
   [garden.core :refer [css]]
   [garden.stylesheet :as stylesheet]
   [goog.style]
   [goog.string :as gstring]
   ;; -----
   [dfym.globals :as globals :refer [display-type]]
   [dfym.utils :as utils :refer [log*]]
   [dfym.client :as client]))

;; Convert JSX to Cljs
;; https://github.com/madvas/jsx-to-clojurescript

(goog-define ^:dynamic *is-dev* false)

(enable-console-print!)
(timbre/set-level! :debug)

;;
;; Actions
;;

(defn build-request [id & [handler]]
  (fn [data & [cb]]
    (client/chsk-send!
     [id data] 30000
     (fn [resp]
       (if-not (and (sente/cb-success? resp) (= :ok (:status resp)))
         (do (log* resp)
             (js/alert (gstring/format "Ups... Error in %s ¯\\_(ツ)_/¯ Restart the app, please..." id)))
         (do (when handler (handler resp))
             (when cb (cb resp))))))))

(defn logout []
  (oset! js/window "location.href" "/logout"))

(defn get-user [user]
  (build-request :user/get
                   (fn [args] (log* "RECEIVED" args))))

;;
;; Event Handlers
;;

(defmethod client/-event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (let [[old-state-map new-state-map] (have vector? ?data)]
    (when (:first-open? new-state-map)
      (log* "New state: " new-state-map))))

(defmethod client/-event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data uid]}]
  (js/console.log ev-msg)
  (let [[?user & _] ?data]
    (when-not ?user
      (js/alert "Internal Error: loading user")
      (logout))
    (reset! globals/user ?user)))

;;
;; UI Components
;;

(def styles
  (css
   (stylesheet/at-font-face {:font-family "VT323"
                             :src "url(\"/fonts/VT323-Regular.ttf\")"})
   [:body :h1 :h2 :h3 :h4 :h5 :h6 {:font-family "VT323"}]
   [:h1 {:font-size "24px"}]
   [:h2 {:font-size "16px"
         :margin "0 0 10px 0"}]
   [:h3 {:font-size "18px"
         :padding "5px"
         :margin "5px"}]
   [:.rfloat {:float "right"}]
   [:.lfloat {:float "left"}]
   [:.top-operations {:float "right"
                      :font-size "14px"
                      :font-weight "bold"
                      :margin "0 10px 0 0"
                      :cursor "pointer"}]
   [:.tag {:font-size "18px"
           :font-weight "normal"
           :padding "5px"
           :margin "5px 10px 5px 10px"
           :border-style "solid"
           :border-width "2px"
           :cursor "pointer"}]
   [:.file {:font-size "18px"
            :font-weight "normal"
            :padding "0px"
            :margin "5px 10px 15px 10px"
            :cursor "pointer"}]
   [:.dir {:font-weight "normal"}]
   [:.selected {:border-bottom "2px solid"
                :font-weight "bold"}]
   [:.scroll {:overflow "auto"}]
   [:.no-scroll {:overflow "hidden"}]
   [:.border {:border-style "solid"
              :border-width "2px"}]
   [:.panel {:overflow-y "scroll"
             :height "100%"}]
   [:.panel-bottom {:height "70px"}]
   [:#controls {:width "100%"
                :position "fixed"
                :bottom 0}]
   [:#menu-button {:float "left"
                   :width "40px"
                   :height "40px"
                   :background-color "white"
                   :border-top "solid 2px"
                   :border-bottom "solid 2px"
                   :font-size "30px"
                   :text-align "center"
                   :line-height "40px"
                   :cursor "pointer"}]))

(defonce style-node (atom nil))
(if @style-node
  (goog.style/setStyles @style-node styles)
  (reset! style-node (goog.style/installStyles styles)))

(rum/defc app < rum/reactive []
  [:div.no-scroll {:style {:width "100%" :height "100%"}}
   [:div {:style {:padding "20px"}}
    [:div.col-1-4 {:style {:padding "0 8px 0 20px"}}
     [:div.panel
      [:h2 "TAGS"]
      [:div
       [:.tag "[Untagged]"]
       [:.tag "Classical"]
       [:.tag "Ambient/Experimental"]
       [:.tag "Work"]
       [:.tag "Energetic"]
       [:.tag "Happy"]
       [:.tag "Death Metal"]
       [:.tag "Metal"]
       [:.tag "Black Metal"]
       [:.tag "Weird"]
       [:.tag "Mathematical"]
       [:.tag "Beautiful"]
       [:.tag "Complex"]
       [:.tag "Disgusting"]
       [:.tag "Should remove"]
       [:.tag "Apocalyptical"]
       [:.tag "Noise"]
       [:.tag "Sophisticated"]]
      [:div.panel-bottom]]]
    [:div.col-1-4 {:style {:padding "0 8px 0 0px"}}
     [:div.panel
      [:h2 "SELECTED TAGS"]
      [:div [:.tag "Mathematical"]]
      [:div.panel-bottom]]
     [:.div.panel-bottom]]
    [:div.col-2-4 {:style {:width "50%"}}
     [:div.panel
      [:h2 "FILTERED FILES"
       [:div.top-operations {:style {:font-weight "normal"}} "[Logged in as: " (:user-name (rum/react globals/user)) "]"]
       [:div.top-operations "Deselect_All"]
       [:div.top-operations "Select_All"]]
      [:div
       [:.file.dir "▨ [Yury Stravisnky] Songs of Komogorov"]
       [:.file.dir "▨ The biggest pain in the Ass [The Beatles]"]
       [:.file.dir.selected "■ MIDI shits I produced when I was on LSD"]
       [:.file.dir "▨ Weird sounds and forbidden music"]
       [:.file.dir "▨ [Yury Stravisnky] Songs of Komogorov"]
       [:.file.dir "▨ The biggest pain in the Ass [The Beatles]"]
       [:.file.dir "▨ The biggest pain in the Ass [The Beatles]"]
       [:.file.dir "▨ [Yury Stravisnky] Songs of Komogorov"]
       [:.file.dir "▨ The biggest pain in the Ass [The Beatles]"]
       [:.file.dir "▨ The biggest pain in the Ass [The Beatles]"]
       [:.file.dir "▨ [Yury Stravisnky] Songs of Komogorov"]
       [:.file.dir "▨ The biggest pain in the Ass [The Beatles]"]
       [:.file.dir "▨ The biggest pain in the Ass [The Beatles]"]
       [:.file.dir "▨ MIDI shits I produced when I was on LSD"]
       [:.file.dir "▨ Weird sounds and forbidden music"]
       [:.file.dir "▨ [Yury Stravisnky] Songs of Komogorov"]
       [:.file.dir "▨ MIDI shits I produced when I was on LSD"]
       [:.file "□ Weird sounds and forbidden music"]
       [:.file "□ Weird sounds and forbidden music"]
       [:.file.selected "■ Weird sounds and forbidden music"]
       [:.file.selected "■ [Yury Stravisnky] Songs of Komogorov"]
       [:.file.selected "■ Weird sounds and forbidden music"]
       [:.file "□ Weird sounds and forbidden music"]
       [:.file "□ Weird sounds and forbidden music"]
       [:.file "□ [Yury Stravisnky] Songs of Komogorov"]
       [:.file "□ Weird sounds and forbidden music"]
       [:.file "□ Weird sounds and forbidden music"]
       [:.file "□ [Yury Stravisnky] Songs of Komogorov"]
       [:.file "□ The biggest pain in the Ass [The Beatles]"]
       [:.file "□ MIDI shits I produced when I was on LSD"]
       [:.file "□ Weird sounds and forbidden music"]]
      [:.div.panel-bottom]]]]
   [:div#controls
    [:div#player
     [:audio {:controls "controls"}
      [:source {:src "https://www.dropbox.com/s/12fpcuwwmg8s7aj/02%20-%20Theme%20From%20Jack%20Johnson.mp3?raw=1"}]]]
    [:div#menu-button {:on-click logout} "⚙"]]])

(defn make-player [player-html-element]
  (let [player (js/MediaElementPlayer.
                player-html-element
                (clj->js {:stretching "responsive"
                          :preload "auto"
                          :features ["playpause" "current" "progress" "tracks" "duration"]}))]
    (.addEventListener player-html-element "ended" #(js/alert "NEXT"))))

;;
;; Init
;;

(rum/mount (app) (js/document.getElementById "app"))

(make-player (js/document.querySelector "audio[controls], video[controls]"))

(client/start-router!)
