(ns dfym.usecases
  (:require [clojure.pprint :refer [pprint]]
            [environ.core :refer [env]]
            [com.stuartsierra.component :as component]
            [buddy.hashers :as hashers]
            [ring.util.response :as resp]
            ;; -----
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

(defn user-check-password [user-name password]
  (letfn [(setter [pwd]
            (let [pwd (hashers/derive pwd)]
              (adapters/user-update! repository
                                     {:user-name user-name
                                      :password pwd})))]
    (hashers/check password
                   (adapters/user-get-password repository user-name)
                   {:setter setter})))

(defn user-create [user-name user-password]
  (or (adapters/user-create! repository
                             {:user-name user-name
                              :password (hashers/derive user-password {:alg :bcrypt+sha512})})
      {:status :error
       :code :data-error}))

(defn user-get [user-id]
  (or (adapters/user-get repository user-id)
      {:status :error
       :code :data-error}))

(defn user-update! [user-map]
  (or (adapters/user-update! repository user-map)
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

