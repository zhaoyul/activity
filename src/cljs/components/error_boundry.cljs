(ns components.error-boundary
  "参照官方文档:https://github.com/reagent-project/reagent/blob/master/doc/ReactFeatures.md#hooks"
  (:require  [reagent.core :as r]
             [clojure.string :as string]))

(defn- render-error-component [{:keys [error info]}]
  [:div
   {:style {:width           "100%"
            :min-width       300
            :backgroundColor "rgba(255,0,0,0.2)"
            :padding         8}}
   [:h6 error]
   [:pre info]])

(def ^:dynamic *render-error* render-error-component)

(defn catch []
  (defn error-boundary [comp]
    (r/create-class
     {:constructor (fn [this props]
                     (set! (.-state this) #js {:error nil}))
      :component-did-catch (fn [this e info])
      :get-derived-state-from-error (fn [error] #js {:error error})
      :render (fn [this]
                (r/as-element
                 (if-let [error (.. this -state -error)]
                   [:div
                    "Something went wrong."
                    [:button {:on-click #(.setState this #js {:error nil})} "Try again"]]
                   comp)))})))
