(ns dfym.controllers.http
  (:require [clojure.java.io :as io]
            [clojure.stacktrace :refer [print-stack-trace]]
            [clojure.pprint :refer [pprint]]
            [com.stuartsierra.component :as component]
            ;; Ring
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            ;; Compojure
            [compojure.core :refer [ANY GET PUT POST DELETE defroutes]]
            [compojure.route :refer [resources not-found]]
            [environ.core :refer [env]]
            ;; Logging
            [taoensso.timbre :as log]
            ;; Web
            [ring.middleware.defaults :refer :all]
            [ring.middleware.stacktrace :as trace]
            [prone.middleware :as prone]
            [aleph [netty] [http]]
            [compojure.route :as route]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.aleph :refer [get-sch-adapter]]
            [taoensso.sente.packers.transit :as sente-transit]
            ;;----
            [dfym.usecases :as usecases])
  (:import java.lang.Integer
           java.net.InetSocketAddress)
  (:gen-class))


(let [packer (sente-transit/get-transit-packer)
      {:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket! (get-sch-adapter) {:packer packer})]
  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv)              ; ChannelSocket's receive channel
  (def chsk-send! send-fn)           ; ChannelSocket's send API fn
  (def connected-uids connected-uids))

(defn user-home [req]
  {:status 200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body (usecases/index (:anti-forgery-token req))})

(defroutes app
  (GET "/" req user-home)
  (GET "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post req))
  (resources "/")
  (not-found "Woooot? Not found!"))

(defn- new-server [ip port]
  (let [port (or port (env :port) 5000)]
    (aleph.http/start-server
     (-> app
         prone/wrap-exceptions
         (wrap-defaults (if (env :production) secure-site-defaults site-defaults))
         wrap-gzip)
     {:port (Integer. port)
      :socket-address (when ip (new InetSocketAddress ip port))})))

;;
;; Controller
;;

(defrecord HTTPController [aleph ip port]
  component/Lifecycle
  (start [component]
    (println "Starting HTTP Server...")
    (merge component {:aleph (new-server ip port)
                      :ip ip
                      :port port}))
  (stop [component]
    (println "Stopping HTTP Server...")
    (.close (:aleph component))
    (dissoc component :aleph)))
