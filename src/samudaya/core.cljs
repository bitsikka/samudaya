(ns ^figwheel-hooks samudaya.core
  (:require
   [goog.dom :as gdom]
   ["status-stream" :as st-stream]
   ["react-virtualized" :as rv :refer [List AutoSizer CellMeasurer]]
   ["server-name-generator" :as server-name]
   [goog.string :as gstring]
   [cljs-bean.core :refer [->clj ->js]]
   [async-await.core :refer [async await]]
   [cljs.core.async :refer [go]]
   ["moment" :as moment]
   [cljs.core.async.interop :refer-macros [<p!]]
   [uix.core.alpha :as uix :refer [defcontext]]
   [uix.dom.alpha :as uix.dom]
   [xframe.core.alpha :as xf :refer [<sub]]
   [js-waku :refer [WakuMessage ChatMessage]]))

;; (set! *warn-on-infer* true)
(enable-console-print!)

(defcontext *waku-context* {:waku nil})

(def use-waku
  (fn []
    (uix/context *waku-context*)))

;; delete this later - referred in test
(defn multiply [a b] (* a b))

(def chat-content-topic "xyz")

(defn tweets [rows]
  (let [cache        (rv/CellMeasurerCache. #js {:fixedWidth    true
                                                 :defaultHeight 117})
        row-renderer (fn [row]
                       (let [{:keys [index key parent style]}
                             (js->clj row :keywordize-keys true)
                             msg       (nth rows index)
                             timestamp (:timestamp msg)
                             keyz      (+ timestamp key)
                             message   (:message msg)
                             nick      (:nick msg)]
                         (uix/as-element
                           [:> CellMeasurer
                            {:cache       cache
                             :columnIndex 0
                             :key         key
                             :style       style
                             :parent      parent
                             :rowIndex    index}
                            [:.col-middle__post__tweet
                             [:<>
                              [:.col-middle__post__img__profile
                               [:img {:src "img/freeCodeCamp.org.jpg" :alt "Profile"}]]
                              [:.col-middle__post__tweet__content
                               [:.col-middle__post__tweet__content_top
                                [:h2.author_name nick]
                                [:span.checked-user [:i.bx.bxs-badge-check]]
                                [:span.author_account (str "@" (:nick msg))]
                                [:span.date [:span (gstring/unescapeEntities "&bull;")] (.fromNow (moment timestamp))]]
                               [:.col-middle__post__tweet__content_text
                                [:p message]
                                [:.content__action
                                 [:span.comment
                                  [:i.far.fa-comment]
                                  [:span.comment-number]]
                                 [:span.retweet
                                  [:i.fas.fa-retweet]
                                  [:span.retweet-number]]
                                 [:span.favorite
                                  [:i.far.fa-heart]
                                  [:span.favorite-number]]
                                 [:span.external-link
                                  [:i.bx.bx-link-external]]]]]
                              [:span.more-info [:i.fas.fa-chevron-down]]]]])))]
    [:> AutoSizer {:disableHeighht true
                   ;; :disableWidth   true
                   :style          {:height "100%"}}
     (fn [sizer]
       (uix/as-element
         [:> List
          {:height                   (aget sizer "height")
           :width                    (aget sizer "width")
           :deferredMeasurementCache cache
           :overscanRowCount         20
           :rowCount                 (count rows)
           :rowHeight                (.-rowHeight cache)
           :rowRenderer              row-renderer}]))]))

#_(defn send-message [msg waku]
   (let [chat-msg (.fromUtf8String
                    ChatMessage
                    (js/Date.)
                    "demo"
                    msg)
         waku-msg (.fromBytes
                    WakuMessage
                    (.encode chat-msg)
                    chat-content-topic)]
     (.send (.-relay waku) waku-msg)))

(defn message-input [nick]
  (let [input-text (uix/state "")
        waku       (use-waku)
        send-message
        (fn [msg]
          (let [chat-msg (.fromUtf8String
                           ChatMessage
                           (js/Date.)
                           nick
                           msg)
                waku-msg (.fromBytes
                           WakuMessage
                           (.encode chat-msg)
                           chat-content-topic)]
            (.send (.-relay waku) waku-msg)))]
    ;; (js/console.log (js-keys waku))
    [:.col-middle__post
     [:<>
      [:.col-middle__post__img__profile
       [:img {:src "img/Beans Avatar.jpg" :alt "Profile"}]]
      [:.col-middle__post__content
       [:textarea#textarea
        {:placeholder "What's happening?"
         :value       @input-text
         :on-change   (fn [e] (reset!
                               input-text
                               (.. e -target -value)))
         :on-key-down (fn [e]
                        ;; (js/console.log send-message)
                        (when (and (not= @input-text "") (= "Enter" (.-key e)) (not (.-shiftKey e)))
                          (.preventDefault e)
                          (send-message @input-text)
                          (reset! input-text "")))}]
       [:span.col-middle__post__content__btn
        [:i.fas.fa-globe-americas]
        [:h3 "Everyone can reply"]]
       [:.line]
       [:.col-middle__post__content__bottom
        [:.bottom__left
         [:span [:i.fas.fa-image]]
         [:span [:i.bx.bxs-file-gif]]
         [:span [:i.bx.bx-poll]]
         [:span [:i.far.fa-smile]]
         [:span [:i.far.fa-calendar-alt]]]
        [:button.bottom-twett
         {:on-click #(do
                       (when-not (= @input-text "")
                         (send-message @input-text)
                         (reset! input-text "")))}
         "Tweet"]]]]]))

