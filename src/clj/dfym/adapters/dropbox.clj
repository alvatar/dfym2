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

(defrecord DropboxAdapter []

  component/Lifecycle
  (start [component] component)
  (stop [component] component)

  adapters/FileStorageAdapter
  (file-storage-sync [self user-id]
    "RESULT Get list of files from the storage")

  (file-storage-token [self code]
    (some-> (http/post (str dropbox-url "oauth2/token")
                       {:throw-exceptions false
                        ;;:debug true
                        ;;:debug-body? true
                        :form-params {:code code
                                      :grant_type "authorization_code"
                                      :redirect_uri "http://localhost:5000/dropbox-connect-finish"
                                      :client_id "c34rhcknih9xxbu"
                                      :client_secret "ljo76ioxon4xcw6"}})
            :body
            (json/parse-string)
            (get "access_token"))))
