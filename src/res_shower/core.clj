(ns res-shower.core
  (:gen-class)
  (:import (java.awt.event InputEvent MouseEvent)
           (java.awt Toolkit MouseInfo Desktop
                     Color AlphaComposite Robot)
           javax.swing.ImageIcon
           javax.swing.event.HyperlinkEvent
           java.net.URI
           (javax.sound.sampled AudioInputStream
                                AudioSystem DataLine
                                DataLine$Info SourceDataLine)
            (java.nio.charset Charset StandardCharsets))
  (:use seesaw.core)
  (:require seesaw.clipboard
            seesaw.behave
            seesaw.bind
            seesaw.icon
            seesaw.color
            clojure.pprint
            [clojure.java.io :as jio]
            [environ.core :refer [env]]
            [clj-http.client :as client]))

(native!)

(def debug (env :dev))

;; (when debug
;;   (when (resolve 'jimaku-timer) (eval '(.stop jimaku-timer)))
;;   (when (resolve 'save-setting-timer) (eval '(.stop save-setting-timer)))
;;   (when (resolve 'auto-reload-timer) (eval '(.stop auto-reload-timer))))

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

(defn display [content]
  (config! f :content content)
  content)

;; HTTPヘッダを使って、差分だけを取得する。
(def response-info (atom {:size 0 :last-modified nil}))
(defn send-http-request [url]
  (let [headers (if-let [last-modified (:last-modified @response-info)]
                  {"If-Modified-Since" last-modified}
                  {})
        range-header (if-let [size (:size @response-info)]
                       {"Range" (str "bytes=" size "-")}
                       {})
        response (client/get url {:headers (merge headers range-header)
                                 :decode-body-headers true :as :auto})]
    (let [content-length
          (try (Integer. (:content-length (:headers response))) (catch NumberFormatException _ nil))]
      (swap! response-info
             (fn [info]
               {:size (+ (:size info) (or content-length 0))
                :last-modified (or (:last-modified (:headers response)) (:last-modified info))}))
      (:body response))))


