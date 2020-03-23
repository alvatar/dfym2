(ns dfym.db
  (:require
   [datascript.transit :as dt]
   [datascript.core :as d]
   ;;-----
   [dfym.fixtures :as fixtures]
   [dfym.utils :as utils :refer [log*]]))

(def ^:const db-name "dfym/db")
(def ^:const schema {:file/child {:db/valueType :db.type/ref
                                  :db/cardinality :db.cardinality/many}
                     :file/id {:db/unique :db.unique/identity}
                     :tag/name {:db/unique :db.unique/identity}
                     :file/tags {:db/cardinality :db.cardinality/many}
                     :tag/files {:db/cardinality :db.cardinality/many
                                 :db/index true}})

(defn db->string [db] (dt/write-transit-str db))
(defn string->db [s] (dt/read-transit-str s))

(defonce db (d/create-conn schema))

(defn persist-db! [db]
  (log* "Persisting DB...")
  (js/ldb.set db-name (db->string db)))

(defn load-db []
  (log* "Loading DB...")
  (js/ldb.get db-name (fn [stored] (when stored
                                     (let [stored-db (string->db stored)]
                                       (when-not (:schema stored-db) (log* "SCHEMA NOT FOUND!!"))
                                       (reset! db stored-db))))))

;; (js/ldb.set db-name nil)

(d/listen! db :log
           (fn [tx-report]
             (let [tx-id  (get-in tx-report [:tempids :db/current-tx])
                   datoms (:tx-data tx-report)]
               (log* "TRANSACTIONS: " datoms))))

(d/listen! db :persistence
           (fn [tx-report]
             ;; FIXME do not notify with nil as db-report
             ;; FIXME do not notify if tx-data is empty
             (when-let [db (:db-after tx-report)]
               (js/setTimeout #(persist-db! db) 0))))

;; System attributes

(defn set-system-attrs! [& args]
  (d/transact! db
               (for [[attr value] (partition 2 args)]
                 (if value
                   ;; ID 1 is a convention here.
                   [:db/add 1 attr value]
                   [:db.fn/retractAttribute 0 attr])))
  'ok)

(defn get-system-attr
  ([attr]
   (get (d/entity @db 1) attr))
  ([db attr]
   (get (d/entity db 1) attr))
  ([db attr & attrs]
   (mapv #(get-system-attr db %) (concat [attr] attrs))))

;; Tags

(defn create-tag! [name]
  (d/transact! db [{:tag/name name}]))

(defn get-tags [db]
  (d/q '[:find ?e ?tag
         :where
         [?e :tag/name ?tag]]
       db))

(defn update-tag! [[id tag]]
  'TODO)

(defn delete-tag! [[id tag]]
  'TODO)

;; Filters

(defn get-filters [db]
  (d/q '[:find ?e ?tag
         :where
         [?e :tag/name ?tag]
         [?e :tag/active true]]
       db))

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

(defn get-by-name [name]
  (d/q '[:find (pull ?file [*]) .
         :in $ ?name
         :where [?file :file/name ?name]]
       @db
       name))

(defn get-root []
  (d/q '[:find (pull ?file [*]) .
         :where [?file :file/name "dropbox"]]
       @db))

(defn get-folder-elements [file]
  (d/q '[:find [(pull ?children [:file/id :file/name]) ...]
         :in $ ?parent-id
         :where
         [?parent :file/id ?parent-id]
         [?parent :file/child ?children]]
       @db
       file))

(defn get-folder-parent [file]
  (d/q '[:find ?parent-id .
         :in $ ?child-id
         :where
         [?child :file/id ?child-id]
         [?parent :file/child ?child]
         [?parent :file/id ?parent-id]]
       @db
       file))

(defn go-to-folder! [id]
  (set-system-attrs! :current-folder id))

(defn get-current-folder []
  (get-folder-elements (get-system-attr :current-folder)))

(defn go-to-parent-folder! []
  (set-system-attrs! :current-folder
                     (get-folder-parent (get-system-attr :current-folder))))

(defn set-files! [files]
  "This function expects the data as {:name [id children]}"
  (d/transact! db [files])
  ;; id:dropbox is the root id for this remote drive
  (go-to-folder! "id:dropbox"))

;; Init

(load-db)
