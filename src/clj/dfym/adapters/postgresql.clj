(ns dfym.adapters.postgresql
  (:require [clojure.pprint :refer [pprint]]
            [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [taoensso.timbre :as log]
            ;; Db
            [clojure.java.jdbc :as sql]
            [postgre-types.json :refer [add-json-type add-jsonb-type]]
            [camel-snake-kebab.core :as case-shift]
            [clj-time.core :as time]
            [cheshire.core :as json]
            [buddy.hashers :as hashers]
            [digest :as digest]
            [alphabase.base58 :as base58]
            [clojure.string :as string]
            ;;-----
            [dfym.adapters :as adapters :refer [RepositoryAdapter]]))

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
  (def dbc (sql/get-connection db))
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

(defn user-get-by [by]
  (fn [id]
    (->kebab-case
     (first
      (sql/query db [(format "SELECT * FROM users WHERE %s = ?" (keyword->column by)) id])))))

(defn -user-get [user-map]
  (let [{:keys [id user-name]} user-map]
    (cond
      id ((user-get-by :id) id)
      user-name ((user-get-by :user-name) user-name)
      :else (throw (Exception. "No id or user-name in user data for user-get")))))

(defn -user-create! [{:keys [name password first-name family-name]}]
  (first
   (sql/query db ["
INSERT INTO users (user_name, password, first_name, family_name)
VALUES (?, ?, ?, ?)
ON CONFLICT (user_name) DO NOTHING
RETURNING id
"
                  name (hashers/derive password) first-name family-name])))

(defn user-update-by! [by]
  (fn [user-map]
    (sql/update! db :users (-> user-map (dissoc :id) ->snake_case)
                 [(format "%s  = ?" (keyword->column by)) (:id user-map)])))

(defn -user-update! [{:keys [id user-name] :as user-map}]
  (cond
    id ((user-update-by! :id) user-map)
    user-name ((user-update-by! :user-data) user-map)
    :else (throw (Exception. "No id or user-name in user data for user-update!"))))

;;
;; File Cache
;;

(def files-cache (atom {}))

(defn build-cache-root [root-user file-storage]
  {:fileinfo {:id root-user
              :path (->Ltree root-user)}
   file-storage {:fileinfo
                 {:id file-storage
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
  (swap! files-cache assoc-in folder-chain
         {:fileinfo file-map}))

;;
;; Files
;;

(defn dropbox-id->db-name [id]
  (base58/encode (.getBytes (subs id 3))))

(defn db-name->dropbox-id [id]
  (str "id:" (String. (base58/decode id))))

(defn -files-create! [user-id {:keys [path-display path-lower name folder? storage id size rev] :as file-map}]
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
    (sql/insert! db :files entry)))

(defn delete-all-files!!! []
  (sql/delete! db :files []))

(defn -files-get [user-id storage]
  (when-not (number? user-id) (throw (Exception. "-files-get: user Id should be a number")))
  (let [root-path [(str "user_" user-id) storage]]
    (or (get-in @files-cache root-path)
        (do (let [[root-user file-storage] (prepend-cache-root user-id storage [])]
              (swap! files-cache assoc root-user
                     (build-cache-root root-user file-storage)))
            (sql/query db [(format "SELECT * FROM files WHERE '%s' @> path"
                                   (str "user_" user-id "." storage))]
                       {:row-fn #(let [entry (->kebab-case %)
                                       path (prepend-cache-root
                                             user-id
                                             storage
                                             (filter not-empty (-> entry
                                                                   :path-display
                                                                   (string/split #"/"))))]
                                   (cache-put! path entry)
                                   entry)})
            (get-in @files-cache root-path)))))

(defn -files-tag! [user-id files tag]
  'TODO)

(defn -files-update! [user-id file-map]
  'TODO)

;;
;; Adapter
;;

(defn single-result [res] (-> res first vals first))

(defrecord PostgreSqlAdapter []
  component/Lifecycle

  (start [component]
    (def db (or (env :database-url)
                ;;"postgres://zapxeakmarafti:O9-vM29dzvG0g2Qo505pTMJTkg@ec2-54-75-233-92.eu-west-1.compute.amazonaws.com:5432/d1heam857nkip0?sslmode=require"
                "postgresql://localhost:5432/dfym"))
    (def dbc (sql/get-connection db))
    (println "Connecting to PostgreSQL:" db)
    component)
  (stop [component] component)

  RepositoryAdapter

  (user-get [self user-map]
    (-user-get user-map))
  (user-create! [self user-map]
    (-user-create! user-map))
  (user-update! [self user-map]
    (-user-update! user-map))
  (files-create! [self user-id file]
    (-files-create! user-id file))
  (files-get [self user-id]
    (-files-get user-id "dropbox"))
  (files-tag! [self user-id files tag]
    (-files-tag! user-id files tag))
  (files-update! [self user-id file-map]
    (-files-update! user-id file-map)))

;;
;; Development utilities
;;

(defn reset-database!!! []
  (try
    (sql/db-do-commands db ["DROP TABLE IF EXISTS files CASCADE;"
                            "DROP TABLE IF EXISTS users CASCADE;"
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
  user_id         INTEGER REFERENCES users(id) NOT NULL,
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
                            ])
    (catch Exception e (or e (.getNextException e))
           (pprint e)))
  ;;
  ;; Init data for development
  ;;
  (let [repo (PostgreSqlAdapter.)]
   (adapters/user-create! repo {:name "Thor" :password "alvaro"})))

;; (require '[dfym.adapters.postgresql :as db])
