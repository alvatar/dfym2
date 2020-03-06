(ns dfym.controllers.websocket
  (:require [clojure.pprint :refer [pprint]]
            [com.stuartsierra.component :as component]
            ;; Environment and configuration
            [environ.core :refer [env]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.aleph :refer [get-sch-adapter]]
            [taoensso.sente.packers.transit :as sente-transit]
            ;;-----
            [dfym.controllers.http :refer [ch-chsk chsk-send! connected-uids]]
            [dfym.usecases :as usecases]))

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
  (future (-event-msg-handler ev-msg)) ; Handle event-msgs on a thread pool
  )

;; (defmethod -event-msg-handler :default ; Default/fallback case (no other matching handler)
;;   [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
;;   (let [session (:session ring-req)
;;         uid (:uid session)]
;;     (when ?reply-fn
;;       (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))

;; (defmethod -event-msg-handler :user/get
;;   [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
;;   (?reply-fn (usecases/user-get
;;               (:user-id ?data))))

;; (defmethod -event-msg-handler :user/update!
;;   [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
;;   (?reply-fn (usecases/user-update!
;;               (:user-id ?data)
;;               (:user-data ?data))))

;; (defmethod -event-msg-handler :files/get
;;   [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
;;   (?reply-fn (usecases/files-get
;;               (:user-id ?data)
;;               (:filters ?data))))

;; (defmethod -event-msg-handler :files/tag!
;;   [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
;;   (?reply-fn (usecases/files-tag!
;;               (:user-id ?data)
;;               (:files ?data)
;;               (:tag ?data))))

;; (defmethod -event-msg-handler :files/resync!
;;   [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
;;   (?reply-fn (usecases/files-resync!
;;               (:user-id ?data))})

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
