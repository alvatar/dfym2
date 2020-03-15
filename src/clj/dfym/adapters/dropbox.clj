(ns dfym.adapters.dropbox
  (:require [clojure.pprint :refer [pprint]]
            [com.stuartsierra.component :as component]
            [clj-http.client :as http]
            [manifold.deferred :as d]
            [byte-streams :as bs]
            [cheshire.core :as json]
            ;;-----
            [dfym.adapters :as adapters]))


(def dropbox-url "https://api.dropboxapi.com/")

(defn -file-storage-token [code]
  (some-> (http/post (str dropbox-url "oauth2/token")
                     {:throw-exceptions false
                      :form-params {:code code
                                    :grant_type "authorization_code"
                                    :redirect_uri "http://localhost:5000/dropbox-connect-finish"
                                    :client_id "c34rhcknih9xxbu"
                                    :client_secret "ljo76ioxon4xcw6"}})
          :body
          (json/parse-string)
          (get "access_token")))

(defn -file-storage-sync [user-id token data-chunk-fn]
  (loop [out (some-> (http/post (str dropbox-url "/2/files/list_folder")
                                {:headers {"Authorization" (str "Bearer " token)}
                                 :content-type :json
                                 :form-params {"path" "/_nosync_music"
                                               "recursive" true
                                               "include_media_info" true
                                               "include_mounted_folders" true}})
                     :body
                     (json/parse-string true))]
    (data-chunk-fn user-id (get out :entries))
    (if (get out :has_more)
      (recur (some-> (http/post (str dropbox-url "/2/files/list_folder/continue")
                                {:headers {"Authorization" (str "Bearer " token)}
                                 :content-type :json
                                 :form-params {"cursor" (get out :cursor)}})
                     :body
                     (json/parse-string true)))
      'ok)))

(defrecord DropboxAdapter []

  component/Lifecycle
  (start [component] component)
  (stop [component] component)

  adapters/FileStorageAdapter
  (file-storage-token [self code]
    (-file-storage-token code))
  (file-storage-sync [self user-id token data-chunk-fn]
    (-file-storage-sync user-id token data-chunk-fn)))
