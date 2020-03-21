(ns dfym.ui
  (:require
   [rum.core :as rum]
   [garden.core :refer [css]]
   [garden.stylesheet :as stylesheet]
   [goog.style]
   ;; -----
   [dfym.db :as db]
   [dfym.actions :as actions]))

;; Convert JSX to Cljs
;; https://github.com/madvas/jsx-to-clojurescript

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

(rum/defc app [db]
  (let [user-name (:user-name (db/get-system-attr db :user))]
    [:div.no-scroll {:style {:width "100%" :height "100%"}}
     [:div {:style {:padding "20px"}}
      [:div.col-1-4 {:style {:padding "0 8px 0 20px"}}
       [:div.panel
        [:h2 "TAGS"]
        [:div
         (for [[id tag] (db/get-tags db)]
           [:.tag {:key (str "tag-" id)} tag])]
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
         [:div.top-operations {:style {:font-weight "normal"}} "[Logged in as: " user-name "]"]
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
      [:div#menu-button {:on-click actions/logout} "⚙"]]]))

(defn make-player [player-html-element]
  (let [player (js/MediaElementPlayer.
                player-html-element
                (clj->js {:stretching "responsive"
                          :preload "auto"
                          :features ["playpause" "current" "progress" "tracks" "duration"]}))]
    (.addEventListener player-html-element "ended" #(js/alert "NEXT"))))

(defn render
  ([] (render @db/db))
  ([db] (rum/mount (app db) (js/document.getElementById "app"))))

(defn init! []
  (render)
  (make-player (js/document.querySelector "audio[controls], video[controls]")))
