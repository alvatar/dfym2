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
  "Usecase: create user"
  (adapters/create-user! repository
                         {:user-name user-name
                          :password (hashers/derive user-password {:alg :bcrypt+sha512})}))

(defn get-user-token [user-id tmp-code]
  "Usecase: get user token"
  (if-let [token (adapters/file-storage-token file-storage tmp-code)]
    (adapters/update-user! repository {:id user-id :dropbox-token token})
    (throw (Exception. "no token received from Dropbox"))))

(defn get-user [user-map]
  "Usecase: get user"
  (adapters/get-user repository user-map))

(defn update-user! [user-map]
  "Usecase: update user"
  (adapters/update-user! repository user-map))

;;
;; Files
;;

(defn valid-extension? [filename]
  (some (partial string/ends-with? filename)
        ["mp3" "flac" "ogg" "wav" "mp4" "mpc" "m4a" "m4b" "m4p" "webm" "wv" "wma" "raw" "aa" "aiff"]))

(defn format-tree [files-tree]
  (-> (letfn [(shrink-branch [m k v]
                ;; We use the Dropbox Id + name
                (let [{:keys [dropbox-id name]} (:fileinfo v)
                      children (dissoc v :fileinfo)]
                  (assoc m
                         k (merge {:file/name k}
                                  (if dropbox-id {:file/id dropbox-id}
                                      (throw (Exception. (str "FATAL: File missing Id in ['" k "']"))))
                                  (when (not-empty children)
                                    {:file/child
                                     (mapv second
                                           (reduce-kv shrink-branch {} children))})))))]
        (reduce-kv shrink-branch {} files-tree))
      (get "dropbox")))

(defn get-files [user-id]
  "Usecase: get files"
  (format-tree
   (adapters/get-files repository user-id)))

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
  "Usecase: resync files"
  (let [{token :dropbox-token} (adapters/get-user repository {:id user-id})]
    (adapters/file-storage-sync file-storage user-id token files-saver!)))

(defn get-file-link [user-id file-id]
  "Usecase: get file link"
  (let [{token :dropbox-token} (adapters/get-user repository {:id user-id})
        {path :path-display} (adapters/get-file repository user-id file-id)]
    (adapters/get-file-link file-storage token path)))

;;
;; Tags
;;

(defn create-tag! [user-id tag]
  (adapters/create-tag! repository user-id tag))

(defn get-tags [user-id]
  (adapters/get-tags repository user-id))

;; (defn update-tag! [user-id tag]
;;   (adapters/update-tag! repository user-id tag))

;; (defn delete-tag! [user-id tag]
;;   (adapters/delete-tag! repository user-id tag))

(defn attach-tag! [user-id file-id tag]
  (adapters/attach-tag! repository user-id file-id tag))

;; (defn detach-tag! [user-id file-id tag]
;;   (adapters/detach-tag! repository user-id file-id tag))
