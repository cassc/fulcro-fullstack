(ns app.server
  (:gen-class)
  (:require
   [app.model.user :as user]
   [org.httpkit.server :as http]
   [com.fulcrologic.fulcro.server.api-middleware :as fmw :refer [not-found-handler wrap-api]]
   [com.wsscode.pathom.connect :as pc]
   [com.wsscode.pathom.core :as p]
   [ring.middleware.content-type :refer [wrap-content-type]]
   [ring.middleware.not-modified :refer [wrap-not-modified]]
   [ring.middleware.resource :refer [wrap-resource]]))

;; Pathom resolvers
(def my-resolvers [user/resolvers])

(defn wrap-index
  "Rewrite the root uri to `index.html`"
  [handler]
  (fn [{:keys [uri] :as req}]
    (if (= uri "/")
      (handler (assoc req :uri "/index.html"))
      (handler req))))


;; setup for a given connect system
(def parser
  (p/parser
    {::p/env     {::p/reader                 [p/map-reader
                                              pc/reader2
                                              pc/open-ident-reader]
                  ::pc/mutation-join-globals [:tempids]}
     ::p/mutate  pc/mutate
     ::p/plugins [(pc/connect-plugin {::pc/register my-resolvers})
                  (p/post-process-parser-plugin p/elide-not-found)
                  p/error-handler-plugin]}))

;; Our ring application
(def middleware (-> not-found-handler
                    (wrap-api {:uri    "/api"
                               :parser (fn [query] (parser {} query))})
                    (fmw/wrap-transit-params)
                    (fmw/wrap-transit-response)
                    (wrap-resource "public")
                    wrap-content-type
                    wrap-not-modified
                    wrap-index))

;; Entry point for our backend application
;; Start HTTP server
(defn -main [& _]
  (http/run-server middleware {:port 3000}))

(comment
  parser
  (parser {} [{:all-users [:user/id  :user/name :user/role]}])
  (parser {} [{[:user/id 1] [:user/role]}])
  (parser {} '[(app.model.user/add-user {:user/id 0 :user/role :user :user/name "faultuser" :user/password "dasf@L2232"})])
  (parser {} `[(app.model.user/login {:user/name "faultuser" :user/password "dasf@L2232"})])
  (parser {} `[(app.model.user/add-user ~{:user/name "faultuser" :user/password "dasf@L2232"})])
  ;;
  )
