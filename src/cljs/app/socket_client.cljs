(ns app.socket-client
  (:require
   [re-frame.core :as rf]
   [kee-frame.core :as kf]
   [taoensso.encore :as encore :refer-macros (have have?)]
   [taoensso.timbre :as timbre :refer-macros (tracef debugf infof warnf errorf)]
   [taoensso.sente  :as sente  :refer (cb-success?)]))


;; ws 处理

(timbre/set-level! :debug)

(defn ->output! [fmt & args]
  (let [msg (apply encore/format fmt args)]
    (timbre/info msg)))

(let [ ;; For this example, select a random protocol:
      rand-chsk-type :ws
      _ (->output! "Randomly selected chsk type: %s" rand-chsk-type)

      ;; Serializtion format, must use same val for client + server:
      packer :edn        ; Default packer, a good choice in most cases
      ;; (sente-transit/get-transit-packer) ; Needs Transit dep
      {:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket-client!
       "/chsk"                    ; Must match server Ring routing URL
       ""
       {:port 7000
        :type   rand-chsk-type
        :packer packer})]

  (def chsk       chsk)
  (def ch-chsk    ch-recv)           ; ChannelSocket's receive channel
  (def chsk-send! send-fn)           ; ChannelSocket's send API fn
  (def chsk-state state)             ; Watchable, read-only atom
  )



(defmulti -event-msg-handler  "Multimethod to handle Sente `event-msg`s"
  :id                                   ; Dispatch on event-id
  )

(defn event-msg-handler
  "Wraps `-event-msg-handler` with logging, error catching, etc."
  [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg))

(defmethod -event-msg-handler
  :default ; Default/fallback case (no other matching handler)
  [{:as ev-msg :keys [event]}]
  (->output! "Unhandled event: %s" event))

(defmethod -event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (let [[old-state-map new-state-map] (have vector? ?data)]
    (if (:first-open? new-state-map)
      (->output! "Channel socket successfully established!: %s" new-state-map)
      (->output! "Channel socket state change: %s"              new-state-map))))

(defmethod -event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (->output! "Push event from server: %s" ?data))

(defmethod -event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (->output! "Handshake: %s" ?data)))

(defonce router_ (atom nil))
(defn  stop-router! [] (when-let [stop-f @router_] (stop-f)))
(defn start-router! []
  (stop-router!)
  (reset! router_
          (sente/start-client-chsk-router!
           ch-chsk event-msg-handler)))

(defn start! [] (start-router!))

(defonce _start-once (start!))

(comment
  (a/go-loop []
    (println (a/<! ch-chsk))
    (recur))
  )



(rf/reg-fx :ws-event
           (fn [[msg-id event]]
             (chsk-send! [msg-id event])))

(rf/reg-fx :ws-event-cb
           (fn [[msg-id event success-id fail-id :as m] ]
             (prn "m:" m)
             (chsk-send! [msg-id event] 1000
                         (fn [cb-reply]
                           (when (cb-success? cb-reply)
                             (if cb-reply
                               (do
                                 (rf/dispatch [success-id cb-reply])
                                 (->output! "消息 %s 成功, 返回值:%s" m cb-reply))
                               (do
                                 (->output! "消息 %s 失败" m)
                                 (rf/dispatch [fail-id cb-reply]))))))))



(kf/reg-event-db
 :login-success
 (fn [db _]
   (assoc db :login {:state :logged-in
                     :busy? false
                     :error nil
                     :username "admin"} )))

(kf/reg-event-fx
 :login-failed
 (fn [cofx _]
   (js/alert "login failed...")))

(kf/reg-event-db
 :logout-success
 (fn [db _]
   (assoc db :login {:state :logged-out
                     :busy? false
                     :error nil
                     :username nil} )))

(kf/reg-event-fx
 :logout-failed
 (fn [cofx _]
   (js/alert "logout failed...")))


;; 阀


;; 获取所有的阀的状态
(kf/reg-event-fx
 :all-valve-status
 (fn [cofx _]
   {:ws-event-cb [:user/get-all-valve-status nil :all-valve-statust-success nil]})
 )

(kf/reg-event-db
 :all-valve-statust-success
 (fn [db [valve-status]] ;; FIXME: not tested yet.
   (assoc-in db [:global-status :valves]  valve-status )))

(comment
  (rf/dispatch [:all-valve-status ])
  )

;; 打开阀门

(kf/reg-event-fx
 :open-valve
 (fn [cofx [vs]]

   {:db (update-in (:db cofx)
                   [:global-status :valves]
                   merge (zipmap vs (repeat (count vs) :open )))
    :ws-event [:user/open-valve vs]}))


(comment
  (rf/dispatch [:open-valve [:v1 :v2] ])
  )

;; 关闭阀门
(kf/reg-event-fx
 :close-valve
 (fn [cofx [vs]]

   {:db (update-in (:db cofx)
                   [:global-status :valves]
                   merge (zipmap vs (repeat (count vs) :close )))
    :ws-event [:user/close-valve vs]}))


;; 关闭所有
(kf/reg-event-fx
 :close-qualification-page
 (fn [cofx  _]
   {:db (assoc-in (:db cofx)
                  [:global-status :valves]
                  {})
    :syncdb nil
    :ws-event [:user/close-all-valve]}))

;; 回零
;;:user/zero-pos
(kf/reg-event-fx
 :zero-pos
 (fn [cofx  _]
   {:ws-event [:user/zero-pos]}))

(kf/reg-event-fx
 :send-program
 (fn [cofx [event]]
   {:ws-event [:user/run-program event]}))


;; 设置flowrate
:send-flowrate

(kf/reg-event-fx
 :send-flowrate
 (fn [cofx [event]]
   {:ws-event [:user/send-flowrate event]}))


(kf/reg-event-fx
 :break
 (fn [cofx _]
   {:ws-event [:user/break nil]}))

(kf/reg-event-fx
 :continue
 (fn [cofx _]
   {:ws-event [:user/continue nil]}))

#_(kf/reg-event-fx
   :login-test
   (fn [cofx [[msg-id event ]]]
     {:ws-event-cb [msg-id event :login-success :login-failed]})
   )

(comment

  (kf/reg-event-fx
   :test-evt
   (fn [cofx [[msg-id event]]]
     (prn "msg-id:" msg-id)
     {:ws-event [msg-id event]})
   )


  )


#_(rf/reg-fx
   :ws-with-response
   (fn [msg-id event]
     (chsk-send! [msg-id event] 1000
                 (fn [cb-reply]
                   (when (cb-success? cb-reply)
                     (if cb-reply
                       (->output! "成功登陆")
                       (->output! "用户名密码不匹配")))))))
