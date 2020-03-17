(ns dfym.usecases
  (:require [clojure.pprint :refer [pprint]]
            [environ.core :refer [env]]
            [com.stuartsierra.component :as component]
            [buddy.hashers :as hashers]
            [ring.util.response :as resp]
            [clojure.string :as string]
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
    (let [{:keys [id password] :as user} (adapters/user-get repository {:user-name user-name})]
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

(defn user-get [user-map]
  (adapters/user-get repository user-map))

(defn user-update! [user-map]
  (adapters/user-update! repository user-map))

;;
;; Files
;;

(defn valid-extension? [filename]
  (when (> (count filename) 3)
    (let [extension (subs filename (- (.length filename) 3))]
      (some #(= extension %) ["mp3" "flac" "ogg" "wav" "mp4" "mpc" "m4a" "m4b" "m4p" "webm" "wv" "wma" "raw" "aa" "aiff"]))))

(defn files-get [user-id filter]
  (adapters/files-get repository user-id))

(defn files-tag! [user-id files tag]
  (adapters/files-tag! repository user-id files tag))

(defn- files-saver! [user-id entries]
  (doseq [{:keys [name path_lower path_display id size rev client_modified server_modified] :as entry} entries]
    (let [folder? (= (get entry :.tag) "folder")]
      (when (or folder? (valid-extension? name))
        (adapters/files-create! repository
                                user-id
                                {:name name
                                 :path-lower path_lower
                                 :path-display path_display
                                 :folder? folder?
                                 :storage :dropbox
                                 :id id
                                 :size size
                                 :rev rev})))))

(defn files-resync! [user-id]
  (let [{token :dropbox-token} (adapters/user-get repository {:id user-id})]
    (adapters/file-storage-sync file-storage user-id token files-saver!)))

