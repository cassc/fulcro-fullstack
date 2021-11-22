(ns app.client
  (:require
   [app.model.user :refer [try-add-user hide-newuser-ui show-newuser-ui user-validator login]]
   [app.model.specs]
   [com.fulcrologic.fulcro.algorithms.merge :as merge]
   [cljs.spec.alpha :as spec]
   [clojure.string :as s]
   [com.fulcrologic.fulcro.application :as app :refer [mount!]]
   [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
   [com.fulcrologic.fulcro.data-fetch :as df]
   [com.fulcrologic.fulcro.dom :as dom :refer [button div span h3 input table tbody td th thead tr]]
   [com.fulcrologic.fulcro.mutations :as m]
   [com.fulcrologic.fulcro.algorithms.denormalize :as fdn]
   [com.fulcrologic.fulcro.algorithms.form-state :as fs]
   [com.fulcrologic.fulcro.networking.http-remote :as http]))

;; simple error formatting
(defn format-error [reason violations]
  (str (name reason)
       "\n\n"
       (s/join "\n  "(map name violations))))


(defsc User [_ {:user/keys [id name role]}]
  {:query [:user/id :user/role :user/name]
   :ident :user/id}
  (tr
   (td name)
   (td (clojure.core/name role))))

(def ui-user (comp/factory User {:keyfn :user/id}))


(defn user-input-field [this field input-type label value error-msg]
  (div :.field
       (div :.ui.labeled.input
            (div :.ui.label label)
            (input :.ui.input {:value value :type input-type
                               :onChange #(m/set-string! this field :event %)}))
       (div :.ui.up.pointing.red.basic.label
            {:classes [(when (not= :invalid (user-validator (comp/props this) field)) "hidden")]}
            error-msg)))

(defsc NewUser [this {:ui/keys      [new?]
                      :error/keys   [violations reason]
                      :newuser/keys [id role name password]}]
  {:query         [:newuser/id :newuser/role :newuser/password :newuser/name :ui/new?
                   :error/reason :error/violations
                   fs/form-config-join]
   :form-fields #{:newuser/role :newuser/password :newuser/name}
   :initial-state {:newuser/id       :param/id
                   :newuser/role     :param/role
                   :newuser/name     :param/name
                   :newuser/password :param/password
                   :ui/new?          :param/new?
                   :error/reason     nil
                   :error/violations []}
   :pre-merge   (fn [{:keys [data-tree]}] (fs/add-form-config NewUser data-tree))
   :ident         (fn [] [:component/id :newuser])}
  (div
   :.ui.form.segment
   {:style {:paddingTop "12px"}}
   ;; (js/console.log "Render NewUser" props)
   (if new?
     (div :.ui.form
          (div :.ui.header (span "Add a new user"))
          (user-input-field this :newuser/name :text "Username" (or name "") "Username must not be empty!")
          (user-input-field this :newuser/role :text "Role" role nil)
          (user-input-field this :newuser/password :password "Password" password "Invalid password")
          ;; Server side error, can be removed
          (when reason
            (div :.ui.field
                 (div :.ui.red.basic.label
                      "Error: "
                      (format-error reason violations))))
          (div :.ui.field
               (button :.ui.primary.button
                       {:onClick (fn []
                                   (comp/transact! this [(try-add-user (comp/props this))]))}
                       "Add")
               (button :.ui.button
                       {:onClick #(hide-newuser-ui this NewUser)}
                       "Cancel")))
     (div
      (button :.ui.primary.button
              {:onClick #(show-newuser-ui this NewUser)}
              "Add user")))))

(def ui-new-user (comp/factory NewUser))


(defsc UserLogin [this {:user-login/keys [name password]
                        :error/keys [reason violations]}]
  {:query [:user-login/name :user-login/password :error/reason :error/violations]
   :form-fields #{:user-login/password :user-login/name}
   ;; :pre-merge   (fn [{:keys [data-tree]}] (fs/add-form-config UserLogin data-tree))
   :initial-state {:user-login/name ""
                   :user-login/password ""}
   :ident (fn [] [:component/id :user-login])}
  (div :.ui.form
       (div :.field
            (div :.ui.labeled.input
                 (div :.ui.label "Username")
                 (input :.ui.input {:value (or name "") :type :text
                                    :onChange #(m/set-string! this :user-login/name :event %)})))
       (div :.field
            (div :.ui.labeled.input
                 (div :.ui.label "Password")
                 (input :.ui.input {:value (or password "") :type :password
                                    :onChange #(m/set-string! this :user-login/password :event %)})))
       (when reason
         (div :.ui.field
              (div :.ui.red.basic.label
                   "Error: "
                   (format-error reason violations))))
       (div :.ui.field
            (button :.ui.primary.button
                    {:onClick (fn []
                                (comp/transact! this [(login (comp/props this))]))}
                    "Login"))))


(def ui-user-login (comp/factory UserLogin))

(defsc UserList [this {:user-list/keys [users]}]
  {:query         [{:user-list/users (comp/get-query User)}]
   :initial-state {:user-list/users []}
   :ident         (fn [] [:component/id :user-list])}
  (js/console.log "rendering" users)
  (div
   (table :.ui.table
          (thead
           (tr
            (th "Name")
            (th "Role")))
          (tbody
           (map ui-user users)))))

(def ui-user-list (comp/factory UserList))

(defsc Root [_ {:root/keys [user-list newuser logins logged-in?]}]
  {:query         [{:root/user-list (comp/get-query UserList)}
                   {:root/newuser (comp/get-query NewUser)}
                   :root/logged-in?
                   {:root/logins (comp/get-query UserLogin)}]
   :initial-state {:root/user-list  {}
                   :root/newuser    {}
                   :root/logged-in? false
                   :root/logins     {}}}
  (div :.ui.container {:style {:padding "2rem 0"}}
       (h3 "Fulcro App Demo")
       (if logged-in?
         (div :.ui.segment
              (h3 "User List")
              (ui-user-list user-list)
              (ui-new-user newuser))
         (ui-user-login logins))))

(defonce APP (app/fulcro-app {:remotes          {:remote (http/fulcro-http-remote {})}
                              :remote-error?    (fn [{:keys [status-code body]}]
                                                  (or
                                                   #_(has-reader-error? body)
                                                   (not= 200 status-code)))
                              :client-did-mount (fn [app]
                                                  (df/load! app :all-users User
                                                            {:target [:component/id :user-list :user-list/users]}))}))

(defn ^:export init
  "Shadow-cljs sets this up to be our entry-point function. See shadow-cljs.edn `:init-fn` in the modules of the main build."
  []
  (mount! APP Root "app")
  (js/console.log "Loaded"))

(defonce init! (delay (init)))

@init!

(defn ^:export refresh
  "During development, shadow-cljs will call this on every hot reload of source. See shadow-cljs.edn"
  []
  ;; re-mounting will cause forced UI refresh, update internals, etc.
  (mount! APP Root "app")

  ;; As of Fulcro 3.3.0, this addition will help with stale queries when using dynamic routing:
  (comp/refresh-dynamic-queries! APP)
  (js/console.log "Hot reload"))

(comment
  (df/load! APP [:user/id "defaultuser"] User)
  (df/load! APP :all-users User
            {:target [:component/id :user-list :user-list/users]})

;; Test if a field is valid
  (let [idt [:component/id :newuser]]
    (as-> {} s
      (merge/merge-component s NewUser {:newuser/id 0 :ui/new? true :newuser/role "user" :newuser/password "" :newuser/name ""})
      (fs/add-form-config* s NewUser idt)
      (assoc-in s [:component/id :newuser :newuser/name] "auser")
      (fs/mark-complete* s idt :newuser/name)
      (fdn/db->tree (comp/get-query NewUser) (get-in s idt) s)

      ;; check the validity of a field
      ;; (user-validator s :newuser/name)

      ;; list fields which have been changed
      (fs/dirty-fields s false)

      ;; (fs/dirty-fields s true)
      ))
;;
  )
