(ns app.model.user
  (:require
   [clojure.string :as str]
   [app.model.specs]
   [taoensso.timbre :as t]
   [clojure.spec.alpha :as spec]
   [com.wsscode.pathom.connect :as pc]))

; an in memory database of registered users
(defonce user-database
  (atom {1 {:user/id       1
            :user/name     "defaultuser"
            :user/password "abcdEFGH@123"
            :user/role     :user}}))

(defn next-id
  "Generate a valid id for a new user"
  [db]
  (some->> db
           keys
           (apply (fnil max 0))
           inc))

(defn password-rules
  "Given a password, return a set of keywords for any rules that are not satisfied"
  [password]
  (cond-> #{}

    ;; password must be at least 8 characters long
    (not (spec/valid? :app.model.specs/password-long-enough password))
    (conj :password.error/too-short)

    ;; password must contain a special character
    (not (spec/valid? :app.model.specs/contains-special-character password))
    (conj :password.error/missing-special-character)

    ;; password contains at least one lower case letter
    (not (spec/valid? :app.model.specs/contains-lowercase password))
    (conj :password.error/missing-lowercase)

    ;; password contains at least one upper case letter
    (not (spec/valid? :app.model.specs/contains-uppercase password))
    (conj :password.error/missing-uppercase)))

(defn- user-by-name [db username]
  (some #(when (= (str/lower-case (:user/name %)) (str/lower-case username))
           %)
        (vals db)))

(defn authenticate-user
  "Returns a user map when a username and password are correct, or nil when incorrect"
  [db username password]
  (t/info "login" username password)
  (if-let [{known-password :user/password :as user} (user-by-name db username)]
    (if (= password known-password)
      (dissoc user :user/password)
      (throw (ex-info "Invalid username or password"
                      {:reason :login.error/invalid-credentials})))
    (throw (ex-info "Username not found"
                    {:reason :login.error/user-not-found}))))

(defn create-user
  "Create a user by adding them to the database, and returns the user's details except for their password"
  ([db-atom username password]
   ; recur with a default role of :user
   (create-user db-atom username password :user))
  ([db-atom username password role]
   ; if there are any password violations
   (if-let [password-violations (not-empty (password-rules password))]
     ; then throw an exception with the violation codes
     (throw (ex-info "Password does not meet criteria"
                     {:reason     :create-user.error/password-violations
                      :violations password-violations}))
     ; otherwise check if the user exists
     (if (user-by-name @db-atom username)
       ; and if they do then return an error code
       (throw (ex-info "User already exists"
                       {:reason :create-user.error/already-exists}))
       ; otherwise return a success message with the user's details
       (let [id (next-id @db-atom)]
         (-> db-atom
           ; put the user in the database
           ;; todo need to use new id
            (swap! assoc id {:user/id id :user/name username :user/role role :user/password password})
           ; select the user in the database
            (get id)
           ; strip their password
            (dissoc :user/password))
         {:user/id id})))))


(pc/defresolver user-resolver [_ {:user/keys [id]}]
  {::pc/input  #{:user/id}
   ::pc/output [:user/age :user/role :user/name]}
  (@user-database id))

(pc/defresolver all-users-resolver [_ _]
  {::pc/output [{:all-users [:user/id]}]}
  {:all-users
   (mapv (fn [id] {:user/id id}) (keys @user-database))})

(defn- handle-exception [e]
  (if-let [data (ex-data e)]
    data
    (do
      (t/error e)
      {:reason :error/internal-error})))

(pc/defmutation add-user [env {:user/keys [id name role password] :as user}]
  {::pc/output [:user/id]}
  ;; todo better way to pass database
  (try
    (t/info "add-user" user)
    (create-user user-database name password (or role :user))
    (catch Exception e
      (handle-exception e))))

(pc/defmutation login [env {:user-login/keys [name password] :as user}]
  {::pc/output [:user/id]}
  (try
    ;; when authentication failed, returns nil
    ;; how to deal with it in fulcro?
    (authenticate-user @user-database name password)
    (catch Exception e
      (handle-exception e))))

(def resolvers [all-users-resolver user-resolver add-user login])

(comment
  (next-id @user-database)
  @user-database
  (user-by-name @user-database "defaultuser")
  (authenticate-user @user-database "defaultuser" "password@123")

  ;;
  )
