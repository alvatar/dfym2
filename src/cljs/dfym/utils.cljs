(ns dfym.utils
  (:require [taoensso.encore :as encore :refer-macros [have have?]]
            [taoensso.timbre :as timbre :refer-macros [tracef debugf infof warnf errorf]]
            ;; -----
            [dfym.globals :as globals]))

;;
;; Utils
;;

(defn json->clj [s] (js->clj (.parse js/JSON s) :keywordize-keys true))

(defn clj->json [ds] (.stringify js/JSON (clj->js ds)))

(defn log* [& objs]
  (when (= globals/*env* "dev")
    (js/console.log (clojure.string/join "" (map str objs)))))

;; window.RTCPeerConnection = window.RTCPeerConnection || window.mozRTCPeerConnection || window.webkitRTCPeerConnection;   //compatibility for firefox and chrome
;;     var pc = new RTCPeerConnection({iceServers:[]}), noop = function(){};
;;     pc.createDataChannel("");    //create a bogus data channel
;;     pc.createOffer(pc.setLocalDescription.bind(pc), noop);    // create offer and set local description
;;     pc.onicecandidate = function(ice){  //listen for candidate events
;;         if(!ice || !ice.candidate || !ice.candidate.candidate)  return;
;;         var myIP = /([0-9]{1,3}(\.[0-9]{1,3}){3}|[a-f0-9]{1,4}(:[a-f0-9]{1,4}){7})/.exec(ice.candidate.candidate)[1];
;;         console.log('my IP: ', myIP);
;;         pc.onicecandidate = noop;
;;     };
