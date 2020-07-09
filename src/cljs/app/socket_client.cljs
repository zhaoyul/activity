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

(kf/reg-event-db
 :init-msg
 (fn [db [event]]
   (prn "init......db with msg")
   (assoc db :msgs event)))

(rf/reg-sub
 :all-msg
 (fn [db]
   (:msgs db)))

(rf/reg-event-db
 :add-msg
 (fn [db [_ event]]
   (prn "event:" event)
   (assoc db :msgs
          (conj (:msgs db )
                event))))


(defn give-me-all []
  (chsk-send! [:user/get-all-msgs nil]
              1000
              (fn [cb-reply]
                (when (cb-success? cb-reply)
                  (if cb-reply
                    (do
                      (rf/dispatch [:init-msg cb-reply])
                      (prn "success:" cb-reply))
                    (do
                      (prn "failed:" cb-reply)
                      ))))))


(defmethod -event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (let [[old-state-map new-state-map] (have vector? ?data)]
    (if (:first-open? new-state-map)
      (do
        (prn "........................")
        (->output! "Channel socket successfully established!: %s" new-state-map)
        (give-me-all))
      (->output! "Channel socket state change: %s"              new-state-map))))

(defmethod -event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (->output! "Push event from server: %s" ?data)
  (let [[event-id event-body] ?data]
    (case :msg/broad-cast
      (rf/dispatch [:add-msg event-body]))))

(defmethod -event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (->output! "Handshake: %s" ?data)))


;; :msg/broad-cast

(defmethod -event-msg-handler :msg/broad-cast
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (rf/dispatch [:add-msg ?data])))


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




#_(rf/reg-fx
   :ws-with-response
   (fn [msg-id event]
     (chsk-send! [msg-id event] 1000
                 (fn [cb-reply]
                   (when (cb-success? cb-reply)
                     (if cb-reply
                       (->output! "成功登陆")
                       (->output! "用户名密码不匹配")))))))
