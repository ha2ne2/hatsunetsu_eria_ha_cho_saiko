(in-ns 'res-shower.core)


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

;; (empty-or "" "hoge")
;=> "hoge"
(defmacro empty-or [head & rest]
  (if (empty? rest)
    head
    `(let [x# ~head]
       (if (empty? x#)
         (empty-or ~@rest) x#))))


