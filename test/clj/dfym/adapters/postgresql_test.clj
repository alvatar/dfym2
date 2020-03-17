(ns dfym.adapters.postgresql_test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [dfym.adapters.postgresql :refer :all]))

(deftest test-file-cache
  (reset! files-cache {})

  (fact "first path is created"
        (let [path ["user_1" "dropbox" "music"]
              entry (ensure-path! path
                                  {:id "a"
                                   :name "music"
                                   :user_id 1})]
          (cache-put! path (->kebab-case entry)))
        @files-cache => {"user_1"
                         {:fileinfo {:id "user_1"
                                     :path (->Ltree "user_1")}
                          "dropbox" {:fileinfo {:id "dropbox"
                                                :path (->Ltree "user_1.dropbox")}
                                     "music" {:fileinfo {:id "a"
                                                         :name "music"
                                                         :path (->Ltree "user_1.dropbox.a")
                                                         :user-id 1}}}}})

  (fact "adding paths at the next level"
        (let [path ["user_1" "dropbox" "music" "albums"]
              entry (ensure-path! path {:id "b"
                                        :name "albums"
                                        :user_id 1})]
          (cache-put! path (->kebab-case entry)))
        @files-cache => {"user_1" {:fileinfo {:id "user_1"
                                              :path (->Ltree "user_1")}
                                   "dropbox" {:fileinfo {:id "dropbox"
                                                         :path (->Ltree "user_1.dropbox")}
                                              "music" {"albums" {:fileinfo {:id "b"
                                                                            :name "albums"
                                                                            :path (->Ltree "user_1.dropbox.a.b")
                                                                            :user-id 1}}
                                                       :fileinfo {:id "a"
                                                                  :name "music"
                                                                  :path (->Ltree "user_1.dropbox.a")
                                                                  :user-id 1}}}}}

        (let [path ["user_1" "dropbox" "music" "composers"]
              entry (ensure-path! path {:id "c"
                                        :name "composers"
                                        :user_id 1})]
          (cache-put! path (->kebab-case entry)))
        @files-cache => {"user_1" {:fileinfo {:id "user_1"
                                              :path (->Ltree "user_1")}
                                   "dropbox" {:fileinfo {:id "dropbox"
                                                         :path (->Ltree "user_1.dropbox")}
                                              "music" {"albums" {:fileinfo {:id "b"
                                                                            :name "albums"
                                                                            :path (->Ltree "user_1.dropbox.a.b")
                                                                            :user-id 1}}
                                                       "composers" {:fileinfo {:id "c"
                                                                               :name "composers"
                                                                               :path (->Ltree "user_1.dropbox.a.c")
                                                                               :user-id 1}}
                                                       :fileinfo {:id "a"
                                                                  :name "music"
                                                                  :path (->Ltree "user_1.dropbox.a")
                                                                  :user-id 1}}}}})

  (fact "adding a third level"
        (let [path ["user_1" "dropbox" "music" "albums" "Aphex Twin ambient works"]
              entry (ensure-path! path {:id "d"
                                        :name "Aphex Twin ambient works"
                                        :user_id 1})]
          (cache-put! path (->kebab-case entry)))
        @files-cache => {"user_1" {:fileinfo {:id "user_1"
                                              :path (->Ltree "user_1")}
                                   "dropbox" {:fileinfo {:id "dropbox"
                                                         :path (->Ltree "user_1.dropbox")}
                                              "music" {"albums" {"Aphex Twin ambient works" {:fileinfo {:id "d"
                                                                                                        :name "Aphex Twin ambient works"
                                                                                                        :path (->Ltree "user_1.dropbox.a.b.d")
                                                                                                        :user-id 1}}
                                                                 :fileinfo {:id "b"
                                                                            :name "albums"
                                                                            :path (->Ltree "user_1.dropbox.a.b")
                                                                            :user-id 1}}
                                                       "composers" {:fileinfo {:id "c"
                                                                               :name "composers"
                                                                               :path (->Ltree "user_1.dropbox.a.c")
                                                                               :user-id 1}}
                                                       :fileinfo {:id "a"
                                                                  :name "music"
                                                                  :path (->Ltree "user_1.dropbox.a")
                                                                  :user-id 1}}}}})

  (fact "cache-put! returns usable value"
        (ensure-path! ["user_1" "dropbox" "music" "albums" "Gorgoroth"]
                      {:id "e"
                       :name "Gorgoroth"
                       :user_id 1})
        =>
        {:id "e" :name "Gorgoroth" :path (->Ltree "user_1.dropbox.a.b.e") :user_id 1}

        (reset! files-cache {})

        (ensure-path! ["user_2" "google" "music"]
                      {:id "a"
                       :name "New Album"
                       :user_id 2})
        =>
        {:id "a" :name "New Album" :path (->Ltree "user_2.google.a") :user_id 2})
  )
