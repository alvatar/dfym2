(ns dfym.actions
  (:require
   [taoensso.encore :as encore :refer-macros [have have?]]
   [taoensso.sente :as sente :refer [cb-success?]]
   [taoensso.sente.packers.transit :as sente-transit]
   [goog.string :as gstring]
   [oops.core :refer [oget oset!]]
   ;; -----
   [dfym.utils :as utils :refer [log*]]
   [dfym.client :as client]
   [dfym.db :as db]))

;;
;; Actions
;;

(defn build-request [id & [handler]]
  (fn [data & [cb]]
    (client/chsk-send!
     [id data] 30000
     (fn [resp]
       (if (sente/cb-success? resp)
         (do (when handler (handler resp))
             (when cb (cb resp)))
         (do (log* resp)
             (js/alert (gstring/format "Ups... Error in %s ¯\\_(ツ)_/¯ Restart the app, please..." id))))))))

(defn logout []
  (oset! js/window "location.href" "/logout"))

(defn get-user [user]
  ((build-request :user/get
                  (fn [result] (log* "TODO " result)))
   {:user {:id user}}))

(defn get-files [user]
  ((build-request :files/get
                  (fn [result] (db/set-files! result)))
   {:user {:id user}}))

;;
;; Reactions
;;

(defmethod client/-event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (let [[old-state-map new-state-map] (have vector? ?data)]
    (when (:first-open? new-state-map)
      (log* "New state: " new-state-map))))

(defmethod client/-event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data uid]}]
  (let [[?user & _] ?data]
    (when-not ?user
      (js/alert "Internal Error: loading user")
      (logout))
    (db/set-system-attrs! :user ?user)))
