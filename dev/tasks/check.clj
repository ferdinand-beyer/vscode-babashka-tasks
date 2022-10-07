(ns tasks.check
  (:require [tasks.format :as format]
            [tasks.tools :refer [clj]]))

(defn clj-kondo []
  (clj "-M:clj-kondo" "--lint" "dev" "src"))

(defn check []
  (format/check)
  (clj-kondo))

(defn -main []
  (check))
