(in-ns 'res-shower.core)

(def jimaku-canvas
  (canvas
   :background (seesaw.color/color 0 0 0 0)
   :font (seesaw.font/font :name "ＭＳ Ｐゴシック" :size 34)
   :paint draw-string*))

(def initial-click-x (atom 0))
(def initial-click-y (atom 0))

;; 枠の左上に表示されるやつ。ドラッグで移動可能。
;; 移動処理にはクリックした位置と現在位置との相対位置を使うと
;; 理想的な動きをする。
(def drag-text
  (doto (label :text software-name
               :background (seesaw.color/color 200 200 200 255))
    (seesaw.behave/when-mouse-dragged
        :start (fn [e]
                 (reset! initial-click-x (.getX e))
                 (reset! initial-click-y (.getY e)))
        :drag  (fn [e _]
                 (let [p (-> (MouseInfo/getPointerInfo) .getLocation)]
                   (.setLocation jimaku-window3
                                 (java.awt.Point.
                                  (- (.x p) @initial-click-x)
                                  (- (.y p) @initial-click-y))))))))

(def tune-window
  (frame :title "常に表示する文章"
         :icon (clojure.java.io/resource "icon2.png")))

(def tune-text
  (text :text ""
        :multi-line? true
        :font (seesaw.font/font :name "ＭＳ Ｐゴシック" :size 34)))

(def tune-panel
  (border-panel
   :center (scrollable tune-text)
   :south  (horizontal-panel 
            :items (list (button :text "CANECL"
                                 :listen [:action (fn [e] (hide! tune-window))])
                         (button :text "APPLY"
                                 :listen [:action (fn [e] (set-default-jimaku-text (value tune-text)))])
                         (button :text "OK"
                                 :listen [:action (fn [e] (set-default-jimaku-text (value tune-text))
                                                    (hide! tune-window))])))))
(config! tune-window :content tune-panel)
(.setPreferredSize tune-window (new java.awt.Dimension 400 300))

(def tune-button
  (button :text "常"
          :listen [:action (fn [e] (text! tune-text @default-jimaku-text)
                             (-> tune-window pack! show!))]))

(def reload-button
  (button :text "更新"
          :listen [:action reload]))

(def auto-reload-cbox
  (checkbox :text "自動更新"
            :listen [:action auto-reload-action]))

(def url-bar
  (text :text (empty-or (setting :thread-url)
                        "URLを入力")
        :popup
        (fn [e]
          [(menu-item :text "コピー"
                      :listen [:action (fn [e] (seesaw.clipboard/contents! (text url-bar)))])
           (menu-item :text "貼り付け"
                      :listen [:action (fn [e] (text! url-bar (seesaw.clipboard/contents)))])
           (menu-item :text "貼り付けて読み込み"
                      :listen [:action (fn [e] (text! url-bar (seesaw.clipboard/contents))
                                         (reload e))])])))

(def waku-cbox 
  (checkbox :text "枠"
            :selected? (setting :waku?)
            :listen
            [:action
             (fn [e]
               (invoke-later
                (if (value waku-cbox)
                  (.setBorder (.getRootPane jimaku-window3)
                              (new javax.swing.border.LineBorder java.awt.Color/gray 1))
                  (.setBorder (.getRootPane jimaku-window3)
                              (new javax.swing.border.LineBorder java.awt.Color/gray 0)))
                (future
                  (Thread/sleep 300)
                  (repaint-jimaku-window3))))]))

(def size-cmbox 
  (combobox :model
            ["640x360" "640x480" "800x450" "800x600"
             "1024x576" "1024x768" "1280x720" "1280x960"]
            :listen 
            [:selection
             (fn [e]
               (let [[x y] (map read-string (clojure.string/split (value size-cmbox) #"x"))]
                  (config! jimaku-window3 :size [x :by y])
                  (future
                    (Thread/sleep 300)
                    (repaint-jimaku-window3))
                  ))]))


(def area
  (editor-pane
   :content-type "text/html"
   :text ""
   :listen [:hyperlink
            (fn [e]
              (if (= (.getEventType e)
                     javax.swing.event.HyperlinkEvent$EventType/ACTIVATED)
                (.browse (Desktop/getDesktop) (.toURI (.getURL e)))))]
   ;; :multi-line? true
   ;; :wrap-lines? true
   :background "BLACK"
   :editable? false
   ;; :foreground "GREEN"
   ;; :font (seesaw.font/font :name "ＭＳ Ｐゴシック" :size 12)
   ))

(def f
  (let [[x y] (setting :main-window-location)
        [width height]
        (map read-string
             (clojure.string/split (setting :main-window-size) #"x"))]
    (doto (frame :title software-name :on-close (if debug :hide :exit)
                 :icon (clojure.java.io/resource "icon2.png")
                 :content
                 (border-panel
                  :north
                  (horizontal-panel
                   :items (list tune-button size-cmbox waku-cbox auto-reload-cbox
                                reload-button url-bar))
                  :center
                  (scrollable area)
                  :vgap 2 :hgap 2 :border 2))
      (.setLocation (java.awt.Point. x y))
      (.setPreferredSize (java.awt.Dimension. width height)))))

(def jimaku-window3
  (let [[x y] (setting :jimaku-window-location)
        [width height]
        (map read-string
             (clojure.string/split (setting :jimaku-window-size) #"x"))]
    (doto (frame :title "字幕ウィンドウ"
                 :icon (clojure.java.io/resource "icon2.png")
                 :content
                 (border-panel
                  :north  (horizontal-panel :items (list drag-text)
                                            :background (seesaw.color/color 0 0 0 0))
                  :center jimaku-canvas
                  :vgap 0 :hgap 0 :border 0
                  :background (seesaw.color/color 0 0 0 0)))
      (.setUndecorated true)
      (.setAlwaysOnTop true)
      (.setBackground (new java.awt.Color 0 0 0 0))
      (.setLocation (java.awt.Point. x y))
      (.setPreferredSize (java.awt.Dimension. width height)))))

(.setBorder (.getRootPane jimaku-window3)
            (javax.swing.border.LineBorder. java.awt.Color/gray 1))


