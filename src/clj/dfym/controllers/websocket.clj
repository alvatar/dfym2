(ns dfym.controllers.websocket
  (:require [clojure.pprint :refer [pprint]]
            [com.stuartsierra.component :as component]
            ;; Environment and configuration
            [environ.core :refer [env]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.aleph :refer [get-sch-adapter]]
            [taoensso.sente.packers.transit :as sente-transit]
            [cljs.repl.server :refer [gzip]]
            ;;-----
            [dfym.controllers.http :refer [ch-chsk chsk-send! connected-uids]]
            [dfym.usecases :as usecases]))


(defn authorized-user? [{:keys [?data ring-req] :as msg}]
  (let [session (:session ring-req)
        uid (get-in ring-req [:session :identity :id])
        user (:user ?data)
        id (:id user)]
    (or (= id uid)
        (do (println "Failed to authenticate uid:" uid "- id:" id)
            false))))

(def error-unauthorized
  {:status "error"
   :message "unauthorized"})

;;
;; Sente event handlers
;;

(defmulti -event-msg-handler
  "Multimethod to handle Sente `event-msg`s"
  ;; Dispatch on event-id
  :id)

(defn event-msg-handler
  "Wraps `-event-msg-handler` with logging, error catching, etc."
  [{:as ev-msg :keys [id ?data event]}]
  ;; (-event-msg-handler ev-msg) ; Handle event-msgs on a single thread
  ;; TODO: proper thread pool
  (future (-event-msg-handler ev-msg)) ; Handle event-msgs on a thread pool
  )

(defmethod -event-msg-handler :default ; Default/fallback case (no other matching handler)
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid (:uid session)]
    (when ?reply-fn (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))

;; User

(defmethod -event-msg-handler :user/get
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [{:keys [user-id]} ?data]
    (?reply-fn (if (authorized-user? ev-msg)
                 (usecases/get-user (get ?data :user))
                 error-unauthorized))))

;; (defmethod -event-msg-handler :user/update!
;;   [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
;;   (let [{:keys [user-id]} ?data]
;;     (?reply-fn (if (authorized-user? ev-msg)
;;                  (usecases/update-user! (get ?data :user))
;;                  error-unauthorized))))

;; Files

(defmethod -event-msg-handler :files/get
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (?reply-fn (if (authorized-user? ev-msg)
               (usecases/get-files (get-in ?data [:user :id]))
               error-unauthorized)))

(defmethod -event-msg-handler :files/resync!
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (?reply-fn (if (authorized-user? ev-msg)
               (usecases/resync-files! (get-in ?data [:user :id]))
               error-unauthorized)))

(defmethod -event-msg-handler :file/get-link
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (?reply-fn (if (authorized-user? ev-msg)
               (usecases/get-file-link (get-in ?data [:user :id])
                                       (get-in ?data [:file :id]))
               error-unauthorized)))

;; Tags

;; (defmethod -event-msg-handler :tags/get
;;   [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
;;   (?reply-fn (if (authorized-user? ev-msg)
;;                (usecases/get-tags (get-in ?data [:user :id]))
;;                error-unauthorized)))

;; (defmethod -event-msg-handler :tag/update!
;;   [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
;;   "Expects the data to be a regular tag, where the id is references the tag to be updated"
;;   (?reply-fn (if (authorized-user? ev-msg)
;;                (usecases/update-tag! (get-in ?data [:user :id])
;;                                      (get ?data :tag))
;;                error-unauthorized)))

(defmethod -event-msg-handler :tag/create!
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (?reply-fn (if (authorized-user? ev-msg)
               (usecases/create-tag! (get-in ?data [:user :id])
                                     (get-in ?data [:tag :name]))
               error-unauthorized)))

(defmethod -event-msg-handler :tag/attach!
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  ;;(println "ATTACH" (get-in ?data [:user :id]) (get-in ?data [:tag :name]) (get-in ?data [:file :id]))
  (?reply-fn (if (authorized-user? ev-msg)
               (usecases/attach-tag! (get-in ?data [:user :id])
                                     (get-in ?data [:file :id])
                                     (get-in ?data [:tag :name]))
               error-unauthorized)))

;; (defmethod -event-msg-handler :tag/unlink!
;;   [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
;;   (?reply-fn (if (authorized-user? ev-msg)
;;                (usecases/unlink-tag! (get-in ?data [:user :id])
;;                                      (get-in ?data [:file :id])
;;                                      (get ?data :tag))
;;                error-unauthorized)))

;;
;; Controller
;;

(defrecord WebsocketController
    [sente]
    component/Lifecycle
    (start [component]
      (assoc component
             :sente
             (sente/start-server-chsk-router! ch-chsk event-msg-handler)))
    (stop [component]
      component))
