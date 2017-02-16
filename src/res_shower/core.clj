(ns res-shower.core
  (:gen-class)
  (:use seesaw.core)
  (:require
   seesaw.clipboard
   seesaw.behave
   seesaw.bind
   seesaw.icon
   seesaw.color
   clojure.pprint
   [clojure.java.io :as jio]))

;; (use 'seesaw.core)
;; (require 'seesaw.behave)
;; (require 'seesaw.bind)
;; (require 'seesaw.color)
;; (require 'clojure.java.io :as jio
(native!)

(import 'java.awt.event.InputEvent)
(import 'java.awt.event.MouseEvent)
(import 'java.awt.Toolkit)
(import 'java.awt.MouseInfo)
(import 'java.awt.Color)
(import 'java.awt.AlphaComposite)
(import 'java.awt.Robot)
(import 'javax.swing.ImageIcon)
(import 'javax.sound.sampled.AudioInputStream)
(import 'javax.sound.sampled.AudioSystem)
(import 'javax.sound.sampled.DataLine)
(import 'javax.sound.sampled.DataLine$Info)
(import 'javax.sound.sampled.SourceDataLine)

(def software-name "res-shower")
(def linux? (<= 0 (.indexOf (System/getProperty "os.name") "Linux")))

(defn play [filepath]
  (let [file (java.io.File. filepath)
        in-stream (AudioSystem/getAudioInputStream file)
        format (.getFormat in-stream)
        info (DataLine$Info. SourceDataLine format)
        #^SourceDataLine line (AudioSystem/getLine info)]
    (doto line (.open format) (.start))
    (let [buf (byte-array 1024)]
      (loop []
        (let [nb (.read in-stream buf 0, (alength buf))]
          (when (>= nb 0) (.write line buf 0 (alength buf)))
          (when (not= nb -1) (recur))))
    (doto line (.drain) (.close)))))

(defn async-play [filepath]
  (future (play filepath)))

;; (async-play "resources/new_res.wav")
;; (async-play "resources/new_res.wav")
;; (take 10 (repeatedly #(async-play "resources/new_res.wav")))
;; うけるｗ

(defn tree-find-if [f tree]
  (letfn [(rec [f tree]
            (cond (f tree) tree
                  (or (not (coll? tree)) (empty? tree)) nil
                  :else (or (rec f (first tree)) (rec2 f (rest tree)))))
          (rec2 [f tree]
            (if (empty? tree) nil
                (or (rec f (first tree)) (rec2 f (rest tree)))))]
    (rec f tree)))

(defn tree-replace-if [f x tree]
  (letfn [(rec [f tree]
            (cond (f tree) x
                  (or (not (coll? tree)) (empty? tree)) tree
                  :else (cons (rec f (first tree)) (rec2 f (rest tree)))))
          (rec2 [f tree]
            (if (empty? tree) tree
                (cons (rec f (first tree)) (rec2 f (rest tree)))))]
    (rec f tree)))

(defmacro if-let-it [condition then-clause else-clause]
  (let [it-exp (tree-find-if (every-pred coll? (comp #(= 'it-is %) first)) condition)
        cond (tree-replace-if #(= it-exp %) 'it condition)]
    `(let [~'it ~(second it-exp)]
       (if ~cond
         ~then-clause
         ~else-clause))))

(defn count= [n seq] (= n (count seq)))

(def f (frame :title software-name :on-close :exit
              :icon (clojure.java.io/resource "icon.png")))

(defn display [content]
  (config! f :content content)
  content)

;; str -> str
;; (res-to-show (ress 1))
;; "891 名無しさん sage 2016/02/27(土) 20:07:29\n「&#65374;」がちゃんと表示されるようにしてください\n\n"
(defn format-res [res]
  (let [v (clojure.string/split res #"<>" Integer/MAX_VALUE) ; 省略させない
        info (drop-last 3 v)
        body (v 4)
        title (v 5)]
      (str (if (not= "" title)
             (do (config! f :title (str software-name " " title)) (str title "\n"))
             "\n") "\n"
           (apply str (interpose " " info)) "\n"
           (if (re-find #"包み紙" body)
             (str "包み紙は綺麗に重ねて直しなさい\n枚数チェックもするわよ")
             (-> body
                 (clojure.string/replace #"<br>" "\n")
                 (clojure.string/replace #"<a.*?>(.*?)</a>" "$1")
                 (clojure.string/replace #"&gt;" ">")
                 (clojure.string/replace #"&lt;" "<")
                 (clojure.string/replace #"&#65374;" "～"))))))

(defn format-res-for-jimaku [res]
  (let [v (clojure.string/split res #"<>" Integer/MAX_VALUE) ; 省略させない
        body (v 4)]
    (if (re-find #"包み紙" body)
      (str "包み紙は綺麗に重ねて直しなさい\n枚数チェックもするわよ")
      (-> body
          (clojure.string/replace #"<br>" "\n")
          (clojure.string/replace #"<a.*?>(.*?)</a>" "$1")
          (clojure.string/replace #"&gt;" ">")
          (clojure.string/replace #"&lt;" "<")
          (clojure.string/replace #"&#65374;" "～")))))

;; str -> str
(defn convert-url-to-dat-url [url]
  (let [[_ a]
        (re-find #"http://jbbs.shitaraba.net/bbs/read.cgi/(.*)$" url)]
    (str "http://jbbs.shitaraba.net/bbs/rawmode.cgi/" a)))

;; url -> [res] or nil
(defn read-thread [url]
  (-> url
       convert-url-to-dat-url
       (slurp :encoding "EUC-JP")
       (#(if (empty? %)
           nil
           (clojure.string/split-lines %)))))

;; (shitaraba-normalize "http://jbbs.shitaraba.net/bbs/read.cgi/internet/17144/1448539337/900-")
;; "http://jbbs.shitaraba.net/bbs/read.cgi/internet/17144/1448539337/"
(defn shitaraba-normalize [url]
  (let [[_ opt] (re-find #"http://jbbs.shitaraba.net/bbs/read.cgi/(.*/)" url)]
     (str "http://jbbs.shitaraba.net/bbs/read.cgi/" opt)))

(def dat (atom []))
(def url (atom ""))
(def new-res-list (atom []))
(def count-down (atom 10))
(def jimaku-window3 (frame :title "字幕ウィンドウ"
                           :icon (clojure.java.io/resource "icon.png")))
(def default-jimaku-text (atom ""))
(def jimaku-text (atom ""))

(defn repaint-jimaku-window3 []
  (if linux?
    (do
      (let [bs (.getBufferStrategy jimaku-window3)
            g (.getDrawGraphics bs)]
        (.update jimaku-window3 g)
        (.dispose g)
        (.show bs)))
    (.repaint jimaku-window3)))

(defn set-default-jimaku-text [s]
  (reset! default-jimaku-text s)
  (reset! jimaku-text @default-jimaku-text)
  (repaint-jimaku-window3)
  )

(def tune-window
  (frame :title "常に表示する文章"
         :icon (clojure.java.io/resource "icon.png")))
(def tune-text
 (text :text "" :multi-line? true :font (seesaw.font/font :name "ＭＳ Ｐゴシック" :size 34)))
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
  (button :text "常" :listen [:action (fn [e] (text! tune-text @default-jimaku-text)
                                        (-> tune-window pack! show!))]))
(def reload-button (button :text "更新"))
(def auto-reload-cbox (checkbox :text "自動更新"))
(def url-bar (text :text "URLを入力"))
(def waku-cbox 
  (checkbox :text "枠" :selected? true
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
(def area (text
           :text ""
           :multi-line? true
           :background "BLACK"
           :foreground "#0F0"
           :font (seesaw.font/font :name "ＭＳ Ｐゴシック" :size 12)))
(.setLineWrap area true)

;; スレをリロードする新着があったら字幕タイマー開始
(def jimaku-timer (atom nil))
(defn reload [e]
   ;; URLに変更があったかどうか
   (if-let-it (not= @url (it-is (shitaraba-normalize (value url-bar))))
     (do (reset! dat (read-thread it))
         (reset! new-res-list nil)
         (reset! url it)
         (invoke-later
          (text! area (apply str (map format-res @dat)))
          (scroll! area :to :bottom)))
     (when-let [news (read-thread (str it (inc (count @dat)) "-"))]
       (reset! dat (concat @dat news))
       (reset! new-res-list (concat @new-res-list news))
       (.setDelay @jimaku-timer
                  (if (<= 3 (count @new-res-list))
                    (max (int (/ 10000 (count @new-res-list))) 1000)
                    5000))
       (when-not (.isRunning @jimaku-timer)
         (.start @jimaku-timer))
       (invoke-later
        (text! area (apply str (map format-res @dat)))
        (scroll! area :to :bottom)))))

(def auto-reload-timer
  (timer (fn [e]
           (invoke-later
            (swap! count-down #(- % 1))
            (config! reload-button :text (str @count-down "/10")
                     :enabled? false)
            ;; <=だとサーバが重い時に連続リロードかかる可能性がある
            (when (= @count-down 0) 
              (future
                (reload e)
                (reset! count-down 10)))))
         :initial-delay 1000
         :delay         1000
         :start? nil))

(defn auto-reload-action [e]
  (if (value auto-reload-cbox)
    (do (reset! count-down 10)
        (.start auto-reload-timer))
    (do (config! reload-button :text "更新" :enabled? true)
        (.stop auto-reload-timer))))

(config! url-bar :popup
         (fn [e]
           [(menu-item :text "コピー"
                       :listen [:action (fn [e] (seesaw.clipboard/contents! (text url-bar)))])
            (menu-item :text "貼り付け"
                       :listen [:action (fn [e] (text! url-bar (seesaw.clipboard/contents)))])
            (menu-item :text "貼り付けて読み込み"
                       :listen [:action (fn [e] (text! url-bar (seesaw.clipboard/contents))
                                          (reload e))])]))

;; (.getActionListeners waku-cbox)
;; (remove-action-listner waku-cbox)
;; (defn remove-action-listner [obj]
;;   (doseq [l (.getActionListeners obj)]
;;     (.removeActionListener obj l)))

;; (def res-text (text :text ""
;;                     :multi-line? true
;;                     :font "MONOSPACED-PLAIN-30"
;;                     :foreground (seesaw.color/color 255 0 0)
;;                     :background (seesaw.color/color 0 0 0 0)))
;; (.setLineWrap res-text true)
(def drag-text
  (label :text software-name :background (seesaw.color/color 200 200 200 255)))

(defn draw-string* [c g]
  (let [lines (clojure.string/split-lines @jimaku-text)
        lines-with-i (map-indexed #(list (inc %) %2) lines)
        x 0 y 0
        longest-line (apply max-key
                            #(.stringWidth (.getFontMetrics g) %)
                            lines)
        font-size (loop [size 34]
                    (.setFont g (seesaw.font/font :name "ＭＳ Ｐゴシック" :size size))
                    (if (and (< (.width (config jimaku-window3 :size))
                                (.stringWidth (.getFontMetrics g) longest-line))
                             (< 1 size))
                      (recur (dec size))
                      size))
        font-height (int (* 1.1 (.. g getFontMetrics getHeight)))
        ]
    ;; (.setComposite g AlphaComposite/Clear)
    (.setBackground g (new java.awt.Color 255 255 255 0))
    (.clearRect g 0 0 800 600)

    ;; (.setComposite g AlphaComposite/SrcOver)
    (.setColor g java.awt.Color/white)
    (doseq [dx [-2 2 0] dy [-2 2 0]]
      (if (count= 1 lines)
        (.drawString g @jimaku-text
                     (int (+ (/ (- (.getWidth c) (.stringWidth (.getFontMetrics g) @jimaku-text))
                                2) dx))
                     (+ y dy font-height))
        (doseq [[i line] lines-with-i]
          (.drawString g line (+ x dx) (+ y dy (* i font-height)))
          )))

    (.setColor g java.awt.Color/blue)
    (if (count= 1 lines)
      (.drawString g @jimaku-text
                   (int (/ (- (.getWidth c) (.stringWidth (.getFontMetrics g) @jimaku-text)) 2))
                   (+ y font-height))
      (doseq [[i line] lines-with-i]
        (.drawString g line x (+ y (* i font-height)))))))


;; ;; (.repaint jimaku-window3)
(def jimaku-canvas
  (canvas
   :background (seesaw.color/color 0 0 0 0)
   :font (seesaw.font/font :name "ＭＳ Ｐゴシック" :size 34)
   :paint draw-string*;; draw-string*
   ))

(if linux?
  (listen jimaku-canvas
          :mouse-clicked
          (fn [e]
            (let [p (.getPoint e)
                  robot (new java.awt.Robot)
                  original-size (config jimaku-window3 :size)]
              (invoke-later 
               (config! jimaku-window3 :size [10 :by 10])
               (Thread/sleep 100)
               (.mousePress robot java.awt.event.InputEvent/BUTTON1_MASK)
               (if (not= java.awt.event.MouseEvent/BUTTON3 (.getButton e))
                 (.mouseRelease robot java.awt.event.InputEvent/BUTTON1_MASK))
               (Thread/sleep 100)
               (config! jimaku-window3 :size original-size)
               (.createBufferStrategy jimaku-window3 2)
               (future 
                 (Thread/sleep 300)
                 (repaint-jimaku-window3))
               )))))
;; (do
;;   (def bpanel
;;   (border-panel
;;    :north  (horizontal-panel :items (list drag-text)
;;                              :background (seesaw.color/color 0 0 0 0))
;;    :center jimaku-canvas
;;    :vgap 0 :hgap 0 :border 0
;;    :background (seesaw.color/color 0 0 0 0)))
;;   (config! jimaku-window3 :content bpanel))

;; (listen jimaku-window3 :action (fn [e] (println "CLICKED")))
(def initial-click-x (atom 0))
(def initial-click-y (atom 0))


(seesaw.behave/when-mouse-dragged
 drag-text
 :start (fn [e]
          (reset! initial-click-x (.getX e))
          (reset! initial-click-y (.getY e)))
 :drag  (fn [e _]
          (let [p (-> (MouseInfo/getPointerInfo) .getLocation)]
            (.setLocation jimaku-window3
                          (new java.awt.Point
                               (- (.x p) @initial-click-x)
                               (- (.y p) @initial-click-y))))))

(defn canvas-gen [s]
  (canvas
   :background (seesaw.color/color 0 0 0 0)
   :font (seesaw.font/font :size 34)
   :paint (fn [c g]
            (let [lines (map-indexed #(list (inc %) %2)
                                     (clojure.string/split-lines s))
                  x 0 y 0
                  metrics (. g getFontMetrics)
                  font-height (. metrics getHeight)]
              (.setColor g java.awt.Color/white)
              (try (doseq [dx [-2 2 0] dy [-2 2 0]]
                     (if (count= 1 lines)
                       (.drawString g s
                                    (int (+ (/ (- (.getWidth c) (.stringWidth metrics s))
                                               2) dx))
                                    (+ y dy font-height))
                       (doseq [[i line] lines]
                         (.drawString g line (+ x dx) (+ y dy (* i font-height)))
                         )))
                   (catch Exception e
                     (prn e)))
              (.setColor g java.awt.Color/blue)
              (if (count= 1 lines)
                (.drawString g s
                             (int (/ (- (.getWidth c) (.stringWidth metrics s)) 2))
                             (+ y font-height))
                (doseq [[i line] lines]
                  (.drawString g line x (+ y (* i font-height)))))))))

(reset!
 jimaku-timer
 (timer (fn [e]
          (invoke-later
           (if (empty? @new-res-list)
             (do (reset! jimaku-text @default-jimaku-text)
                 (repaint-jimaku-window3)
                 (.stop @jimaku-timer))
             (do (async-play "new_res.wav")
                 (reset! jimaku-text (format-res-for-jimaku (first @new-res-list)))
                 (repaint-jimaku-window3)
                 (swap! new-res-list rest)))))
        :delay 5000 
        :start? nil))


;; (reset! jimaku-window2 (frame :title "字幕ウィンドウ2"))
;; (doto @jimaku-window2
;;   (.setUndecorated true)
;;   (.setAlwaysOnTop true)
;;   (.setPreferredSize (new java.awt.Dimension 800 100))
;;   (.setLocation (let [p (.getLocation jimaku-window3)]
;;                   (new java.awt.Point (.x p) (+ (.y p) 10))))
;;   (.setBackground (new java.awt.Color 0 0 0 0))
;;   (config! :content (canvas-gen "透過ウィンドウの再描画方法が\nわかったので実装します"))
;;   (-> pack! show!))

(listen reload-button    :action reload)
(listen auto-reload-cbox :action auto-reload-action)
(display (border-panel
          :north (horizontal-panel
                  :items (list tune-button size-cmbox waku-cbox auto-reload-cbox
                               reload-button url-bar))
          :center (scrollable area)
          :vgap 2 :hgap 2 :border 2))

(def bpanel
  (border-panel
   :north  (horizontal-panel :items (list drag-text)
                             :background (seesaw.color/color 0 0 0 0))
   :center jimaku-canvas
   :vgap 0 :hgap 0 :border 0
   :background (seesaw.color/color 0 0 0 0)))

(config! jimaku-window3 :content bpanel)

(.setBorder (.getRootPane jimaku-window3)
            (new javax.swing.border.LineBorder java.awt.Color/gray 1))
(doto jimaku-window3
  (.setUndecorated true)
  (.setAlwaysOnTop true)
  (.setBackground (new java.awt.Color 0 0 0 0))
  (.setPreferredSize (new java.awt.Dimension 800 600)))

(def save-setting-timer
  (timer (fn [e]
           (spit
            "setting.clj"
            (clojure.pprint/write
             {:jimaku-window-size (value size-cmbox)
              :jimaku-window-location (let [p (.getLocation jimaku-window3)]
                                        (list (.x p) (.y p)))
              :main-window-size (let [p (config f :size)]
                                  (str (.width p) "x" (.height p)))
              :main-window-location (let [p (.getLocation f)]
                                      (list (.x p) (.y p)))
              :thread-url (value url-bar)
              :waku?      (value waku-cbox)}
             :stream nil)))
       :delay 30000
       :start? nil))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [setting
        (try (read-string (slurp "setting.clj"))
             (catch Exception e
               {:jimaku-window-size "800x600"
                :jimaku-window-location '(200 200)
                :main-window-size "400x300"
                :main-window-location '(200 200)
                :thread-url ""
                :waku? true}))]
    (let [[x y] (map read-string
                     (clojure.string/split (setting :jimaku-window-size) #"x"))]
      (doto jimaku-window3
        (.setPreferredSize (new java.awt.Dimension x y))
        (.setLocation (apply #(java.awt.Point. %1 %2)
                             (setting :jimaku-window-location)))))
    (let [[x y] (map read-string
                     (clojure.string/split (setting :main-window-size) #"x"))]
      (doto f
        (.setPreferredSize (new java.awt.Dimension x y))
        (.setLocation (apply #(java.awt.Point. %1 %2)
                             (setting :main-window-location)))))
    (text! url-bar (setting :thread-url))
    (selection! waku-cbox (setting :waku?))
    (selection! size-cmbox (setting :jimaku-window-size)))
  (-> jimaku-window3 pack! show!)
  (.createBufferStrategy jimaku-window3 2)
  (-> f pack! show!)
  (.start save-setting-timer)
  )




