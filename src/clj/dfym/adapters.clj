(ns dfym.adapters
  (:require [clojure.pprint :refer [pprint]]
            ;; Environment and configuration
            [environ.core :refer [env]]))

(defprotocol RepositoryAdapter
  "An adapter for data repositories"
  (user-get [self user-id] "Get user data")
  (user-get-password [self user-id] "Get user data")
  (user-update! [self user-data] "Update user data")
  (files-get [self user-id filters] "Get user files listing")
  (files-tag! [self user-id files tag] "Tag user files")
  (files-update! [self user-id files] "Update files for user"))

(defprotocol FileStorageAdapter
  "An adapter for file storages"
  (files-sync [self user-id] "Get list of files from the storage"))
