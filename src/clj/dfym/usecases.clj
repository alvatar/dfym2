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

(defn check-user-password [user-name unhashed-password]
  (letfn [(setter [pwd]
            (let [pwd (hashers/derive pwd)]
              (adapters/update-user! repository
                                     {:user-name user-name
                                      :password pwd})))]
    (let [{:keys [id password] :as user} (adapters/get-user repository {:user-name user-name})]
      (when (hashers/check unhashed-password
                           password
                           {:setter setter})
        (select-keys user [:id :user-name :dropbox-token])))))

(defn create-user [user-name user-password]
  (adapters/create-user! repository
                         {:user-name user-name
                          :password (hashers/derive user-password {:alg :bcrypt+sha512})}))

(defn get-user-token [user-id tmp-code]
  (if-let [token (adapters/file-storage-token file-storage tmp-code)]
    (adapters/update-user! repository {:id user-id :dropbox-token token})
    (throw (Exception. "no token received from Dropbox"))))

(defn get-user [user-map]
  (adapters/get-user repository user-map))

(defn update-user! [user-map]
  (adapters/update-user! repository user-map))

;;
;; Files
;;

(defn valid-extension? [filename]
  (when (> (count filename) 3)
    (let [extension (subs filename (- (.length filename) 3))]
      (some #(= extension %) ["mp3" "flac" "ogg" "wav" "mp4" "mpc" "m4a" "m4b" "m4p" "webm" "wv" "wma" "raw" "aa" "aiff"]))))

(defn get-files [user-id]
  (adapters/get-files repository user-id))

(defn- files-saver! [user-id entries]
  (doseq [{:keys [name path_lower path_display id size rev client_modified server_modified] :as entry} entries]
    (let [folder? (= (get entry :.tag) "folder")]
      (when (or folder? (valid-extension? name))
        (adapters/create-file! repository
                               user-id
                               {:name name
                                :path-lower path_lower
                                :path-display path_display
                                :folder? folder?
                                :storage :dropbox
                                :id id
                                :size size
                                :rev rev})))))

(defn resync-files! [user-id]
  (let [{token :dropbox-token} (adapters/get-user repository {:id user-id})]
    (adapters/file-storage-sync file-storage user-id token files-saver!)))

;;
;; Tags
;;

(defn get-tags [user-id]
  (adapters/get-tags repository user-id))

(defn update-tag! [user-id tag]
  (adapters/update-tag! repository user-id tag))

(defn delete-tag! [user-id tag]
  (adapters/delete-tag! repository user-id tag))

;;
;; Tag links
;;

(defn link-tag! [user-id file-id tag]
  (adapters/link-tag! repository user-id file-id tag))

(defn unlink-tag! [user-id file-id tag]
  (adapters/unlink-tag! repository user-id file-id tag))
