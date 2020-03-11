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

(add-json-type json/generate-string json/parse-string)
(add-jsonb-type json/generate-string json/parse-string)

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

(defn clj-time->sql-time [clj-time]
 (java.sql.Date. (.getMillis (.toInstant clj-time))))

;;
;; Users
;;

(defn user-get-by [by]
  (fn [id]
    (->kebab-case
     (first
      (sql/query db [(format "SELECT * FROM users WHERE %s = ?" (keyword->column by)) id])))))


(defn user-update-by! [by]
  (fn [user-map]
    (sql/update! db :users (-> user-map (dissoc :id) ->snake_case)
                 [(format "%s  = ?" (case-shift/->snake_case by)) (:id user-map)])))

;;
;; Adapter
;;

(defn single-result [res] (-> res first vals first))

(defrecord PostgreSqlAdapter []
  ;;
  ;; Component
  ;;
  component/Lifecycle

  (start [component]
    (def db (or (env :database-url)
                ;;"postgres://zapxeakmarafti:O9-vM29dzvG0g2Qo505pTMJTkg@ec2-54-75-233-92.eu-west-1.compute.amazonaws.com:5432/d1heam857nkip0?sslmode=require"
                "postgresql://localhost:5432/dfym"))
    (def dbc (sql/get-connection db))
    (println "Connecting to PostgreSQL:" db)
    component)

  (stop [component] component)

  ;;
  ;; Repository Adapter
  ;;
  RepositoryAdapter

  (user-get [self user-name]
    ((user-get-by :user-name) user-name))

  (user-get-password [self user-name]
    (single-result (sql/query db ["SELECT password FROM users WHERE user_name = ?" user-name])))

  (user-create! [self user-data]
    (let [{:keys [name password first-name family-name]} user-data]
      (first
       (sql/query db ["
INSERT INTO users (user_name, password, first_name, family_name)
VALUES (?, ?, ?, ?)
ON CONFLICT (user_name) DO NOTHING
RETURNING id
"
                      name (hashers/derive password) first-name family-name]))))

  (user-update! [self user-data]
    (let [{:keys [id user-name]} user-data]
      (cond
        id ((user-update-by! :id) user-data)
        user-name ((user-update-by! :user-data) user-data)
        :else (throw (Exception. "No id or user-name in user data for user-update!")))))

  (files-get [self user-id filters]
    'TODO)

  (files-tag! [self user-id files tag]
    'TODO)

  (files-update! [self user-id files]
    'TODO))

;;
;; Development utilities
;;

(defn reset-database!!! []
  (try
    (sql/db-do-commands db ["DROP TABLE IF EXISTS users CASCADE;"
                            "
CREATE TABLE users (
  id              SERIAL PRIMARY KEY,
  created         TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  user_name       VARCHAR(128) NOT NULL UNIQUE,
  password        VARCHAR(256) NOT NULL,
  first_name      VARCHAR(256),
  family_name     VARCHAR(256)
)
"
                            ])
    (catch Exception e (or e (.getNextException e))))
  ;;
  ;; Init data for development
  ;;
  (let [repo (PostgreSqlAdapter.)]
   (adapters/user-create! repo {:name "Thor" :password "alvaro"})))

;; (require '[dfym.adapters.postgresql :as db])

(defn test-fn []
  (adapters/user-get-password (PostgreSqlAdapter.) 1))
