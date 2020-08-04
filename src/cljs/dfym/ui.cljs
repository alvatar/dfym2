(ns dfym.ui
  (:require
   [taoensso.timbre :as timbre :refer-macros [tracef debugf infof warnf errorf]]
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
(def player (atom nil))

;;
;; UI Components
;;

(rum/defcs tags < rum/reactive
  (rum/local "" ::new-tag)
  [state db]
  (let [new-tag (::new-tag state)]
    [:div
     [:h2 "ALL TAGS"]
     [:div.panel
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
                    :on-mouse-up #(db/add-filter-tag! id)
                    :draggable "true"
                    :on-drag-start (fn [ev]
                                     (.setData (.-dataTransfer ev) "ID" id) ; Data received in the dropped
                                     (oset! (.-target ev) "style.opacity" 1))}
          tag])]
      [:div.panel-bottom]]]))

(rum/defc filter-tags [db]
  [:div
   [:h2 "SELECTED TAGS"]
   [:div.panel {:style {:height "60%"}}
    (for [[filter-id id tag] (db/get-filter-tags db)]
      [:.tag {:key id
              :on-mouse-down #(dfym.db/remove-filter-tag! filter-id)}
       tag])
    [:.div.panel-bottom]]
   ])

(defonce current-playlist (atom {}))

(defn play [user-id playlist play-idx]
  (let [[_ file-id _] (nth playlist play-idx)]
    ;; (infof "Playing file %s" file-id)
    (reset! current-playlist {:user-id user-id
                              :playlist playlist
                              :index play-idx
                              :file-id file-id})
    (actions/get-file-link user-id file-id
                           #(doto @player
                              (.setSrc (get % :link))
                              (.load)
                              (.play)))))

(defn play-next []
  (let [{:keys [user-id playlist index]} @current-playlist
        playlist-length (count playlist)
        next (if (= index (- playlist-length 1)) 0 (inc index))]
    (play user-id playlist next)))

(defn build-playlist [db]
  (->> (db/get-current-folder-elements db)
       (sort-by #(nth % 2))))

(rum/defc file-listing < rum/reactive [db]
  (let [{user-id :id user-name :user-name} (db/get-user db)]
    [:div
     [:h2 "FILTERED FILES"
      [:div.top-operations {:style {:font-weight "normal"}} "[Logged in as: " user-name "]"]
      [:div.top-operations {:on-click #(actions/get-files user-id)}
       "Refresh ◌"]
      [:div.top-operations {:on-click #(js/alert "I don't wanna")} "Deselect_All □"]
      [:div.top-operations {:on-click #(js/alert "select your ass!")} "Select_All ▦"]
      [:div.top-operations {:on-click #(js/alert "random shit in your fan!")}
       "Randomize ☲"]]
     [:div.panel
      (let [playlist (build-playlist db)
            contents (for [[idx [eid file-id file-name folder?]] (map-indexed (fn [i e] [i e]) playlist)]
                       (if folder?
                         [:.dir {:key file-id
                                 :on-click #(db/go-to-folder! file-id)
                                 :on-drag-enter #(oset! (.-target %) "className" "dir-hover")
                                 :on-drag-leave #(oset! (.-target %) "className" "dir")
                                 :on-drag-over #(.preventDefault %)
                                 :on-drop (fn [ev]
                                            (.preventDefault ev)
                                            (oset! (.-target ev) "className" "dir")
                                            ;; ID sent from the draggable
                                            (db/link-tag! eid (js/parseInt (.getData (.-dataTransfer ev) "ID"))))}
                          (str file-name)]
                         [:.file {:key file-id
                                  :on-click #(play user-id playlist idx)
                                  :on-drag-enter #(oset! (.-target %) "className" "file-hover")
                                  :on-drag-leave #(oset! (.-target %) "className" "file")
                                  :on-drag-over #(.preventDefault %)
                                  :on-drop (fn [ev]
                                             (.preventDefault ev)
                                             (oset! (.-target ev) "className" "file")
                                             ;; ID sent from the draggable
                                             (db/link-tag! eid (js/parseInt (.getData (.-dataTransfer ev) "ID"))))}
                          (let [{index_ :index file-id_ :file-id} (rum/react current-playlist)]
                            (str (if (and (= index_ idx) (= file-id_ file-id)) "▶ " "□ ")
                                 file-name))]))]
        (if (db/is-top-folder? db)
          contents
          (cons [[:.dir {:key "parent-folder"
                         :on-click #(db/go-to-parent-folder! db)}
                  "../"]]
                contents)))]
     [:.div.panel-bottom]]))

(rum/defc app [db]
  [:div.no-scroll {:style {:width "100%" :height "100%"}}
   [:div {:style {:padding "20px"}}
    [:div.col-3-4 {:style {:width "75%"}}
     (file-listing db)]
    [:div.col-1-4 {:style {:padding "0 8px 0 20px"}}
     [:div {:style {:height "65%" :padding-bottom "20px"}}
      (tags db)]
     [:div {:style {:height "35%"}}
      (filter-tags db)]]]
   [:div#controls
    [:div#player
     [:audio {:controls "controls"}
      [:source {:src "https://www.dropbox.com/s/12fpcuwwmg8s7aj/02%20-%20Theme%20From%20Jack%20Johnson.mp3?raw=1"}]]]
    [:div#menu-button {:on-click actions/logout} "⚙"]]])

(defn make-player [player-html-element]
  (reset! player (js/MediaElementPlayer.
                  player-html-element
                  (clj->js {:stretching "responsive"
                            :preload "auto"
                            :features ["playpause" "current" "progress" "tracks" "duration"]})))
  (.addEventListener player-html-element "ended" play-next))

(defn render
  ([] (render @db/db))
  ([db] (rum/mount (app db) (js/document.getElementById "app"))))

(defn init! []
  (render)
  (make-player (js/document.querySelector "audio[controls], video[controls]")))
