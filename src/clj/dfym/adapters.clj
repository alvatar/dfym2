(ns dfym.adapters
  (:require [clojure.pprint :refer [pprint]]
            ;; Environment and configuration
            [environ.core :refer [env]]))

(defprotocol RepositoryAdapter
  "An adapter for data repositories"

  (user-get [self user-name] "Get user data")
  (user-create! [self user-data] "Create user")
  (user-update! [self user-data] "Update user data")
  (files-get [self user-id filters] "Get user files listing")
  (files-tag! [self user-id files tag] "Tag user files")
  (files-update! [self user-id files] "Update files for user"))

(defprotocol FileStorageAdapter
  "An adapter for file storages"

  (file-storage-token [self code] "Get a user token")
  (file-storage-sync [self user-id] "Get list of files from the storage"))
