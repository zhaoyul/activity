(ns ws
  "websocket server, 负责和cljs的websocket的clicent通讯"
  {:author "Kevin"}
  (:require
   [ring.middleware.defaults]
   [compojure.core     :as comp :refer (defroutes GET POST)]
   [compojure.route    :as route]
   [jumblerg.middleware.cors :refer [wrap-cors]]
   [clojure.core.async :as async  :refer (<! <!! >! >!! put! chan go go-loop)]
   [taoensso.timbre    :as timbre :refer (tracef debugf infof warnf errorf)]
   [taoensso.sente     :as sente]
   [org.httpkit.server :as http-kit]
   [taoensso.sente.server-adapters.http-kit :refer (get-sch-adapter)]))

(timbre/set-level! :debug) ; Uncomment for more logging
(reset! sente/debug-mode?_ true) ; Uncomment for extra debug info

(def messages (atom [{:id 1
                      :msg "消息1"}
                     {:id 2
                      :msg "消息2"}]))

(let [packer                               :edn
      chsk-server                          (sente/make-channel-socket-server!
                                            (get-sch-adapter) {:packer packer
                                                               :csrf-token-fn nil})

      {:keys [ch-recv        ;; 接收消息的channel
              send-fn        ;; server -> client
              connected-uids ;; 已经连接的用户, 现在看起来是空的
              ajax-post-fn   ;; Post Ajax func
              ajax-get-or-ws-handshake-fn]} chsk-server]

  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
  (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
  (def connected-uids                connected-uids) ; Watchable, read-only atom
  )

;; We can watch this atom for changes if we like
(add-watch connected-uids :connected-uids
           (fn [_ _ old new]
             (when (not= old new)
               (infof "Connected uids change: %s" new))))


(def users [{:name "admin"
             :password "admin"}
            {:name "a"
             :password "a"}])

(defn login-handler
  "Here's where you'll add your server-side login/auth procedure (Friend, etc.).
  In our simplified example we'll just always successfully authenticate the user
  with whatever user-id they provided in the auth request."
  [ring-req]
  (let [{:keys [session params]} ring-req
        {:keys [user-id]} params]
    (debugf "Login request: %s" params)
    {:status 200 :session (assoc session :uid user-id)}))

(defroutes ring-routes
  (GET  "/chsk"  ring-req (ring-ajax-get-or-ws-handshake ring-req))
  (POST "/chsk"  ring-req (ring-ajax-post                ring-req))
  (POST "/login" ring-req (login-handler                 ring-req))
  (route/resources "/") ; Static files, notably public/main.js (our cljs target)
  (route/not-found "<h1>Page not found</h1>"))

(def my-defaults
  "A default configuration for a browser-accessible website, based on current
  best practice."
  {:params    {:urlencoded true
               :multipart  true
               :nested     true
               :keywordize true}
   :cookies   true
   :session   {:flash true
               :cookie-attrs {:http-only true, :same-site :strict}}
   :security  {:anti-forgery   false ;; 不需要anti-forge
               :xss-protection {:enable? true, :mode :block}
               :frame-options  :sameorigin
               :content-type-options :nosniff}
   :static    {:resources "public"}
   :responses {:not-modified-responses true
               :absolute-redirects     true
               :content-types          true
               :default-charset        "utf-8"}})

(def main-ring-handler
  (wrap-cors (ring.middleware.defaults/wrap-defaults
              ring-routes  my-defaults)
             #".*"))

;;;; Some server>user async push examples

(defn test-fast-server>user-pushes
  "Quickly pushes 100 events to all connected users. Note that this'll be
  fast+reliable even over Ajax!"
  []
  (doseq [uid (:any @connected-uids)]
    (doseq [i (range 100)]
      (chsk-send! uid [:fast-push/is-fast (str "hello " i "!!")]))))

(comment (test-fast-server>user-pushes))

(defonce broadcast-enabled?_ (atom true))

(defn broad-cast-new-msg [msg]
  (let [uids (:any @connected-uids)]
    (debugf "Broadcasting server>user: %s uids" (count uids))
    (doseq [uid uids]
      (chsk-send! uid
                  [:msg/broad-cast
                   msg]))) )

(defn start-example-broadcaster!
  "As an example of server>user async pushes, setup a loop to broadcast an
  event to all connected users every 10 seconds"
  []
  (let [broadcast!
        (fn [i]
          (let [uids (:any @connected-uids)]
            (debugf "Broadcasting server>user: %s uids" (count uids))
            (doseq [uid uids]
              (chsk-send! uid
                          [:some/broadcast
                           {:what-is-this "An async broadcast pushed from server"
                            :how-often "Every 10 seconds"
                            :to-whom uid
                            :i i}]))))]

    (go-loop [i 0]
      (<! (async/timeout 10000))
      (when @broadcast-enabled?_ (broadcast! i))
      (recur (inc i)))))

;;;; Sente event handlers

(defmulti -event-msg-handler
  "Multimethod to handle Sente `event-msg`s"
  :id ; Dispatch on event-id
  )

(defn event-msg-handler
  "Wraps `-event-msg-handler` with logging, error catching, etc."
  [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg) ; Handle event-msgs on a single thread
  ;; (future (-event-msg-handler ev-msg)) ; Handle event-msgs on a thread pool
  )

(defmethod -event-msg-handler
  :default ; Default/fallback case (no other matching handler)
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid     (:uid     session)]
    (infof "Unhandled event: %s session:%s" event session )
    (when ?reply-fn
      (?reply-fn {:umatched-event-as-echoed-from-server event}))))

(defmethod -event-msg-handler :example/test-rapid-push
  [ev-msg] (test-fast-server>user-pushes))

(defmethod -event-msg-handler :example/toggle-broadcast
  [{:as ev-msg :keys [?reply-fn]}]
  (let [loop-enabled? (swap! broadcast-enabled?_ not)]
    (?reply-fn loop-enabled?)))



(defonce router_ (atom nil))
(defn  stop-router! [] (when-let [stop-fn @router_] (stop-fn)))
(defn start-router! []
  (stop-router!)
  (reset! router_
          (sente/start-server-chsk-router!
           ch-chsk event-msg-handler
           {:csrf-token-fn nil})))

;;;; Init stuff

(defonce    web-server_ (atom nil)) ; (fn stop [])
(defn  stop-web-server! [] (when-let [stop-fn @web-server_] (stop-fn)))
(defn start-web-server! [& [port]]
  (stop-web-server!)
  (let [port 7000
        ring-handler (var main-ring-handler)

        [port stop-fn]
        (let [stop-fn (http-kit/run-server ring-handler {:port port})]
          [(:local-port (meta stop-fn)) (fn [] (stop-fn :timeout 100))])
        uri (format "http://localhost:%s/" port)]

    (infof "Web server is running at `%s`" uri)
    (reset! web-server_ stop-fn)))

(defn stop!  []  (stop-router!)  (stop-web-server!))
(defn start! [] (start-router!) (start-web-server!) #_(start-example-broadcaster!))


(comment
  (start!)
  (test-fast-server>user-pushes)
  )

(defn send-msg [msg]
  (chsk-send! :taoensso.sente/nil-uid msg))

(defn get-all-msgs []
  (chsk-send! :taoensso.sente/nil-uid [] ))

(defmethod -event-msg-handler :user/get-all-msgs
  [{:keys [?reply-fn event]}]
  (prn "返回现在全部....")
  (?reply-fn @messages ))



(defn new-msg [msg]
  (let [new-id (->> @messages
                    (map :id)
                    (apply max)
                    inc)]
    {:id new-id
     :msg msg}))


(defmethod -event-msg-handler :user/new-msg
  [{:keys [?reply-fn event]}]
  (prn "new-msg" event)
  (swap! messages conj (new-msg (second event)))
  (broad-cast-new-msg (new-msg (second event))))

(defn -main []
  (start!)
  (println "启动ws....")
  )
