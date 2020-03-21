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

(defonce db (d/create-conn schema))

(defn persist [db] (js/localStorage.setItem "dfym/db" (db->string db)))

#_(js/localStorage.clear)

;; Hack to inject render-fn
;; (def render-fn nil)

;; (defn reset-db! [db]
;;   (reset! db db)
;;   ;;(when render-fn (render-fn db))
;;   ;; (ui/render db)
;;   (persist db))

(d/listen! db :log
           (fn [tx-report]
             (let [tx-id  (get-in tx-report [:tempids :db/current-tx])
                   datoms (:tx-data tx-report)]
               (log* "TRANSACTIONS: " datoms))))

;; (or (when-let [stored (js/localStorage.getItem "dfym/db")]
;;       (let [stored-db (string->db stored)]
;;         (when (= (:schema stored-db) schema) ;; check for code update
;;           (reset-db! stored-db)
;;           ;;(reset-conn! stored-db)
;;           ;;(swap! history conj @conn)
;;           true)))
;;     (d/transact! conn fixtures))


;; (d/listen! conn :persistence
;;              (fn [tx-report] ;; FIXME do not notify with nil as db-report
;;                ;; FIXME do not notify if tx-data is empty
;;                (when-let [db (:db-after tx-report)]
;;                  (js/setTimeout #(persist db) 0))))

;; System attributes

(defn set-system-attrs! [& args]
  (d/transact! db
               (for [[attr value] (partition 2 args)]
                 (if value
                   ;; ID 1 is a convention here.
                   [:db/add 1 attr value]
                   [:db.fn/retractAttribute 0 attr]))))

(defn get-system-attr
  ([attr]
   (get (d/entity @db 1) attr))
  ([db attr]
   (get (d/entity db 1) attr))
  ([db attr & attrs]
   (mapv #(get-system-attr db %) (concat [attr] attrs))))

;; Tags

(defn create-tag! [[id tag]]
  'TODO)

(defn get-tags [db]
  (let [res (d/q '[:find ?e ?tag
                   :where
                   [?e :tag/name ?tag]]
                 db)]
    res))

(defn update-tag! [[id tag]]
  'TODO)

(defn delete-tag! [[id tag]]
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

;; Init

(set-system-attrs! :user {})
(d/transact! db fixtures/tags)
