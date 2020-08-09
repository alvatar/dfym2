(ns dfym.actions
  (:require
   [taoensso.encore :as encore :refer-macros [have have?]]
   [taoensso.sente :as sente :refer [cb-success?]]
   [taoensso.sente.packers.transit :as sente-transit]
   [taoensso.timbre :as log]
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
             (log/error "Ups... Error in %s ¯\\_(ツ)_/¯ Restart the app, please...")))))))

(defn logout []
  (oset! js/window "location.href" "/logout"))

(defn pull-files! [user-id]
  ((build-request :files/get (fn [result] (db/set-files! result)))
   {:user {:id user-id}}))

(defn pull-tags! [user-id]
  ((build-request :tags/get (fn [[tags files-tags]] (db/set-tags! tags files-tags)))
   {:user {:id user-id}}))

(defn get-file-link [user-id file-id cb]
  ((build-request :file/get-link)
   {:user {:id user-id}
    :file {:id file-id}}
   cb))

(defn create-tag! [user-id tag]
  ((build-request :tag/create!)
   {:user {:id user-id}
    :tag {:name tag}}))

(defn attach-tag! [user-id file-id tag]
  ((build-request :tag/attach!)
   {:user {:id user-id}
    :file {:id file-id}
    :tag {:name tag}}))

;;
;; Data reactions
;;

(defn process-events [database datoms]
  (let [[[eid operation & vals]] datoms]
    (case operation
      :tag/file
      (let [[file _] vals]
        (attach-tag! (:id (db/get-user database))
                     (:file/id (db/get-file-info database file))
                     (:tag/name (db/get-tag-name database eid))))
      :tag/name
      (create-tag! (:id (db/get-user database))
                   (first vals))
      nil)))

;;
;; Websocket Reactions
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
