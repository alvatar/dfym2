(ns dfym.client
  (:require
   [taoensso.sente :as sente]
   [taoensso.sente.packers.transit :as sente-transit]
   ;; -----
   [dfym.globals :as globals]
   [dfym.utils :as utils :refer [log*]]))


;;
;; Sente setup
;;

(def ?csrf-token
  (when-let [el (.getElementById js/document "sente-csrf-token")]
    (.getAttribute el "data-csrf-token")))

(if ?csrf-token
  (log* "CSRF token detected in HTML, great!")
  (log* "CSRF token NOT detected in HTML, default Sente config will reject requests"))

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket-client!
       "/chsk"
       ?csrf-token
       {:host (let [conn (if (= globals/*env* "dev")
                           (if globals/*enable-mobile-dev*
                             (str globals/*server-ip* ":5000")
                             "localhost:5000")
                           "domain.com")]
                (log* "Connecting to " conn))
        :protocol (if (= globals/*env* "dev") "http:" "https:")
        :type :auto
        :packer (sente-transit/get-transit-packer)})]
  (def chsk chsk)
  (def ch-chsk ch-recv)              ; ChannelSocket's receive channel
  (def chsk-send! send-fn)           ; ChannelSocket's send API fn
  (def chsk-state state)) ; Watchable, read-only atom

;;
;; Sente event handlers
;;

(defmulti -event-msg-handler
  "Multimethod to handle Sente `event-msg`s"
  :id)

(defn event-msg-handler
  "Wraps `-event-msg-handler` with logging, error catching, etc."
  [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg))

(defmethod -event-msg-handler
  :default ; Default/fallback case (no other matching handler)
  [{:as ev-msg :keys [event]}]
  (log* "Unhandled event: " event))

(defmethod -event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (log* "Push event from server: " ?data))

(defonce router_ (atom nil))
(defn stop-router! [] (when-let [stop-f @router_] (stop-f)))
(defn start-router! []
  (stop-router!)
  (log* "Initializing Sente client router")
  (reset! router_
    (sente/start-client-chsk-router!
      ch-chsk event-msg-handler)))
