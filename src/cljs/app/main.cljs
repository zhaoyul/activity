(ns app.main
  (:require
   [kee-frame.core :as kf]
   [re-frame.core :as rf]
   [reagent.core :as r]
   [app.socket-client :as client]
   ["@material-ui/core/styles" :refer [StylesProvider createMuiTheme ThemeProvider makeStyles]]
   ["@material-ui/core/Tabs" :default Tabs]
   ["@material-ui/core/Tab" :default Tab]
   ["@material-ui/core/Box" :default Box]
   ["@material-ui/core/Button" :default Button]
   ["@material-ui/core/Paper" :default Paper]
   ["@material-ui/core/Input" :default Input]
   ["@material-ui/core/Divider" :default Divider]
   ["@material-ui/core/IconButton" :default IconButton]
   ["@material-ui/icons/Menu" :default Menu]
   ["@material-ui/icons/Directions" :default Directions]
   ["react-chat-elements" :refer [MessageBox MessageList]]
   ["videojs-for-react" :default VideoJsForReact]
   ["@material-ui/core/CssBaseline" :default CssBaseline]))

(def theme (createMuiTheme #js {:palette #js
                                {;;:type "dark"
                                 }}) )

(def tab-select-val  (r/atom 0))

(def screen-width (.. js/window -screen -width))
(def video-height (int (* screen-width (/ 9 16) )))

(defn home []
  [:> Box {:display :flex
           :height "100vh"
           :flex-direction "column"}
   [:> Box {:height "300px"
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

       :sources [{;;:src "http://xhlive.3vyd.com/live/007.m3u8"
                  :src "http://devimages.apple.com/iphone/samples/bipbop/bipbopall.m3u8"
                  :type 'application/x-mpegURL',
                  :label 'HLS1',
                  :withCredentials false,
                  :res 960}]}]]]
   [:> Box {:flex "1 0 auto"}
    [:> Tabs {:width "100%"
              :value @tab-select-val
              :on-change (fn [_ new-val]
                           (reset! tab-select-val new-val))}
     [:> Tab {:style {:width "50%"}
              :label (r/as-element [:div {:style {:font-size :large}} "互动"] )}
      [:> Box
       "hello"]]
     [:> Tab {:style {:width "50%"}
              :label (r/as-element [:div {:style {:font-size :large}} "会议详情"])}
      ]]
    [:> Box
     (if (zero? @tab-select-val)
       [:> Box
        [:> MessageList
         {:className "message-list"
          :lockable true
          :toBottomHeight "100%"
          :dataSource [{
                        :position "right"
                        :type "text"
                        :text "变态反应科中的“变态”是指免疫机制失去常态，这个科室专门治疗呼吸系统、皮肤、五官、心血管、消化系统等器官的过敏性疾病，涉及过敏性鼻炎、儿童哮喘、过敏性皮肤病、荨麻疹、过敏性紫癜、花粉症等数十种疾病。"
                        :date (js/Date.)
                        }
                       {
                        :position "right"
                        :type "text"
                        :text "主要治疗呼吸系统、皮肤、心血管、消化系统等器官的过敏性疾病，涉及过敏性鼻炎、儿童哮喘、过敏性皮肤病、荨麻疹、过敏性紫癜、花粉症等数十种疾病。"
                        :date (js/Date.)
                        }
                       {
                        :position "left"
                        :type "text"
                        :text "Lorem ipsum dolor sit amet, consectetur adipisicing elit"
                        :date (js/Date.)
                        }]}]]
       [:img {:width "100%"
              :src "/imgs/intro.jpg"}])
     ]]])

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

