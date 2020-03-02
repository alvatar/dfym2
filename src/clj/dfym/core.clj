(ns dfym.core
  (:require [clojure.pprint :refer [pprint]]
            [com.stuartsierra.component :as component]
            ;; Logging
            [taoensso.timbre :as log]
            ;;-----
            [dfym.usecases :refer [map->UseCases]]
            [dfym.controllers.http :refer [map->HTTPController]]
            [dfym.controllers.websocket :refer [map->WebsocketController]]
            [dfym.adapters.postgresql :refer [map->PostgreSqlAdapter]])
  (:gen-class))

(log/set-level! :debug)

;;
;; Components initialization
;;

(defn system [& [config-options]]
  (component/system-map
   :http-controller (map->HTTPController (select-keys config-options [:ip :port]))
   :websocket-controller (map->WebsocketController {})
   :use-cases (component/using
               (map->UseCases {})
               [:repository])
   :repository (map->PostgreSqlAdapter {})))

(defn -main [& [port ip]]
  (let [s (system {:ip ip :port port})]
    (component/start s)
    (aleph.netty/wait-for-close (get-in s [:server :aleph]))))