(def archived-messages (atom []))
(defn app []
  (let [waku     (<sub [:get :waku])
        messages (<sub [:get :messages])
        nick     (uix/state (server-name/generate))]
    ;; (js/console.log (js-keys waku))
    ;; (js/console.log (str "messages:\n" (take-last 10 messages)))
    (uix/context-provider
      [*waku-context* waku]
      [:.row
       [:<>
        [:.col.col-left
         [:<>
          [:ul
           [:a.logo {:href "#"} [:img {:src "img/logo.png"}]]
           [:li [:a.active [:i.bx.bxs-home-circle][:span.menu-text "Home"]]]
           [:li [:a [:i.fas.fa-hashtag][:span.menu-text "Explore"]]]
           [:li [:a.notification [:i.far.fa-bell][:i [:span.number 13]]
                 [:span.menu-text "Notifications"]]]
           [:li [:a [:i.far.fa-envelope][:span.menu-text "Messages"]]]
           [:li [:a [:i.bx.bx-bookmark][:span.menu-text "Bookmarks"]]]
           [:li [:a [:i.bx.bx-list-ul][:span.menu-text "Lists"]]]
           [:li [:a [:i.far.fa-user][:span.menu-text "Profile"]]]
           [:li [:a [:i.bx.bx-dots-horizontal-rounded][:span.menu-text "More"]]]
           [:li [:button]]]
          [:.btn-account
           [:<>
            [:.account-info
             [:.img
              [:img {:src "img/Beans Avatar.jpg" :alt "Perfil"}]]
             [:.account-info__name [:h2 @nick][:span (str "@" @nick)]]]
            [:i.fas.fa-chevron-down]]]]]
        [:.col.col-middle
         [:<>
          [:.col-middle__top [:h1 "Home"][:span [:i.bx.bxs-star-half]]]
          [message-input @nick]
          [:hr.col-middle__principal_line]
          [tweets (reverse (sort-by :timestamp (distinct messages)))]]]
        [:.col.col-right
         [:<>
          [:.search_bar
           [:span.search-icon [:i.bx.bx-search]]
           [:input {:type "text" :placeholder "Search Samudaya"}]]]]]])))

(defn archived [messages]
  (doseq [message (reverse (->clj messages))]
    (let [chat-msg {:timestamp (.valueOf (. message -timestamp))
                    :message   (. message -payloadAsUtf8)
                    :nick      (. message -nick)}]
      (xf/dispatch [:merge chat-msg]))))

(xf/reg-fx
  :init-waku
  (fn [_ [_ {:keys [on-receive]}]]
    (go (let [waku (<p! (.default st-stream chat-content-topic archived))]
          (xf/dispatch [:set [:waku] waku])
          (-> (.. waku -relay)
              (.addObserver
                (fn [waku-msg]
                  (when-let [payload (.. waku-msg -payload)]
                    (let [chat-msg (->> payload
                                        (.decode ChatMessage))
                          message  {:timestamp (.valueOf (. chat-msg -timestamp))
                                    :message   (. chat-msg -payloadAsUtf8)
                                    :nick      (. chat-msg -nick)}]
                      (when chat-msg
                        (xf/dispatch [on-receive message])))))
                (clj->js [chat-content-topic])))))))

(xf/reg-event-fx
  :db/init
  (fn [_ _]
    {:init-waku {:on-receive :merge}
     :db        {:waku     nil
                 :messages []}}))

(xf/reg-event-db
  :set
  (fn [db [_ ks v]]
    (assoc-in db ks v)))

(xf/reg-event-db
  :merge
  (fn [db [_ data]]
    ;; (js/console.log (str (:messagees db)))
    (when (not (some #(= data %) (:messagees db)))
      (update db :messages conj data))))

(xf/reg-sub
  :get
  (fn [& ks]
    (get-in (xf/<- [::xf/db]) ks)))

(defn init-fn []
  (xf/dispatch [:db/init]))

(defn mount-app-element []
  (when-let [el (gdom/getElement "app")]
    (uix.dom/render [app] el)))

(defn ^:after-load on-reload []
  (js/console.log "reload")
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )

(mount-app-element)
(defonce init (init-fn ))
