(ns dfym.controllers.http
  (:require [clojure.java.io :as io]
            [clojure.stacktrace :refer [print-stack-trace]]
            [clojure.pprint :refer [pprint]]
            [com.stuartsierra.component :as component]
            [clj-time.core :as time]
            ;; Ring
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            ;; Compojure
            [compojure.core :refer [ANY GET PUT POST DELETE defroutes]]
            [compojure.route :refer [resources]]
            [environ.core :refer [env]]
            ;; Logging
            [taoensso.timbre :as log]
            ;; Web middleware
            [ring.middleware.defaults :refer :all]
            [ring.middleware.stacktrace :as trace]
            [prone.middleware :as prone]
            [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.backends.session :refer [session-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            ;; Server
            [aleph [netty] [http]]
            [ring.util.response :refer :all]
            [compojure.route :as route]
            ;; Sente
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.aleph :refer [get-sch-adapter]]
            [taoensso.sente.packers.transit :as sente-transit]
            ;;----
            [dfym.usecases.html :as html]
            [dfym.usecases :as usecases])
  (:import java.lang.Integer
           java.net.InetSocketAddress)
  (:gen-class))


(def sign-secret "341lm2qrytc81hlfiguahs01t648913g@#tcgaHG!#$%XQErtj163erp8ahsm")

(let [packer (sente-transit/get-transit-packer)
      {:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket! (get-sch-adapter) {:packer packer})]
  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv)              ; ChannelSocket's receive channel
  (def chsk-send! send-fn)           ; ChannelSocket's send API fn
  (def connected-uids connected-uids))

(defn ok [html]
  (-> html
      response
      (header "Content-Type" "text/html; charset=utf-8")))

(defn index [req]
  (ok (html/index (:anti-forgery-token req))))

(defn login [req]
  (ok (html/login (:flash req))))

(defn login-authenticate [req]
  (let [username (get-in req [:form-params "username"])
        password (get-in req [:form-params "password"])
        session (:session req)]
    (if (usecases/user-check-password username password)
      (let [next-url (get-in req [:query-params :next] "/")
            updated-session (assoc session :identity (keyword username))]
        (-> (redirect (or next-url "/"))
            (assoc :session updated-session)))
      (-> (redirect "/login")
          (assoc :flash "Wrong Username or Password")))))

(defn logout [req]
  (-> (redirect "/login")
      (assoc :session {})))

;; (redirect "https://www.dropbox.com/oauth2/authorize?client_id=ewrsd8qgaxdvks3&response_type=code")

(defn authenticate [handler]
  (fn [req]
    (if-not (authenticated? req)
      (throw-unauthorized)
      (handler req))))

(defroutes app
  (GET "/" req (authenticate index))
  (GET "/login" req login)
  (POST "/login" req login-authenticate)
  (GET "/logout" [] logout)
  (GET "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post req))
  (resources "/")
  (not-found "Woooot? Not found!"))

(defn unauthorized-handler
  [request metadata]
  (if (authenticated? request)
    ;; If request is authenticated, raise 403 instead
    ;; of 401 (because user is authenticated but permission
    ;; denied is raised).
    (-> request
        (assoc :status 403))
    ;; In other cases, redirect the user to login page.
    (let [current-url (:uri request)]
      (redirect (format "/login?next=%s" current-url)))))

(def auth-backend
  (session-backend {:unauthorized-handler unauthorized-handler}))

(defn- new-server [ip port]
  (let [port (or port (env :port) 5000)]
    (aleph.http/start-server
     (-> app
         (wrap-authorization auth-backend)
         (wrap-authentication auth-backend)
         (wrap-defaults (if (env :production) secure-site-defaults site-defaults))
         prone/wrap-exceptions
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
