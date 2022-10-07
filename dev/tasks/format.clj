(ns tasks.format
  (:require [tasks.tools :refer [clj]]))

(defn check []
  (clj "-M:cljfmt" :check "dev" "src"))

(defn fix []
  (clj "-M:cljfmt" :fix "dev" "src"))
