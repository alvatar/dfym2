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
     ;; Normalize
     [:link {:rel "stylesheet" :href "css/normalize.css"}]
     [:link {:rel "stylesheet" :href "css/simplegrid.css"}]
     ;; Media player
     [:script {:src "mediaplayer/mediaelement-and-player.min.js" :type "text/javascript"}]
     [:link {:rel "stylesheet" :href "mediaplayer/mediaelementplayer.min.css"}]
     [:link {:rel "stylesheet" :href "css/mediaplayer.css"}]]
    [:body
     [:div#sente-csrf-token {:data-csrf-token csrf-token}]
     [:div#app]
     ;; Media player
     [:script {:src "https://unpkg.com/mediaplayer/browser.js" :type "text/javascript"}]
     ;; App
     [:script {:src "js/dfym.js" :type "text/javascript"}]]]))
