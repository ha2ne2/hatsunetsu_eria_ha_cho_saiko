(ns res-shower.core
  (:gen-class)
  (:import java.awt.event.InputEvent
           java.awt.event.MouseEvent
           java.awt.Toolkit
           java.awt.MouseInfo
           java.awt.Color
           java.awt.AlphaComposite
           java.awt.Robot
           javax.swing.ImageIcon
           javax.sound.sampled.AudioInputStream
           javax.sound.sampled.AudioSystem
           javax.sound.sampled.DataLine
           javax.sound.sampled.DataLine$Info
           javax.sound.sampled.SourceDataLine)
  (:use seesaw.core)
  (:require
   seesaw.clipboard
   seesaw.behave
   seesaw.bind
   seesaw.icon
   seesaw.color
   clojure.pprint
   [clojure.java.io :as jio]))

(native!)

(def debug true)

(load "core_util")

(declare 
 jimaku-canvas drag-text tune-window tune-text tune-panel
 tune-button reload-button auto-reload-cbox url-bar waku-cbox
 size-cmbox area f jimaku-window3)

(def software-name "ハツネツエリアは超サイコー")
(def linux? (<= 0 (.indexOf (System/getProperty "os.name") "Linux")))
(def dat (atom []))
(def url (atom ""))
(def new-res-list (atom []))
(def count-down (atom 10))
(def default-jimaku-text (atom ""))
(def jimaku-text (atom ""))
(def jimaku-timer (atom nil))

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
                 (clojure.string/replace #"&quot;" "\"")
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
          (clojure.string/replace #"&quot;" "\"")
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
  (repaint-jimaku-window3))

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
            (when (or (= @count-down 0)  (= (mod @count-down 10) 0))
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
          (.drawString g line (+ x dx) (+ y dy (* i font-height))))))

    (.setColor g java.awt.Color/blue)
    (if (count= 1 lines)
      (.drawString g @jimaku-text
                   (int (/ (- (.getWidth c) (.stringWidth (.getFontMetrics g) @jimaku-text)) 2))
                   (+ y font-height))
      (doseq [[i line] lines-with-i]
        (.drawString g line x (+ y (* i font-height)))))))

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
                         (.drawString g line (+ x dx) (+ y dy (* i font-height))))))
                   (catch Exception e (prn e)))
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

(def setting
  (try (read-string (slurp "setting.clj"))
       (catch Exception e
         {:jimaku-window-size "800x600"
          :jimaku-window-location '(200 200)
          :main-window-size "400x300"
          :main-window-location '(200 200)
          :thread-url ""
          :waku? true})))
  
(load "core_component")

(defn -main
  [& args]
  (selection! waku-cbox (setting :waku?))
  (selection! size-cmbox (setting :jimaku-window-size))
  (-> jimaku-window3 pack! show!)
  (.createBufferStrategy jimaku-window3 2)
  (-> f pack! show!)
  (.start save-setting-timer))


(-main)


