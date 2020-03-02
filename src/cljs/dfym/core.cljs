(ns dfym.core
  (:require
   [taoensso.encore :as encore :refer-macros [have have?]]
   [taoensso.timbre :as timbre :refer-macros [tracef debugf infof warnf errorf]]
   [cljs.core.async :as async :refer [<! >! put! take! chan]]
   [taoensso.sente :as sente :refer [cb-success?]]
   [taoensso.sente.packers.transit :as sente-transit]
   [rum.core :as rum]
   [garden.core :refer [css]]
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
;; Event Handlers
;;

(defmethod client/-event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (let [[old-state-map new-state-map] (have vector? ?data)]
    (when (:first-open? new-state-map)
      (log* new-state-map))))

(defmethod client/-event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?user ?csrf-token ?handshake-data] ?data]
    ;; (reset! (:user-id app-state) ?user)
    (when-not (= ?user :taoensso.sente/nil-uid)
      (log* "HANDSHAKE"))))

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

(def get-user
  (build-request :user/get
                 (fn [args] (log* "RECEIVED" args))))

;;
;; UI Components
;;

(def styles
  (css [:h1 {:font-weight "bold"}]))

(defonce style-node (atom nil))
(if @style-node
  (goog.style/setStyles @style-node styles)
  (reset! style-node (goog.style/installStyles styles)))

(rum/defc app []
  [:section.section>div.container
   [:h1.title "HELLO"]
   [:p.subtitle {:on-click #(get-user 0)} "Let's go!"]])

;;
;; Init
;;

(rum/mount (app) (js/document.getElementById "app"))

(client/start-router!)
