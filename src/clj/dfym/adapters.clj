(ns dfym.adapters
  (:require [clojure.pprint :refer [pprint]]
            ;; Environment and configuration
            [environ.core :refer [env]]))

(defprotocol RepositoryAdapter
  "An adapter for data repositories"

  ;; Users
  (create-user! [self user-map] "Create user")
  (get-user [self user-map] "Get user data")
  (update-user! [self user-map] "Update user data")
  ;; Files
  (get-files [self user-id] "Get user files listing")
  (create-file! [self user-id file-map] "Create a new file for a user")
  (get-file [self user-id file-id] "Get the file metadata")
  ;; Tags
  (create-tag! [self user-id tag] "Create new tag")
  (get-tags [self user-id] "Get user file tags")
  (update-tag! [self user-id tag] "Update a tag")
  (delete-tag! [self user-id tag] "Update a tag")
  ;; Tag links
  (attach-tag! [self user-id file-id tag] "Attach a tag to a file")
  (detach-tag! [self user-id file-id tag] "Detach a tag from a file"))

(defprotocol FileStorageAdapter
  "An adapter for file storages"

  (file-storage-token [self code] "Get a user token")
  (file-storage-sync [self user-id token data-chunk-fn] "Get list of files from the storage")
  (get-file-link [self token file-id] "Get the streamable link from the provider"))