;; str -> str
;; (res-to-show (ress 1))
;; "891 名無しさん sage 2016/02/27(土) 20:07:29\n「&#65374;」がちゃんと表示されるようにしてください\n\n"
(defn format-res [res]
  (let [v (clojure.string/split res #"<>" Integer/MAX_VALUE) ; 省略させない
        info (drop-last 3 v)
        body (v 3)
        title (v 4)]
      (str (apply str (interpose " " info)) "<br>"
           (if (re-find #"包み紙" body)
             (str "包み紙は綺麗に重ねて直しなさい<br>枚数チェックもするわよ")
             (-> body
                 (clojure.string/replace #"(?<!=\")(https?://[-_.!~*'()a-zA-Z0-9;/?:@&=+$,%#]+)"
                                         "<a href=\"$1\">$1</a>")
                 (clojure.string/replace #"(?<!h)(ttps?://[-_.!~*'()a-zA-Z0-9;/?:@&=+$,%#]+)"
                                         "<a href=\"h$1\">$1</a>")
                 (clojure.string/replace #"&quot;" "\"")
                 (clojure.string/replace #"&gt;" ">")
                 (clojure.string/replace #"&lt;" "<")
                 (clojure.string/replace #"&#65374;" "～")))
           "<br><br>"
           (when (not= "" title)
             (config! f :title (str software-name " " title))
             (str  "--------------- " title " ---------------<br>")))))

(defn format-res-for-jimaku [res]
  (let [v (clojure.string/split res #"<>" Integer/MAX_VALUE) ; 省略させない
        body (v 4)]
    (if (re-find #"包み紙" body)
      (str "包み紙は綺麗に重ねて直しなさい\n枚数チェックもするわよ")
      (-> body
          (clojure.string/replace #"<br>" "\n")
          (clojure.string/replace #"&quot;" "\"")
          (clojure.string/replace #"&gt;" ">")
          (clojure.string/replace #"&lt;" "<")
          (clojure.string/replace #"<.*?>" "")
          (clojure.string/replace #"&#65374;" "～")))))

;; str -> str
;; (convert-url-to-dat-url "http://jbbs.shitaraba.net/bbs/read.cgi/internet/17144/1494773060/" )
;; "http://jbbs.shitaraba.net/bbs/rawmode.cgi/internet/17144/1494773060/"
(defn convert-url-to-dat-url [url]
  (if-let [[_ a]
           (re-find #"https://jbbs.shitaraba.net/bbs/read.cgi/(.*)$" url)]
    (str "https://jbbs.shitaraba.net/bbs/rawmode.cgi/" a)
    ;; bbs.jpnkn 対応
    (if-let [[_ name thread-id]
             (re-find #"https://bbs.jpnkn.com/test/read.cgi/(.*?)/(.*)/$" url)]
      (str "http://bbs.jpnkn.com/" name "/dat/" thread-id ".dat"))))

;; Url -> [res] or nil
(defn read-thread [url]
  (-> url
      convert-url-to-dat-url
      send-http-request
      (#(if (empty? %)
          nil
          (clojure.string/split-lines %)))))
  
;; (shitaraba-normalize "http://jbbs.shitaraba.net/bbs/read.cgi/internet/17144/1448539337/900-")
;; "http://jbbs.shitaraba.net/bbs/read.cgi/internet/17144/1448539337/"
(defn shitaraba-normalize [url]
  (if-let [[_ opt] (re-find #"https://jbbs.shitaraba.net/bbs/read.cgi/(.*/)" url)]
    (str "https://jbbs.shitaraba.net/bbs/read.cgi/" opt)
    ;; bbs.jpnkn 対応
    url))

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

(def jimaku-timer
  (timer (fn [e]
           (invoke-later
            (if (empty? @new-res-list)
              (do (reset! jimaku-text @default-jimaku-text)
                  (repaint-jimaku-window3)
                  (.stop jimaku-timer))
              (do (async-play "new_res.wav")
                  (reset! jimaku-text (format-res-for-jimaku (first @new-res-list)))
                  (repaint-jimaku-window3)
                  (swap! new-res-list rest)))))
         :delay 5000 
         :start? nil))

;; (timer (fn[e] (async-play "new_res.wav"))
;;        :delay 3000
;;        :start? true)



(def style
"<style>
  body {
    color: #00FF00;
    background: #000000;
    font-family: ＭＳ Ｐゴシック;
    font-size: 12
  }

  a {
    text-decoration: none;
    color: #40AAFF;
  }
</style>")


(defn reload [e]
   ;; URLに変更があったかどうか
  (if-let-it (not= @url (it-is (shitaraba-normalize (value url-bar))))
     (do (reset! dat (read-thread it))
         (reset! new-res-list nil)
         (reset! url it)
         (invoke-later
          (text! area
                 (reduce str (concat [(str style "<body>")]
                                     (reduce str (map format-res (reverse @dat)))
                                     ["</body>"])))
          (scroll! area :to :top)))
     (when-let [news (read-thread it)]
       (reset! dat (concat @dat news))
       (reset! new-res-list (concat @new-res-list news))
       (.setDelay jimaku-timer
                  (if (<= 3 (count @new-res-list))
                    (max (int (/ 10000 (count @new-res-list))) 1000)
                    5000))
       (when-not (.isRunning jimaku-timer) (.start jimaku-timer))
       (invoke-later
        (text! area (reduce str (concat [(str style "<body>")]
                                          (reduce str (map format-res (reverse @dat)))
                                          ["</body>"])))
        (scroll! area :to :top)))))

(def auto-reload-timer
  (timer (fn [e]
           (invoke-later
            (swap! count-down #(- % 1))
            (config! reload-button :text (str @count-down "/10"))
            ;; 通信失敗時に-10でもリロード
            (when  (= (mod @count-down 10) 0)
              (future
                (reload e)
                (reset! count-down 10)))))
         :initial-delay 1000
         :delay         1000
         :start? nil))

(defn auto-reload-cbox-toggled [e]
  (if (value auto-reload-cbox)
    (do (reset! count-down 10)
        (config! reload-button :text (str @count-down "/10") :enabled? false)
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


(when debug (-main))



