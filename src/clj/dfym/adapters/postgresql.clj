(ns dfym.adapters.postgresql
  (:require [clojure.pprint :refer [pprint]]
            [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [taoensso.timbre :as log]
            ;; Db
            [clojure.java.jdbc :as jdbc]
            [postgre-types.json :refer [add-json-type add-jsonb-type]]
            [camel-snake-kebab.core :as case-shift]
            [clj-time.core :as time]
            [cheshire.core :as json]
            [buddy.hashers :as hashers]
            [digest :as digest]
            [alphabase.base58 :as base58]
            [clojure.string :as string]
            ;;-----
            [dfym.adapters :as adapters :refer [RepositoryAdapter]])
  (:import (com.mchange.v2.c3p0 ComboPooledDataSource)))

;; References:
;; SQL
;; http://clojure-doc.org/articles/ecosystem/java_jdbc/using_sql.html
;; PostgreSQL/JSON
;; https://github.com/jeffpsherman/postgres-jsonb-clojure/blob/master/src/postgres_jsonb_clojure/core.clj

(defn connect-to-production []
  (def db "")
  (println "Connecting to PostgreSQL:" db))

(defn connect-to-local []
  (def db "postgresql://localhost:5432/dfym")
  (def dbc (jdbc/get-connection db))
  (println "Connecting to PostgreSQL:" db))

;;
;; Utils
;;

(defn ->kebab-case [r] (reduce-kv #(assoc %1 (case-shift/->kebab-case %2) %3) {} r))

(defn ->snake_case [r] (reduce-kv #(assoc %1 (case-shift/->snake_case %2) %3) {} r))

(defn keyword->column [k] (case-shift/->snake_case (name k)))

;;
;; PostgreSQL Datatypes
;;

;; JSON / JSON binary
(add-json-type json/generate-string json/parse-string)
(add-jsonb-type json/generate-string json/parse-string)

;; Array
(extend-protocol clojure.java.jdbc/ISQLParameter
  clojure.lang.IPersistentVector
  (set-parameter [v ^java.sql.PreparedStatement stmt ^long i]
    (let [conn (.getConnection stmt)
          meta (.getParameterMetaData stmt)
          type-name (.getParameterTypeName meta i)]
      (if-let [elem-type (when (= (first type-name) \_) (apply str (rest type-name)))]
        (.setObject stmt i (.createArrayOf conn elem-type (to-array v)))
        (.setObject stmt i v)))))

(extend-protocol clojure.java.jdbc/IResultSetReadColumn
  java.sql.Array
  (result-set-read-column [val _ _]
    (into [] (.getArray val))))

(defn sql-array [a] (.createArrayOf dbc "varchar" (into-array String a)))

;; Time
(defn clj-time->sql-time [clj-time]
  (java.sql.Date. (.getMillis (.toInstant clj-time))))

;; LTREE
(defrecord Ltree [ltree])

(extend-protocol clojure.java.jdbc/ISQLParameter
  Ltree
  (set-parameter [self ^java.sql.PreparedStatement stmt ^long i]
    (let [conn (.getConnection stmt)
          meta (.getParameterMetaData stmt)]
      (.setObject stmt i (:ltree self) java.sql.Types/OTHER))))

;;
;; Users
;;

(defn -create-user! [{:keys [name password first-name family-name]}]
  (first
   (jdbc/query db ["
INSERT INTO users (user_name, password, first_name, family_name)
VALUES (?, ?, ?, ?)
ON CONFLICT (user_name) DO NOTHING
RETURNING id
"
                  name (hashers/derive password) first-name family-name])))

(defn get-user-by [by]
  (fn [id]
    (jdbc/query db [(format "SELECT * FROM users WHERE %s = ?" (keyword->column by)) id]
                {:identifiers #(.replace % \_ \-)
                 :result-set-fn first})))

(defn -get-user [user-map]
  (let [{:keys [id user-name]} user-map]
    (cond
      id ((get-user-by :id) id)
      user-name ((get-user-by :user-name) user-name)
      :else (throw (Exception. "No id or user-name in user data for user-get")))))

(defn update-user-by! [by]
  (fn [user-map]
    (jdbc/update! db :users (-> user-map (dissoc :id) ->snake_case)
                  [(format "%s  = ?" (keyword->column by)) (:id user-map)])))

(defn -update-user! [{:keys [id user-name] :as user-map}]
  (cond
    id ((update-user-by! :id) user-map)
    user-name ((update-user-by! :user-data) user-map)
    :else (throw (Exception. "No id or user-name in user data for user-update!"))))

;;
;; File Cache
;;

(def files-cache (atom {}))

(defn build-cache-root [root-user file-storage]
  {:fileinfo {:id root-user
              :name root-user
              :path (->Ltree root-user)}
   file-storage {:fileinfo
                 {:id file-storage
                  :dropbox-id (str "id:" file-storage)
                  :name file-storage
                  :path (->Ltree (str root-user "." file-storage))}}})

(defn prepend-cache-root [user-id storage path]
  (into [(str "user_" user-id) (name storage)] path))

(defn ensure-path! [folder-chain file-map]
  "Ensures that the folder-chain is reachable, and creates the root ones if not yet created.
If a file-map is provided, it associates the LTREE path as well.
The folder chain has the following structure:
  - user
    - storage
      - file1
      - ..."
  (if-let [parent (get-in @files-cache (drop-last folder-chain))]
    (assoc file-map
           :path
           ;; The LTree is built with the IDs, which are unique and base58
           (Ltree. (str (-> parent :fileinfo :path :ltree)
                        "."
                        (:id file-map))))
    ;; Set root if unset
    (if (= 3 (count folder-chain))
      (let [[root-user file-storage _] folder-chain]
        (swap! files-cache assoc root-user
               (build-cache-root root-user file-storage))
        (ensure-path! folder-chain file-map))
      (throw (Exception. "The requested file doens't exist and has no possible parent")))))

(defn cache-put! [folder-chain file-map]
  "Puts a new file in the cache. Expects data in the kebab case"
  (swap! files-cache update-in folder-chain
         (fn [x] (if x (assoc x :fileinfo file-map) {:fileinfo file-map}))))

;;
;; Files
;;

(defn dropbox-id->db-name [id]
  (base58/encode (.getBytes (subs id 3))))

(defn db-name->dropbox-id [id]
  (str "id:" (String. (base58/decode id))))

(defn get-file-by [by]
  (fn [user-id id]
    (jdbc/query db [(format "SELECT * FROM files WHERE user_id = ? AND %s = ?" (keyword->column by)) user-id id]
                {:identifiers #(.replace % \_ \-)
                 :result-set-fn first})))

(defn -get-file [user-id file-id]
  ((get-file-by :dropbox-id) user-id file-id))

(defn -create-file! [user-id {:keys [path-display path-lower name folder? storage id size rev] :as file-map}]
  ;; We must use path-lower for building the tree, because path display is inconsistent
  (let [folder-chain (prepend-cache-root user-id storage
                                         (->> (string/split path-lower #"/")
                                              (filter not-empty)))
        entry (ensure-path! folder-chain
                            {:id (case storage
                                   ;; We use the Dropbox ID as unique, but it might be different
                                   (:dropbox) (dropbox-id->db-name id))
                             :user_id user-id
                             :name name
                             :path_display path-display
                             :is_folder folder?
                             :dropbox_id id
                             :size size})]
    (cache-put! folder-chain (->kebab-case entry))
    (jdbc/insert! db :files entry)))

(defn -get-files [user-id storage]
  (when-not (number? user-id) (throw (Exception. "-get-files: user Id should be a number")))
  (let [root-path [(str "user_" user-id) storage]]
    {storage ; Return it with the {storage tree} format for compatibility with tree algorithms
     (or (get-in @files-cache root-path)
         (do (let [[root-user file-storage] (prepend-cache-root user-id storage [])]
               (swap! files-cache assoc root-user
                      (build-cache-root root-user file-storage)))
             (jdbc/query db [(format "SELECT * FROM files WHERE '%s' @> path"
                                     (str "user_" user-id "." storage))]
                         {:identifiers #(.replace % \_ \-)
                          :row-fn #(let [path (prepend-cache-root
                                               user-id
                                               storage
                                               (filter not-empty
                                                       (-> % :path-display (string/split #"/"))))]
                                     (cache-put! path %)
                                     %)})
             (get-in @files-cache root-path)
             ))}))

(defn delete-user-files! [user-id]
  (jdbc/delete! db :files ["user_id = ? CASCADE" user-id]))

;;
;; Tags
;;

(defn -get-tags [user-id]
  (jdbc/with-db-transaction
    [tr db]
    [(jdbc/query tr ["SELECT id, name FROM tags WHERE user_id = ?" user-id])
     (jdbc/query tr ["SELECT file_id, tag_id FROM files_tags WHERE files_tags.user_id = ?" user-id])]))

(defn -create-tag! [user-id tag]
  (jdbc/with-db-transaction
    [tr db]

    ;; HERE!!! DELETE TAGS AND FILE TAGS
    ;; The client sends transactions for the server to execute and keep in sync. The client then
    ;; is able to freeze the last data (save it). This happens in the webworker.

    ;; (doseq [tag tags]
    ;;   (let [tag (assoc tag :user_id user-id)
    ;;         result (jdbc/update! tr :tags tag
    ;;                              ["name = ? AND user_id = ?" (:name tag) (:user_id tag)])]
    ;;     (when (zero? (first result))
    ;;       (println "inserting")
    ;;       (jdbc/insert! tr :tags tag))))
    ;; (doseq [file-tag files-tags]
    ;;   (let [file-tag (-> file-tag
    ;;                      ->kebab-case
    ;;                      (assoc :user_id user-id))
    ;;         result 'TODO #_(jdbc/update! tr :files_tags file-tag
    ;;                              ["name = ? AND user_id = ?" (:name tag) (:user_id tag)])]
    ;;     (when (zero? (first result))
    ;;       (jdbc/insert! tr :files_tags file-tag))))
    ))

(defn -update-tag! [user-id tag]
  'TODO)

(defn -delete-tag! [user-id tag]
  'TODO)

(defn -link-tag! [user-id file-id tag]
  'TODO)

(defn -unlink-tag! [user-id file-id tag]
  'TODO)

;;
;; Adapter
;;

(def db-spec
  {:classname "org.postgresql.Driver"
   :subprotocol "postgresql"
   :subname "//localhost:5432/dfym"
   ;;:user "myaccount"
   ;;:password "secret"
   })

(defn pool
  [spec]
  (let [cpds (doto (ComboPooledDataSource.)
               (.setDriverClass (:classname spec))
               (.setJdbcUrl (str "jdbc:" (:subprotocol spec) ":" (:subname spec)))
               ;;(.setUser (:user spec))
               ;;(.setPassword (:password spec))
               ;; expire excess connections after 30 minutes of inactivity:
               (.setMaxIdleTimeExcessConnections (* 30 60))
               ;; expire connections after 3 hours of inactivity:
               (.setMaxIdleTime (* 3 60 60)))]
    {:datasource cpds}))

(defrecord PostgreSqlAdapter []
  component/Lifecycle

  (start [component]
    (def db (or (env :database-url)
                ;;"postgres://zapxeakmarafti:O9-vM29dzvG0g2Qo505pTMJTkg@ec2-54-75-233-92.eu-west-1.compute.amazonaws.com:5432/d1heam857nkip0?sslmode=require"
                "postgresql://localhost:5432/dfym"))
    (def dbc (jdbc/get-connection db))
    (println "Connecting to PostgreSQL:" db)
    component)
  (stop [component] component)

  RepositoryAdapter

  ;; Users
  (create-user! [self user-map]
    (-create-user! user-map))
  (get-user [self user-map]
    (-get-user user-map))
  (update-user! [self user-map]
    (-update-user! user-map))
  ;; Files
  (create-file! [self user-id file-map]
    (-create-file! user-id file-map))
  (get-files [self user-id]
    (-get-files user-id "dropbox"))
  (get-file [self user-id file-id]
    (-get-file user-id file-id))
  ;; Tags
  (get-tags [self user-id]
    (-get-tags user-id))
  (create-tag! [self user-id tag]
    (-create-tag! user-id tag))
  (update-tag! [self user-id tag]
    (-update-tag! user-id tag))
  (delete-tag! [self user-id tag]
    (-delete-tag! user-id tag))
  ;; Tag links
  (link-tag! [self user-id file-id tag]
    (-link-tag! user-id file-id tag))
  (unlink-tag! [self user-id file-id tag]
    (-unlink-tag! user-id file-id tag)))

;;
;; Development utilities
;;

(defn reset-database!!! []
  (try
    (jdbc/db-do-commands db ["DROP TABLE IF EXISTS files CASCADE;"
                             "DROP TABLE IF EXISTS users CASCADE;"
                             "DROP TABLE IF EXISTS files_tags CASCADE;"
                             "DROP TABLE IF EXISTS tags CASCADE;"
                             "DROP INDEX IF EXISTS files_path_idx;"
                             "
CREATE TABLE users (
  id              SERIAL PRIMARY KEY,
  created         TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  user_name       VARCHAR(128) NOT NULL UNIQUE,
  password        VARCHAR(256) NOT NULL,
  first_name      VARCHAR(256),
  family_name     VARCHAR(256),
  dropbox_token   VARCHAR(256),
  dropbox_synced  BOOL NOT NULL DEFAULT false
)
"
                             "
CREATE TABLE files (
  id              VARCHAR(256) PRIMARY KEY,
  user_id         INTEGER REFERENCES users(id) ON DELETE CASCADE NOT NULL,
  path            LTREE,
  name            TEXT NOT NULL,
  path_display    TEXT NOT NULL,
  is_folder       BOOL NOT NULL,
  dropbox_id      VARCHAR(256) NOT NULL,
  size            BIGINT
)
"
                             "
CREATE INDEX files_path_idx ON files USING GIST (path);
"
                             "
CREATE TABLE tags (
  id              SERIAL PRIMARY KEY,
  name            VARCHAR(1024),
  user_id         INTEGER REFERENCES users(id) ON DELETE CASCADE NOT NULL,
  UNIQUE (name, user_id)
)
"
                             "
CREATE INDEX ON tags(name);
"
                             "
CREATE TABLE files_tags (
  file_id         VARCHAR(256) REFERENCES files(id) ON DELETE CASCADE NOT NULL,
  tag_id          INTEGER REFERENCES tags(id) ON DELETE CASCADE NOT NULL,
  user_id         INTEGER REFERENCES users(id) ON DELETE CASCADE NOT NULL,
  UNIQUE (file_id, tag_id)
)
"
                             ])
    (catch Exception e (or e (.getNextException e))
           (pprint e)))
  ;;
  ;; Init data for development
  ;;
  (let [repo (PostgreSqlAdapter.)]
    (adapters/create-user! repo {:name "Thor" :password "alvaro"})))

;; (require '[dfym.adapters.postgresql :as db])
