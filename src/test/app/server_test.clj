(ns app.server-test
  (:require
   [clojure.data.json :as json]
   [clojure.test :refer [deftest testing is]]
   [app.model.user :refer [add-user login]]
   [app.server :refer [parser middleware]]))


(defn uuid [] (.toString (java.util.UUID/randomUUID)))

(def default-user {:user/name     "defaultuser"
                   :user/password "abcdEFGH@123"})

(defn rand-user []
  {:user/name     (uuid)
   :user/password "abcdEFGH@123"})

(defn- pathom-result
  "Get pathom result from parser result"
  [resp]
  (println resp)
  (->> resp
       vals
       first))

(defn- expect-invalid-password-error [resp]
  (is (= :create-user.error/password-violations (get-in resp [:reason])) "Expect error when password is invalid"))

(defn- test-invalid-user-creation [title user specific-error-fn]
  (testing title
    (let [resp (pathom-result (parser {} `[(add-user ~user)]))]
      (expect-invalid-password-error resp)
      (specific-error-fn resp))))


(deftest create-user-test
  (testing "Testing user creation"
    (testing "User already exists when attempting to create a new one"
      (is (= {:reason :create-user.error/already-exists}
             (pathom-result (parser {} `[(add-user ~default-user)])))))

    (test-invalid-user-creation
     "Password is not at least 8 characters long"
     {:user/name "tuser2" :user/password "abcd"}
     (fn [resp]
       (is (some (partial = :password.error/too-short) (get resp :violations)) "Expect password too short error")))

    (test-invalid-user-creation
     "Password does not contain a lower case letter"
     {:user/name "tuser2" :user/password "ABCDEFGHIJ"}
     (fn [resp]
       (is (some (partial = :password.error/missing-lowercase) (get resp :violations)) "Expect missing lowercase error")))

    (test-invalid-user-creation
     "Password does not contain an upper case letter"
     {:user/name "tuser2" :user/password "adsbsdsdww"}
     (fn [resp]
       (is (some (partial = :password.error/missing-uppercase) (get resp :violations)) "Expect missing uppercase error")))

    (test-invalid-user-creation
     "Password does not contain a special character"
     {:user/name "tuser2" :user/password "Adsbsdsdww"}
     (fn [resp]
       (is (some (partial = :password.error/missing-special-character) (get resp :violations)) "Expect missing special character error")))

    (testing "Create a valid user without specifying role"
      (let [user (rand-user)
            resp (pathom-result (parser {} `[(add-user ~user)]))
            id (resp :user/id)
            actual-role (pathom-result (parser {} [{[:user/id id] [:user/role]}]))]
        (is (pos-int? id) "Expect user id after user creation")
        (is (= :user (:user/role actual-role)) "Expect a default user role")))
    (testing "Create a valid user with a specified role"
      (let [user (assoc (rand-user) :user/role :admin)
            resp (pathom-result (parser {} `[(add-user ~user)]))
            id (resp :user/id)
            actual-role (pathom-result (parser {} [{[:user/id id] [:user/role]}]))]
        (is (pos-int? id) "Expect user id after user creation")
        (is (= (:user/role user) (:user/role actual-role)) "Expect same user role created on server")))))

(deftest get-user-test
  (testing "Testing user get"
    (testing "Username not exists in database"
      (let [user {:user-login/name "nonexistuser"
                  :user-login/password "anyL@validpass"}
            resp (pathom-result (parser {} `[(login ~user)]))]
        (is (= :login.error/user-not-found (get resp :reason)) "Expect user not found error")))
    (testing "Invalid username and password combination"
      (let [user {:user-login/name (:user/name default-user)
                  :user-login/password "anyL@validpass"}
            resp (pathom-result (parser {} `[(login ~user)]))]
        (is (= :login.error/invalid-credentials (get resp :reason)) "Expect invalid credential error")))))



(comment
  (pathom-result (parser {} [{[:user/id 1] [:user/role]}]))
  ;;
  )
