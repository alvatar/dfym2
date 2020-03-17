(ns dfym.adapters
  (:require [clojure.pprint :refer [pprint]]
            ;; Environment and configuration
            [environ.core :refer [env]]))

(defprotocol RepositoryAdapter
  "An adapter for data repositories"

  (user-get [self user-map] "Get user data")
  (user-create! [self user-map] "Create user")
  (user-update! [self user-map] "Update user data")
  (files-create! [self user-id file-map] "Create a new file record")
  (files-get [self user-id] "Get user files listing")
  (files-tag! [self user-id files tag] "Tag user files")
  (files-update! [self user-id file-map] "Update file"))

(defprotocol FileStorageAdapter
  "An adapter for file storages"

  (file-storage-token [self code] "Get a user token")
  (file-storage-sync [self user-id token data-chunk-fn] "Get list of files from the storage"))
