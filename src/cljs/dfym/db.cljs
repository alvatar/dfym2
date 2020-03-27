(ns dfym.db
  (:require
   [datascript.transit :as dt]
   [datascript.core :as d]
   ;;-----
   [dfym.fixtures :as fixtures]
   [dfym.utils :as utils :refer [log*]]))

(declare db)

(def ^:const db-name "dfym/db")
(def ^:const schema {:file/child {:db/valueType :db.type/ref
                                  :db/cardinality :db.cardinality/many}
                     :file/id {:db/unique :db.unique/identity}
                     :tag/name {:db/unique :db.unique/identity}
                     :tag/file {:db/valueType :db.type/ref
                                :db/cardinality :db.cardinality/many}
                     :filter/tag {:db/valueType :db.type/ref
                                  :db/unique :db.unique/identity}
                     ;;:file/tags {:db/cardinality :db.cardinality/many}
                     })

(defn db->string [db] (dt/write-transit-str db))
(defn string->db [s] (dt/read-transit-str s))

(defn persist-db! [db]
  (log* "Persisting DB...")
  (js/ldb.set db-name (db->string db)))

(defn load-db! []
  (log* "Loading DB...")
  (js/ldb.get db-name (fn [stored] (when stored
                                     (let [stored-db (string->db stored)]
                                       (when-not (:schema stored-db) (log* "SCHEMA NOT FOUND!!"))
                                       (reset! db stored-db))))))

(defn clear-db! []
  (js/ldb.set db-name nil))

;;
;; Data functions
;; Note that queries are passed a DB, but transactions don't. We transact always over the last
;; DB version. This is conceptual, even though we are not using any history support ATM.
;;

;; System attributes

(defn set-system-attrs! [& args]
  (d/transact! db
               (for [[attr value] (partition 2 args)]
                 (if (nil? value)
                   ;; ID 1 is a convention here.
                   [:db.fn/retractAttribute 1 attr]
                   [:db/add 1 attr value])))
  true)

(defn get-system-attr
  ([attr]
   (get (d/entity @db 1) attr))
  ([db attr]
   (get (d/entity db 1) attr))
  ([db attr & attrs]
   (mapv #(get-system-attr db %) (concat [attr] attrs))))

;; User

(defn get-user [db] (get-system-attr db :user))

;; Tags

(defn create-tag! [name]
  (d/transact! db [{:tag/name name}])
  true)

(defn get-tags [db]
  (d/q '[:find ?e ?tag
         :where
         [?e :tag/name ?tag]]
       db))

(defn update-tag! [[id tag]]
  'TODO)

(defn delete-tag! [id]
  'TODO)

(defn link-tag! [file-id tag-id]
  (d/transact! db [{:db/id tag-id :tag/file file-id}])
  true)

(defn unlink-tag! [file-id tag]
  'TODO)

;; Files

(defn get-root [db]
  (d/q '[:find (pull ?file [:db/id :file/id :file/name]) .
         :where [?file :file/name "dropbox"]]
       db))

(defn get-folder-elements [db file]
  ;;[(pull ?children [:db/id :file/id :file/name]) ...]
  (d/q '[:find ?child ?child-id ?child-name ?folder?
         :in $ ?parent-id
         :where
         [?parent :file/id ?parent-id]
         [?parent :file/child ?child]
         [?child :file/id ?child-id]
         [?child :file/name ?child-name]
         [(get-else $ ?child :file/child false) ?folder?]]
       db
       file))

(defn get-folder-parent [db file]
  (d/q '[:find ?parent-id .
         :in $ ?child-id
         :where
         [?child :file/id ?child-id]
         [?parent :file/child ?child]
         [?parent :file/id ?parent-id]]
       db
       file))

(defn go-to-folder! [id]
  (set-system-attrs! :current-folder
                     (concat (list id) (get-system-attr :current-folder))))

(defn get-filtered-files [db]
  (d/q '[:find ?file ?file-id ?file-name ?folder?
         :where
         [_ :filter/tag ?te]
         [?te :tag/file ?file]
         [?file :file/id ?file-id]
         [?file :file/name ?file-name]
         [(get-else $ ?file :file/child false) ?folder?]]
       db))

(defn get-current-folder [db]
  (first (get-system-attr :current-folder)))

(defn get-current-folder-elements [db]
  (let [current-folder (get-current-folder db)]
    (if current-folder
      (get-folder-elements db current-folder)
      (get-filtered-files db))))

(defn go-to-parent-folder! [db]
  (set-system-attrs! :current-folder
                     (not-empty (drop 1 (get-system-attr :current-folder)))))

(defn is-top-folder? [db]
  (let [current-folder (get-system-attr :current-folder)]
    (or (not current-folder)
        (= (first current-folder) "id:dropbox"))))

(defn set-files! [files]
  "This function expects the data as {:name [id children]}"
  (d/transact! db [files])
  ;; id:dropbox is the root id for this remote drive
  (go-to-folder! "id:dropbox"))

;; Filters Tags

(defn get-filter-tags [db]
  (d/q '[:find ?fe ?te ?tag
         :where
         [?fe :filter/tag ?te]
         [?te :tag/name ?tag]]
       db))

(defn add-filter-tag! [tag]
  (when-not (get-system-attr :previous-current-folder)
    (set-system-attrs! :previous-current-folder (get-system-attr :current-folder))
    (set-system-attrs! :current-folder nil))
  (d/transact! db [{:filter/tag tag}])
  true)

(defn remove-filter-tag! [tag-id]
  (d/transact! db [[:db.fn/retractEntity tag-id]])
  (if (not-empty (get-filter-tags @db))
    (go-to-folder! (last (get-system-attr :current-folder)))
    ;; When we remove the last filter tag
    (do (set-system-attrs! :current-folder (get-system-attr :previous-current-folder))
        (set-system-attrs! :previous-current-folder nil)))
  true)

;;
;; Main init function
;;

(defn init! []
  "Initialize all the resources required by the DB. Call only once."
  (def db (d/create-conn schema))
  (load-db!)
  ;; Logging
  (d/listen! db :log
             (fn [tx-report]
               (let [tx-id  (get-in tx-report [:tempids :db/current-tx])
                     datoms (:tx-data tx-report)]
                 (log* "TRANSACTIONS: " datoms)))))

