(ns app.model.user
  (:require
   [app.model.specs]
   [cljs.spec.alpha :as spec]
   [clojure.string :as s]
   [com.fulcrologic.fulcro.algorithms.merge :as merge]
   [com.fulcrologic.fulcro.algorithms.denormalize :as fdn]
   [com.fulcrologic.fulcro.data-fetch :as df]
   [com.fulcrologic.fulcro.components :as comp]
   [com.fulcrologic.fulcro.algorithms.form-state :as fs]
   [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
   [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]))


(defn toggle-newuser-ui [on? app comp]
  (merge/merge-component! app comp
                          {:ui/new?          on?
                           :error/reason     nil
                           :error/violations []
                           :newuser/id       (tempid/tempid)
                           :newuser/role     "user"
                           :newuser/name     ""
                           :newuser/password ""}))

(def show-newuser-ui (partial toggle-newuser-ui true))
(def hide-newuser-ui (partial toggle-newuser-ui false))


;; Add user by sending request to server and updating local state
#_{:clj-kondo/ignore [:unresolved-symbol]}
(defmutation add-user [params]
  (remote [env] true)
  (ok-action [{:keys [state tempid->realid component result] :as env}]
             (let [
                   ;; the id for the new user
                   id (get-in result [:body (symbol ::add-user) :user/id])
                   ;; server response errors
                   {:keys [reason violations]} (get-in result [:body (symbol ::add-user)])]
               (if reason
                 ;; If there is an error, we display it on the UI
                 (swap! state update-in [:component/id :newuser] (fn [user] (assoc user :error/reason reason :error/violations violations)))
                 ;; If user is created,
                 (if id
                   (let [user (assoc params :user/id id)]
                     ;; add the newly created user to our local state
                     (swap! state assoc-in [:user/id id] user)
                     ;; hide the NewUser div
                     (swap! state assoc-in [:component/id :newuser :ui/new?] false)
                     ;; add the newly created user to our local user-list
                     (swap! state update-in [:component/id :user-list :user-list/users] conj user))
                   ;; If there is no error and also no :user/id returned, something is very wrong,
                   (throw "Server not returning new id"))))))


(defn valid-user?
  "Check the validity of a field in a NewUser form"
  [{:newuser/keys [name role password] :as user} field]
  (js/console.log "valdiating user" user)
  (try
    (case field
     :newuser/name (spec/valid? :user/name name)
     :newuser/role true ;; not checked
     :newuser/password (spec/valid? :user/password password)
     false)
    (catch :default _
      false)))

;; A Fulcro form validator
;; A field must be marked as complete for the validator to work
(def user-validator (fs/make-validator valid-user?))


;; Try to create a new user
#_{:clj-kondo/ignore [:unresolved-symbol]}
(defmutation try-add-user [{:newuser/keys [id name role password] :as params}]
  (action [{:keys [app state]}]
          (let [state-map       @state
                ident           [:component/id :newuser]
                ;; Mark the form as complete, so the form validator can work
                completed-state (fs/mark-complete* state-map ident)
                item            (get-in completed-state ident)
                NewUser         (comp/registry-key->class :app.client/NewUser)
                user-props      (fdn/db->tree (comp/get-query NewUser) item completed-state)
                valid?          (= :valid (user-validator user-props))]
            (js/console.log "newuser completed item:" item)
            (if valid?
              ;; if the form is valid, we sends the add-user request
              (comp/transact! app [(add-user {:user/id id
                                              :user/name name
                                              :user/role (if (s/blank? role) :user (-> role (s/trim) (s/lower-case)))
                                              :user/password password})])
              ;; if the form is invalid, we simply mark the form as complete, and display errors on UI
              (reset! state completed-state)))))


;; Login using user name and password
#_{:clj-kondo/ignore [:unresolved-symbol]}
(defmutation login [{:user-login/keys [name password] :as params}]
  (remote [env] true)
  (ok-action [{:keys [state result app] :as env}]
             (let [id                          (get-in result [:body (symbol ::login) :login-user/id])
                   {:keys [reason violations]} (get-in result [:body (symbol ::login)])]
               (js/console.log "env" (keys env))
               (if reason
                 ;; Show errors if there is error message in the response
                 (swap! state update-in [:component/id :user-login] (fn [data] (assoc data :error/reason reason :error/violations violations)))
                 ;; Login success,
                 (let [User (comp/registry-key->class :app.client/User)]
                   (when-not id
                     ;; No error and no :user/id returned, this shouldn't happen
                     (js/console.log "ID not found in response" (get-in result [:body])))
                   ;; we load user-list data
                   (df/load! app :all-users User {:target [:component/id :user-list :user-list/users]})
                   ;; set logged-in state to true to hide the login form
                   (swap! state assoc :root/logged-in? true))))))


(comment
  (user-validator {:newuser/name ""})
  (valid-user? {:newuser/name ""} :newuser/name)
  (spec/valid? :user/name "")

  (spec/assert :user/password "vL")

  ;;
  )
