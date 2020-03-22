(ns dfym.core
  (:require
   [taoensso.encore :as encore :refer-macros [have have?]]
   [taoensso.timbre :as timbre :refer-macros [tracef debugf infof warnf errorf]]
   [cljs.core.async :as async :refer [<! >! put! take! chan]]
   [oops.core :refer [oget oset!]]
   [goog.string :as gstring]
   [datascript.core :as d]
   ;; -----
   [dfym.utils :as utils :refer [log*]]
   [dfym.client :as client]
   [dfym.ui :as ui]
   [dfym.db :as db]))

(goog-define ^:dynamic *is-dev* false)

(enable-console-print!)
(timbre/set-level! :debug)

(ui/init!)
(client/start-router!)

;; re-render on every DB change
(d/listen! db/db :render
  (fn [tx-report]
    (ui/render (:db-after tx-report))))

