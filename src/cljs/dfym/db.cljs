(ns dfym.db
  (:require
   [datascript.transit :as dt]
   [datascript.core :as d]
   ;;-----
   [dfym.fixtures :as fixtures]
   [dfym.utils :as utils :refer [log*]]))

;; Some references
;; https://github.com/stathissideris/datascript-dom/blob/master/src/datascript_dom/core.clj
;; https://github.com/tonsky/datascript-todo/blob/gh-pages/src/datascript_todo/core.cljs
;; https://docs.datomic.com/on-prem/query.html

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

(defn get-user [db]
  (get-system-attr db :user))

;; Tags

(defn get-tags [db]
  (d/q '[:find ?e ?tag
         :where
         [?e :tag/name ?tag]]
       db))

(defn get-tag-name [db eid]
  (d/pull db [:tag/name] eid))

(defn create-tag! [name]
  (d/transact! db [{:tag/name name}])
  true)

(defn set-tags! [tags files-tags]
  "Sets the tags received from the server"
  ;; This function needs to convert the tags provided as a list of indexes external to this db
  (let [server-id-tag-tuples (for [{:keys [id name]} tags] [id name])
        tag-server-id->name (into {} server-id-tag-tuples)]
    ;; Insert the tags, then extract them to obtain the internal IDs
    (d/transact! db (for [[id name] server-id-tag-tuples] {:tag/name name}))
    (d/transact! db (for [{:keys [file_id tag_id]} files-tags]
                      {:tag/name (get tag-server-id->name tag_id)
                       :tag/file (d/q '[:find ?e .
                                        :in $ ?file-id
                                        :where
                                        [?e :file/id ?file-id]]
                                      @db file_id)}))))

(defn update-tag! [[id tag]]
  'TODO)

(defn delete-tag! [id]
  'TODO)

(defn attach-tag! [file-id tag-id]
  (d/transact! db [{:db/id tag-id :tag/file file-id}])
  true)

(defn detach-tag! [file-id tag]
  'TODO)

;; Files

(defn push-current-folder []
  (set-system-attrs! :previous-current-folder (get-system-attr :current-folder))
  (set-system-attrs! :current-folder nil))

(defn load-current-folder []
  (set-system-attrs! :current-folder (get-system-attr :previous-current-folder))
  (set-system-attrs! :previous-current-folder nil))

(defn get-root [db]
  (d/q '[:find (pull ?file [:db/id :file/id :file/name]) .
         :where [?file :file/name "dropbox"]]
       db))

(defn get-file-info [db eid]
  (d/pull db [:file/id :file/name] eid))

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

(defn get-search-files [db search-string]
  (d/q '[:find ?file ?file-id ?file-name ?folder?
         :in $ ?match
         :where
         [?file :file/id ?file-id]
         [?file :file/name ?file-name]
         [(?match ?file-name)]
         [(get-else $ ?file :file/child false) ?folder?]]
       db
       (fn [s] (re-find (re-pattern search-string) s))))

(defn get-current-folder [db]
  (first (get-system-attr :current-folder)))

(def search-filter (atom nil))

(defn set-search-filter! [db search-string]
  (reset! search-filter search-string)
  (when-not (get-system-attr :previous-current-folder)
    (push-current-folder)))

(defn unset-search-filter! [db]
  (reset! search-filter nil)
  (log* "SEARCH FILTER" @search-filter)
  (load-current-folder))

(defn get-display-elements [db]
  ;; If we don't have a search string and the current folder was disabled, it means
  ;; we are abck to regular folder navigation
  (let [current-folder (get-current-folder db)]
    (cond
      ;; Regular folder navigation
      current-folder
      (get-folder-elements db current-folder)
      ;; Search mode
      (not-empty @search-filter)
      (get-search-files db @search-filter)
      ;; Filter mode
      :else
      (get-filtered-files db))))

(defn go-to-parent-folder! [db]
  (set-system-attrs! :current-folder
                     (not-empty (drop 1 (get-system-attr :current-folder)))))

(defn is-top-folder? [db]
  (let [current-folder (get-system-attr :current-folder)]
    (or (not current-folder)
        (= (first current-folder) "id:dropbox"))))

(defn set-files! [files]
  "Sets the files received from the server.
   This function expects the data as {:name [id children]}"
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
    (push-current-folder))
  (d/transact! db [{:filter/tag tag}])
  true)

(defn remove-filter-tag! [tag-id]
  (d/transact! db [[:db.fn/retractEntity tag-id]])
  (if (not-empty (get-filter-tags @db))
    (go-to-folder! (last (get-system-attr :current-folder)))
    ;; When we remove the last filter tag
    (load-current-folder))
  true)

;;
;; Main init function
;;

(defn init! []
  "Initialize all the resources required by the DB. Call only once."
  (def db (d/create-conn schema))
  (load-db!))
