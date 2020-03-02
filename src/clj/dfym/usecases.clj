(ns dfym.usecases
  (:require [clojure.pprint :refer [pprint]]
            [environ.core :refer [env]]
            [com.stuartsierra.component :as component]
            ;; -----
            [dfym.usecases.html :as html]
            [dfym.adapters :as adapters]))

;;
;; Use Cases
;;

(defrecord UseCases [repository]
  component/Lifecycle
  (start [component]
    (println "Starting Use Cases...")
    (def repository (:repository component))
    component)
  (stop [component]
    (println "Stopping Use Cases...")
    component))

(defn user-get [user-id]
  (adapters/user-get repository user-id)
  (println "USER GET!")
  'TODO)

(defn user-set! [user-id user-data]
  (println "USER SET!")
  'TODO)

(defn files-get [user-id filter]
  (println "FILES GET!")
  'TODO)

(defn files-resync! [user-id]
  (println "FILES RESYNC!")
  'TODO)

(defn index [csrf-token]
  (html/index csrf-token))
