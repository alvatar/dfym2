(ns dfym.db
  (:require
   [datascript.transit :as dt]
   [datascript.core :as d]
   ;;-----
   [dfym.fixtures :as fixtures]
   [dfym.utils :as utils :refer [log*]]))

(defn db->string [db] (dt/write-transit-str db))
(defn string->db [s] (dt/read-transit-str s))

(def schema {:tag/name {:db/unique :db.unique/identity}
             ;; :file/tags {:db/cardinality :db.cardinality/many
             ;;             :db/index true}
             ;; :tag/files {:db/cardinality :db.cardinality/many
             ;;             :db/index true}
             })

(defonce conn (d/create-conn schema))

(defn persist [db] (js/localStorage.setItem "dfym/db" (db->string db)))

#_(js/localStorage.clear)

(defn reset-db! [db]
  (reset! conn db)
  ;; (render db)
  (persist db))

(d/listen! conn :log
           (fn [tx-report]
             (let [tx-id  (get-in tx-report [:tempids :db/current-tx])
                   datoms (:tx-data tx-report)]
               (log* datoms))))

;; (or (when-let [stored (js/localStorage.getItem "dfym/db")]
;;       (let [stored-db (string->db stored)]
;;         (when (= (:schema stored-db) schema) ;; check for code update
;;           (reset-db! stored-db)
;;           ;;(reset-conn! stored-db)
;;           ;;(swap! history conj @conn)
;;           true)))
;;     (d/transact! conn fixtures))
(d/transact! conn fixtures/tags)

;; (d/listen! conn :persistence
;;              (fn [tx-report] ;; FIXME do not notify with nil as db-report
;;                ;; FIXME do not notify if tx-data is empty
;;                (when-let [db (:db-after tx-report)]
;;                  (js/setTimeout #(persist db) 0))))

;; Tags

(defn get-tags []
  (let [res (d/q '[:find ?e ?tag
                   :where
                   [?e :tag/name ?tag]]
                 @conn)]
    (log* res)
    res))

(defn update-tag! [[id tag]]
  'TODO)

;; Filters

(defn get-filters []
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;; HERE
  'TODO)

(defn add-filter! [tag]
  'TODO)

(defn remove-filter! [tag]
  'TODO)

;; Tag linking

(defn link-tag! [file-id tag]
  'TODO)

(defn unlink-tag! [file-id tag]
  'TODO)

;; Files

(defn get-files [root]
    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;; HERE
  'TODO)
