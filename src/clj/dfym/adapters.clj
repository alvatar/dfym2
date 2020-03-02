(ns dfym.adapters
  (:require [clojure.pprint :refer [pprint]]
            ;; Environment and configuration
            [environ.core :refer [env]]))

(defprotocol RepositoryAdapter
  "An adapter for data repositories"
  (user-get [self user-id] "Get user data")
  (user-update! [self user-id data] "Update user data")
  (files-get [self user-id filters] "Get user files listing")
  (files-resync! [self user-id] "Resync user files listing with original source"))
