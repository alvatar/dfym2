(ns dfym.controllers.dropbox
  (:require [clojure.pprint :refer [pprint]]))

(defrecord DropboxAdapter []

  component/Lifecycle
  (start [component] component)
  (stop [component] component)

  FileStorageAdapter
  (files-sync [self user-id]
    "RESULT Get list of files from the storage"))
