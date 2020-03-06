(ns dfym.adapters.postgresql
  (:require [clojure.pprint :refer [pprint]]
            [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [taoensso.timbre :as log]
            ;; Db
            [clojure.java.jdbc :as sql]
            [postgre-types.json :refer [add-json-type add-jsonb-type]]
            ;;[camel-snake-kebab.core :as case-shift]
            [clj-time.core :as time]
            [cheshire.core :as json]
            [buddy.hashers :as hashers]
            ;;-----
            [dfym.adapters :refer [RepositoryAdapter]]))


(defrecord PostgreSqlAdapter []

  component/Lifecycle
  (start [component] component)
  (stop [component] component)

  RepositoryAdapter
  (user-get [self user-id]
    "RESULT Get user data")
  (user-get-password [self user-id]
    "RESULT user get pass")
  (user-update! [self user-data]
    "RESUlT Update user data")
  (files-get [self user-id filters]
    "RESULT Get user files listing")
  (files-tag! [self user-id files tag]
    "RESULT Files tag")
  (files-update! [self user-id files]
    "REsulT Update user files listing with original source"))


;; (add-json-type json/generate-string json/parse-string)
;; (add-jsonb-type json/generate-string json/parse-string)

;; ;; References:
;; ;; SQL
;; ;; http://clojure-doc.org/articles/ecosystem/java_jdbc/using_sql.html
;; ;; PostgreSQL/JSON
;; ;; https://github.com/jeffpsherman/postgres-jsonb-clojure/blob/master/src/postgres_jsonb_clojure/core.clj

;; (def db (or (env :database-url)
;;             ;;"postgres://zapxeakmarafti:O9-vM29dzvG0g2Qo505pTMJTkg@ec2-54-75-233-92.eu-west-1.compute.amazonaws.com:5432/d1heam857nkip0?sslmode=require"
;;             "postgresql://localhost:5432/dfym"))
;; (def dbc (sql/get-connection db))

;; (println "Connecting to PostgreSQL:" db)

;; (defn connect-to-production []
;;   (def db "")
;;   (println "Connecting to PostgreSQL:" db))

;; (defn connect-to-local []
;;   (def db "postgresql://localhost:5432/z03-dev")
;;   (def dbc (sql/get-connection db))
;;   (println "Connecting to PostgreSQL:" db))

;; ;;
;; ;; Utils
;; ;;

;; (defn ->kebab-case [r] (reduce-kv #(assoc %1 (case-shift/->kebab-case %2) %3) {} r))

;; (defn ->snake_case [r] (reduce-kv #(assoc %1 (case-shift/->snake_case %2) %3) {} r))

;; (defn keyword->column [k] (case-shift/->snake_case (name k)))

;; ;;
;; ;; Datatypes
;; ;;

;; (extend-protocol clojure.java.jdbc/ISQLParameter
;;   clojure.lang.IPersistentVector
;;   (set-parameter [v ^java.sql.PreparedStatement stmt ^long i]
;;     (let [conn (.getConnection stmt)
;;           meta (.getParameterMetaData stmt)
;;           type-name (.getParameterTypeName meta i)]
;;       (if-let [elem-type (when (= (first type-name) \_) (apply str (rest type-name)))]
;;         (.setObject stmt i (.createArrayOf conn elem-type (to-array v)))
;;         (.setObject stmt i v)))))

;; (extend-protocol clojure.java.jdbc/IResultSetReadColumn
;;   java.sql.Array
;;   (result-set-read-column [val _ _]
;;     (into [] (.getArray val))))

;; (defn sql-array [a] (.createArrayOf dbc "varchar" (into-array String a)))

;; (defn clj-time->sql-time [clj-time]
;;  (java.sql.Date. (.getMillis (.toInstant clj-time))))

;; ;;
;; ;; Users
;; ;;

;; (defn user-create! [user-name pass-plaintext first-name family-name]
;;   (sql/insert! db "users"
;;                ["user_name" "password" "first_name" "family_name"]
;;                [user-name (hashers/derive pass-plaintext) first-name family-name]))

;; (defn user-get-by [by id]
;;   (->kebab-case
;;    (first
;;     (sql/query db [(format "SELECT * FROM users WHERE %s = ?" (keyword->column by)) id]))))

;; (defn user-authenticate [user-name pass-plaintext]
;;   (try
;;     (let [user (user-get-by :user-name user-name)]
;;       (and (hashers/check pass-plaintext (:password user))
;;            user))
;;     (catch Exception e
;;       (log/debugf "Exception authenticating user: %s" (with-out-str (pprint e))))))

;; ;;
;; ;; Projects
;; ;;

;; (defn project-create! [user-id git-repo name description]
;;   (sql/insert! db "projects"
;;                ["user_id" "git_repo" "name" "description"]
;;                [user-id git-repo name description]))

;; (defn project-find-by [by id]
;;   (mapv
;;    ->kebab-case
;;    (sql/query db [(format "SELECT * FROM projects WHERE %s = ?" (keyword->column by)) id])))

;; (def project-get-by (comp first project-find-by))

;; (defn project-update! [by id vals]
;;   (sql/update! db :projects (-> vals
;;                                 (dissoc :id)
;;                                 ->snake_case)
;;                [(format "%s  = ?" (name by)) id]))

;; ;;
;; ;; Development utilities
;; ;;

;; (defn reset-database!!! []
;;   (try
;;     (sql/db-do-commands db ["DROP TABLE IF EXISTS users CASCADE;"
;;                             "DROP TABLE IF EXISTS projects CASCADE;"
;;                             "
;; CREATE TABLE users (
;;   id              SERIAL PRIMARY KEY,
;;   created         TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
;;   user_name       VARCHAR(128) NOT NULL UNIQUE,
;;   password        VARCHAR(256) NOT NULL,
;;   first_name      VARCHAR(256) NOT NULL,
;;   family_name     VARCHAR(256) NOT NULL
;; )
;; "
;;                             ;; Revision max name is 255 chars
;;                             "
;; CREATE TABLE projects (
;;   id              SERIAL PRIMARY KEY,
;;   user_id         INTEGER REFERENCES users(id) NOT NULL,
;;   created         TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
;;   name            VARCHAR(1024) NOT NULL,
;;   description     VARCHAR(2048) NOT NULL,
;;   latest_commits  VARCHAR(255) ARRAY [5],
;;   git_repo        TEXT
;; )
;; "
;;                             ])
;;     (catch Exception e (or e (.getNextException e))))
;;   ;;
;;   ;; Init data for development
;;   ;;
;;   (user-create! "thor" "alvaro" "Thor" "Quator")
;;   (project-create! 1 "/data/Dropbox/projects/z03-tester" "test-project" "Test Project for Metapen")
;;   (project-update! :name "ios7-templates" {:latest-commits ["[master] Final version" "Mockup v4" "Mockup v3" "Mockup v2" "Mockup v1"]})
;;   (project-create! 1 "/data/Dropbox/projects/cointrust" "rick-interstellar-enterprises-branding" "Rick Interstellar Enterprises: branding")
;;   (project-update! :name "rick-interstellar-enterprises-branding" {:latest-commits ["[master] Branding v1" "Simplified logo; reduced number of colors"]}))
