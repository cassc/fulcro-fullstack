(ns app.model.specs
  (:require
   [clojure.string :refer [blank?]]
   #?(:clj
      [clojure.spec.alpha :as s]
      :cljs
      [cljs.spec.alpha :as s])))

(s/def ::password-long-enough
  (fn [password]
    (>= (count password) 8)))

(s/def ::contains-special-character
  (fn [password]
    (re-seq #"[!@#$%^&*(),.?\":{}|<>]" password)))


(s/def ::contains-lowercase
  (fn [password]
    (re-seq #".*[a-z]" password)))

(s/def ::contains-uppercase
  (fn [password]
   (re-seq #".*[A-Z]" password)))

(s/def :user/id int?)
(s/def :user/name (s/and string? (complement blank?)))
(s/def :user/password (s/and string?
                             ::password-long-enough
                             ::contains-special-character
                             ::contains-lowercase
                             ::contains-uppercase))

(s/def :user/role keyword?)

(s/def ::user (s/keys :req [:user/id :user/name :user/password :user/role]))

(comment
  (s/explain  :user/password "adasdfasddb")
  (s/valid?  :user/password "adasdfM#asddbA@")
  (->>
   (s/explain-data  :user/password "adas")
   #?(:clj
      (:clojure.spec.alpha/problems)
      :cljs
      (:cljs.spec.alpha/problems))
   (map :via))
  (s/explain ::user {:user/id 0
                     :user/name "auser"
                     :user/password "password@232L"
                     :user/role :user})

  ;;
  )
