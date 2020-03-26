(ns worker.core
  (:require
   [butler.core :as butler]
   [datascript.transit :as dt]))

(js/importScripts "localStorageDB.min.js")

(defn persist-db [db]
  (js/ldb.set "dfym/db" db)
  (butler/bring! :print-string "IndexedDB written from Webworker"))

(butler/serve! {:persist-db persist-db})
