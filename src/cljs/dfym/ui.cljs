(ns dfym.ui
  (:require
   [rum.core :as rum]
   [garden.core :refer [css]]
   [garden.stylesheet :as stylesheet]
   [goog.style]
   [oops.core :refer [oget oset! oset!]]
   ;; -----
   [dfym.globals :refer [window mouse]]
   [dfym.utils :as utils :refer [log*]]
   [dfym.db :as db]
   [dfym.actions :as actions]
   [dfym.dom :as dom]))

;; Convert JSX to Cljs
;; https://github.com/madvas/jsx-to-clojurescript

(declare render)

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

(rum/defc draggable-tag < rum/reactive
  [tag-data*]
  (let [pos (rum/react mouse)
        tag-data (rum/react tag-data*)]
    (when tag-data
      (let [[id name] tag-data]
        [:div.drag-space {:style {:position "fixed " :top 0 :left 0 :width "100%" :height "100%"}
                          :on-mouse-up (fn []
                                         (reset! tag-data* nil)
                                         (if (and (> (:x pos) (/ (:width @window) 4))
                                                  (< (:x pos) (/ (:width @window) 2)))
                                           (db/add-filter-tag! id)))}
         [:h1 {:style {:position "absolute"
                       :width (- (/ (:width @window) 4) 40)
                       :left (- (:x pos) (/ (:width @window) 18))
                       :top (- (:y pos) 30)}}
          [:div.tag name]]]))))

(rum/defcs tags < rum/reactive
  (rum/local "" ::new-tag)
  (rum/local nil ::dragging)
  [state db]
  (let [new-tag (::new-tag state)
        dragging (::dragging state)]
    [:div
     [:div.panel
      [:h2 "TAGS"]
      [:div {:style {:width "100%"}}
       [:input.add-tag {:type "text"
                        :placeholder "Add tag..."
                        :on-change (fn [] (swap! new-tag #(dom/qval ".add-tag")))
                        :on-key-down #(when (= (.-keyCode %) 13)
                                        (db/create-tag! @new-tag)
                                        (dom/set-val! (dom/q ".add-tag") ""))}]]
      [:div {:style {:width "100%"}}
       (for [[id tag] (db/get-tags db)]
         [:div.tag {:key id
                    :on-mouse-down #(reset! dragging [id tag])}
          tag])]
      [:div.panel-bottom]]
     (draggable-tag dragging)]))

(rum/defc filter-tags [db]
  [:div (for [[filter-id id tag] (db/get-filter-tags db)]
          [:.tag {:key id
                  :on-mouse-down #(dfym.db/remove-filter-tag! filter-id)}
           tag])])

(rum/defc app [db]
  (let [{:keys [id user-name]} (db/get-system-attr db :user)]
    [:div.no-scroll {:style {:width "100%" :height "100%"}}
     [:div {:style {:padding "20px"}}
      [:div.col-1-4 {:style {:padding "0 8px 0 20px"}}
       (tags db)]
      [:div.col-1-4 {:style {:padding "0 8px 0 0px"}}
       [:div.panel
        [:h2 "SELECTED TAGS"]
        (filter-tags db)
        [:.div.panel-bottom]]]
      [:div.col-2-4 {:style {:width "50%"}}
       [:div.panel
        [:h2 "FILTERED FILES"
         [:div.top-operations {:style {:font-weight "normal"}} "[Logged in as: " user-name "]"]
         [:div.top-operations "Deselect_All"]
         [:div.top-operations "Select_All"]
         [:div.top-operations {:on-click #(actions/get-files id)}
          "Refresh"]]
        [:div
         (cons [[:.file.dir {:on-click #(db/go-to-parent-folder!)
                             :key "parent-folder"}
                 "../"]]
               (for [{file-id :file/id
                      file-name :file/name} (sort-by :file/name (db/get-current-folder))]
                 [:.file.dir {:on-click #(db/go-to-folder! file-id)
                              :key file-id}
                  (str file-name)]))]
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
