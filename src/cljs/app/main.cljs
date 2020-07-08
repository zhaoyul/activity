(ns app.main
  (:require
   [kee-frame.core :as kf]
   [re-frame.core :as rf]
   ["@material-ui/core/styles" :refer [StylesProvider createMuiTheme ThemeProvider]]
   #_[components.error-boundry :refer [catch]]
   ["@material-ui/core/CssBaseline" :default CssBaseline]))


(def theme (createMuiTheme #js {:palette #js {:type "dark"}}) )
(defn root-view []
  [:> ThemeProvider {:theme theme}
   [:> StylesProvider {:inject-first true}
    [:> CssBaseline]
    [kf/switch-route (fn [route] (-> route :data :name))
     :home [:h1 "hello world"]
     nil [:h1 "not found"]]]])


(def ^:private routes [["/" :home]])


(defn main! []
  (kf/start! {:routes routes
              :initial-db {}
              :root-component [root-view]
              :debug? true}))

