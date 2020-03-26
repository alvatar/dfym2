(ns dfym.core
  (:require
   [taoensso.encore :as encore :refer-macros [have have?]]
   [taoensso.timbre :as timbre :refer-macros [tracef debugf infof warnf errorf]]
   [datascript.core :as d]
   ;; -----
   [dfym.utils :as utils :refer [log*]]
   [dfym.client :as client]
   [dfym.ui :as ui]
   [dfym.db :as db]))

(goog-define ^:dynamic *is-dev* false)

;; Todo: make as components
(defn init! []
  (enable-console-print!)
  (timbre/set-level! :debug)
  (db/init!)
  (d/listen! db/db :render ; render on every DB change
             (fn [tx-report]
               (ui/render (:db-after tx-report))))
  (ui/init!)
  (client/start-router!))

(defonce _init (init!))
