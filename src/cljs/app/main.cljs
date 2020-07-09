(ns app.main
  (:require
   [kee-frame.core :as kf]
   [re-frame.core :as rf]
   [reagent.core :as r]
   [app.socket-client :as client]
   [taoensso.sente  :as sente  :refer (cb-success?)]
   ["@material-ui/core/styles" :refer [StylesProvider createMuiTheme ThemeProvider makeStyles]]
   ["@material-ui/core/Tabs" :default Tabs]
   ["@material-ui/core/Tab" :default Tab]
   ["@material-ui/core/Box" :default Box]
   ["@material-ui/core/Button" :default Button]
   ["@material-ui/core/Paper" :default Paper]
   ["@material-ui/core/Input" :default Input]
   ["@material-ui/core/InputBase" :default InputBase]
   ["@material-ui/core/Divider" :default Divider]
   ["@material-ui/core/IconButton" :default IconButton]
   ["@material-ui/icons/Menu" :default Menu]
   ["@material-ui/icons/Directions" :default Directions]
   ["@material-ui/icons/Search" :default SearchIcon]
   ["@material-ui/icons/Directions" :default DirectionsIcon]
   ["react-chat-elements" :refer [MessageBox MessageList]]
   ["videojs-for-react" :default VideoJsForReact]
   ["@material-ui/core/CssBaseline" :default CssBaseline]))

(def theme (createMuiTheme #js {:palette #js
                                {;;:type "dark"
                                 }}) )

(def user-nname (str "user-" (rand-int 10000)))

(def useStyles
  (makeStyles
   (fn [theme]
     (clj->js {:root {:padding "2px 4px",:display "flex",:alignItems "center",:width 400,},
               :input {:marginLeft (.spacing theme 1) :flex 1,},
               :iconButton {:padding 10,},
               :divider {:height 28,:margin 4,}}))))

(def tab-select-val  (r/atom 0))

(def screen-width (.. js/window -screen -width))
(def screen-height (.. js/window -screen -height))
(def video-height (int (* screen-width (/ 9 16) )))
(def tab-height 45)
(def input-height 45)
(def buttom-height (- screen-height
                      video-height
                      tab-height
                      input-height))

(defn msg->data [msgs]
  (mapv (fn [msg]
          {:position "right"
           :type "text"
           :text msg
           :date (js/Date.)})
        (mapv :msg msgs)))

(def current-msg (r/atom ""))

(defn input-send []
  (r/as-element
   (let [classes (useStyles) ]
     [:> Box {:align-self :flex-end}
      [:> Paper {:component "form"
                 :class-name (.-root classes)}
       [:> InputBase {:placeholder "请输入您的发言"
                      :on-change (fn [event ]
                                   (prn "....")
                                   (reset! current-msg (.. event -target -value) ))
                      :class-name (.-input classes)}]

       [:> Divider {:orientation "vertical"
                    :class-name (.-divider classes)}]
       [:> Button {:color "primary"
                   :aria-label "directions"
                   :onClick (fn []
                              (prn "发出新消息...")
                              (when-not (empty? @current-msg)
                                (client/chsk-send! [:user/new-msg @current-msg])
                                (reset! current-msg "")))}
        "提交"
        [:> DirectionsIcon]]]])))


(defn message []
  (fn []
    [:> Box #_{:height (str buttom-height "px")}
     [:> MessageList
      {:className "message-list"
       :lockable true
       :toBottomHeight "100%"
       :dataSource (msg->data @(rf/subscribe [:all-msg]))}]
     ]))

(defn buttom []
  (if (zero? @tab-select-val)
    [:img {:width "100%"
           :src "/imgs/intro.jpg"}]
    [message]))

(defn input []
  (when-not (zero? @tab-select-val)
    [:> input-send]))


(defn video []
  [:> Box {:height (str video-height "px")
           :display :flex
           :width "100%"}
   [:> Box {:width "100%"}
    [:> VideoJsForReact
     {:stype {:width "100%"}
      :sourceChanged #(js/console.log %)
      :onReady #(js/console.log "准备完毕" %)
      :preload "auto"
      :width (.. js/window -screen -width)
      :autoplay true
      :controls true
      :playbackRates [1, 1.5, 2]

      :sources [{:src "http://xhlive.3vyd.com/live/007.m3u8"
                 ;;:src "http://devimages.apple.com/iphone/samples/bipbop/bipbopall.m3u8"
                 :type 'application/x-mpegURL',
                 :label 'HLS1',
                 :withCredentials false,
                 :res 960}]}]]])

(defn tab []
  [:> Box {:flex "1 0 auto"
           :height "45px"}
   [:> Tabs {:width "100%"
             :value @tab-select-val
             :on-change (fn [_ new-val]
                          (reset! tab-select-val new-val))}
    [:> Tab {:style {:width "50%"}
             :label (r/as-element [:div {:style {:font-size :large}} "会议详情"])}]
    [:> Tab {:style {:width "50%"}
             :label (r/as-element [:div {:style {:font-size :large}} "互动"] )}]]])

(defn home []
  [:> Box {:display :flex
           :postition :relative
           :height "100vh"
           :flex-direction "column"}
   [video]
   [tab]
   [buttom]
   [input]])

(defn root-view []
  [:> ThemeProvider {:theme theme}
   [:> StylesProvider {:inject-first true}
    [:> CssBaseline]
    [kf/switch-route (fn [route] (-> route :data :name))
     :home [home]
     nil [:h1 "not found"]]]])


(def ^:private routes [["/" :home]])

(defn main! []
  (kf/start! {:routes routes
              :initial-db {}
              :root-component [root-view]
              :debug? true}))

