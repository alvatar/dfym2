(ns dfym.usecases.html
  (:require [clojure.pprint :refer [pprint]]
            [ring.middleware.anti-forgery :as anti-forgery]
            [hiccup.core :refer :all]))

(defn index [csrf-token]
  (html
   [:html
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     ;; [:link {:rel "stylesheet" :href "/css/antd.min.css"}]
     ;; Normalize
     [:link {:rel "stylesheet" :href "css/normalize.css"}]]
    [:body
     [:div#sente-csrf-token {:data-csrf-token csrf-token}]
     [:div#app]
     [:script {:src "js/dfym.js" :type "text/javascript"}]]]))
