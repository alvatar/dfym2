(ns dfym.core
  (:require
   [taoensso.encore :as encore :refer-macros [have have?]]
   [taoensso.timbre :as timbre :refer-macros [tracef debugf infof warnf errorf]]
   [datascript.core :as d]
   [butler.core :as butler]
   ;; -----
   [dfym.utils :as utils :refer [log*]]
   [dfym.client :as client]
   [dfym.ui :as ui]
   [dfym.db :as db]
   [dfym.globals :as globals]))

(goog-define ^:dynamic *is-dev* false)


(defn print-string-handler [string]
  (println string))

(defn print-array-handler [{:keys [array-buffer]}]
  (println (-> (.-prototype js/Array)
               .-slice
               (.call (js/Float32Array. array-buffer))
               js->clj)))

;; Todo: make as components
(defn init! []
  (enable-console-print!)
  (timbre/set-level! :debug)

  (def worker (butler/butler "/js/worker.js"
                             {:print-string print-string-handler
                              :print-array print-array-handler}))

  (db/init!)
  ;; Render on every DB change
  (d/listen! db/db :render
             (fn [tx-report]
               (ui/render (:db-after tx-report))))
  ;; Persist DB on every change
  (d/listen! db/db :persistence
             (fn [tx-report]
               ;; FIXME do not notify with nil as db-report?
               ;; FIXME do not notify if tx-data is empty?
               ;; TODO: Idea. Have a copy of the DB in the webworker, and instead of sending the DB,
               ;; send the transactions, and duplicate the behavior there
               (when-let [db (:db-after tx-report)]
                 ;; (js/setTimeout #(db/persist-db! db) 500)
                 ;; The 500 millisecond delay provides a smoother UI experience, since serialization
                 ;; is expensive and must be done before delivering to the webworker
                 (js/setTimeout #(butler/work! worker
                                               :persist-db (db/db->string db))
                                500))))
  (globals/init!)
  (ui/init!)
  (client/start-router!))

(defonce _init (init!))

;; For development with Figwheel
(ui/init!)
