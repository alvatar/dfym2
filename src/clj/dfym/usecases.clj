(ns dfym.usecases
  (:require [clojure.pprint :refer [pprint]]
            [environ.core :refer [env]]
            [com.stuartsierra.component :as component]
            [buddy.hashers :as hashers]
            ;; -----
            [dfym.usecases.html :as html]
            [dfym.adapters :as adapters]))

;;
;; Use Cases
;;

(defrecord UseCases [repository file-storage]
  component/Lifecycle
  (start [component]
    (println "Starting Use Cases...")
    (def repository (:repository component))
    (def file-storage (:files-storage component))
    component)
  (stop [component]
    component))

(defn user-create [user-id user-name user-password]
  (or (adapters/user-update! repository
                             {:user
                              {:id user-id
                               :name user-name
                               :password (hashers/derive user-password {:alg :bcrypt+sha512})}})
      {:status :error
       :code :data-error}))

(defn user-login [user-id password]
  (letfn [(setter [pwd]
            (let [pwd (hashers/derive pwd)]
              (adapters/user-update! repository
                                     {:user
                                      {:id user-id
                                       :password pwd}})))]
    (if (hashers/check password
                       (adapters/user-get-password repository user-id)
                       {:setter setter})
      {:status :ok}
      {:status :error
       :code :unauthorized})))

(defn user-get [user-id]
  (or (adapters/user-get repository user-id)
      {:status :error
       :code :data-error}))

(defn user-update! [user-id user-data]
  (or (adapters/user-update! repository user-id)
        {:status :error
         :code :data-error}))

(defn files-get [user-id filter]
  (or (adapters/files-get repository user-id {})
        {:status :error
         :code :data-error}))

(defn files-tag! [user-id files tag]
  (or (adapters/files-tag! repository user-id files tag)
        {:status :error
         :code :data-error}))

(defn files-resync! [user-id]
  (if-let [files (adapters/files-sync file-storage user-id)]
    (or (adapters/files-update! repository user-id files)
        {:status :error
         :code :data-error})
    {:status :error
     :code :service-unavailable}))

(defn index [csrf-token]
  (html/index csrf-token))
