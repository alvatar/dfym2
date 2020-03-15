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
(defrecord Ltree [path])

(extend-protocol clojure.java.jdbc/ISQLParameter
  Ltree
  (set-parameter [self ^java.sql.PreparedStatement stmt ^long i]
    (let [conn (.getConnection stmt)
          meta (.getParameterMetaData stmt)]
      (.setObject stmt i (:path self) java.sql.Types/OTHER))))

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
;; Files
;;

(defn- create-unique-id [tr file-name]
  (let [hash-id (digest/sha-512 file-name)
        id-exists? (fn [id]
                     (-> (sql/query tr ["SELECT COUNT(*) FROM files WHERE id = ?" id])
                         first :count pos-int?))]
    (loop [try-hash-id hash-id
           n 1]
      (println (id-exists? try-hash-id))
      (if (id-exists? try-hash-id)
        (recur (str hash-id "_" n)
               (inc n))
        try-hash-id))))

(defn- find-id [tr file-name]
  (let [hash-id (digest/sha-512 file-name)
        get-by-id (fn [id]
                     (-> (sql/query tr ["SELECT name FROM files WHERE id = ?" id])
                         first :name))]
    (loop [try-hash-id hash-id
           n 1]
      (cond (= n 1000) (throw (Exception. "Too many iterations finding Id. This means that the file-id could not be determined."))
            (= (get-by-id try-hash-id) file-name)
            (recur (str hash-id "_" n)
                   (inc n))
            :else  try-hash-id))))

(defn -files-create! [user-id {:keys [path name folder? id size rev] :as file-map}]
  (try
    (sql/with-db-transaction
      [tr db]
      (let [unique-id (create-unique-id tr name)]
        (println "name: " name " - Hash: " unique-id " - id: " id)
        ;;(pprint file-map)
        (println (clojure.string/join "." (cons (str "USER_" user-id)
                                                (->> (clojure.string/split path #"/")
                                                     (filter not-empty)
                                                     ;;drop-last
                                                     (map (partial find-id tr))))))
        (sql/insert! tr :files
                     {:id unique-id
                      :user_id user-id
                      :path (Ltree.
                             (clojure.string/join "." (cons (str "USER_" user-id)
                                                            (->> (clojure.string/split path #"/")
                                                                 (filter not-empty)
                                                                 ;;drop-last
                                                                 (map (partial find-id tr))))))
                      :name name
                      :path_display path
                      :is_folder folder?
                      :dropbox_id id
                      :size size
                      :rev rev}))
      #_(sql/query tr ["
INSERT INTO files (id, user_id, path, name, path_display, is_folder, dropbox_id, size, rev)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                       (create-unique-id tr name)
                       user-id
                       (Ltree.
                        (clojure.string/join "." (->> (clojure.string/split path #"/")
                                                      (filter not-empty)
                                                      drop-last
                                                      (map (partial find-id tr)))))
                       name
                       path
                       folder?
                       id
                       size
                       rev]))
    (catch Exception e (pprint e) (pprint (.getNextException e)) (throw e))))

(defn delete-all-files!!! []
  (sql/delete! db :files []))

(defn -files-get [user-id filters]
  'TODO)

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
  (files-get [self user-id filters]
    (-files-get user-id filters))
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
  size            INTEGER,
  rev             CHAR(14)
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
