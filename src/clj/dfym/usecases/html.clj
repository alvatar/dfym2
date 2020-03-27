(ns dfym.usecases.html
  (:require [clojure.pprint :refer [pprint]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
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
     ;; Main CSS
     [:link {:rel "stylesheet" :href "css/main.css"}]
     ;; IndexDB storage
     [:script {:src "js/localStorageDB.min.js" :type "text/javascript"}]
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

(defn login [flash]
  (html
   [:html
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     ;; Normalize
     [:link {:rel "stylesheet" :href "css/normalize.css"}]
     [:link {:rel "stylesheet" :href "css/simplegrid.css"}]
     [:body
      [:h1 "Login"]
      [:h2 flash]
      [:form {:method "post"}
       (anti-forgery-field)
       [:input {:type "text" :placeholder "Username: " :name "username"}]
       [:input {:type "password" :placeholder "Password: " :name "password"}]
       [:input {:type "submit" :value "Submit"}]]]]]))

(defn dropbox-connect []
  (html
   [:html
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     ;; Normalize
     [:link {:rel "stylesheet" :href "css/normalize.css"}]
     [:link {:rel "stylesheet" :href "css/simplegrid.css"}]]
    [:body
     [:h1 "Connect to Dropbox"]
     [:h2 [:a {:href "/dropbox-redirect"} "Go"]]]]))
