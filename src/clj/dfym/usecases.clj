(ns dfym.usecases
  (:require [clojure.pprint :refer [pprint]]
            [environ.core :refer [env]]
            [com.stuartsierra.component :as component]
            [buddy.hashers :as hashers]
            [ring.util.response :as resp]
            ;; -----
            [dfym.adapters :as adapters]))

(defrecord UseCases [repository file-storage]
  component/Lifecycle
  (start [component]
    (println "Starting Use Cases...")
    (def repository (:repository component))
    (def file-storage (:file-storage component))
    (when-not (and repository file-storage)
      (throw (Exception. "usecases require a repository and a file storage")))
    component)
  (stop [component]
    component))

;;
;; User
;;

(defn user-check-password [user-name unhashed-password]
  (letfn [(setter [pwd]
            (let [pwd (hashers/derive pwd)]
              (adapters/user-update! repository
                                     {:user-name user-name
                                      :password pwd})))]
    (let [{:keys [id password] :as user} (adapters/user-get repository user-name)]
      (when (hashers/check unhashed-password
                           password
                           {:setter setter})
        (select-keys user [:id :user-name :dropbox-token])))))

(defn user-create [user-name user-password]
  (adapters/user-create! repository
                         {:user-name user-name
                          :password (hashers/derive user-password {:alg :bcrypt+sha512})}))

(defn user-get-token [user-id tmp-code]
  (if-let [token (adapters/file-storage-token file-storage tmp-code)]
    (adapters/user-update! repository {:id user-id :dropbox-token token})
    (throw (Exception. "no token received from Dropbox"))))

(defn user-get [user-id]
  (adapters/user-get repository user-id))

(defn user-update! [user-map]
  ;; TODO: filter keys
  (adapters/user-update! repository user-map))

;;
;; Files
;;

(defn files-get [user-id filter]
  (adapters/files-get repository user-id {}))

(defn files-tag! [user-id files tag]
  (adapters/files-tag! repository user-id files tag))

(defn files-resync! [user-id]
  (if-let [files (adapters/file-storage-sync file-storage user-id)]
    (or (adapters/files-update! repository user-id files)
        (throw (Exception. "data storage error")))
    (throw (Exception. "file storage service unavailable"))))

