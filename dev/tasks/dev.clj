(ns tasks.dev
  (:require [tasks.build :refer [install]]
            [tasks.tools :refer [*opts* clj]]))

(defn dev []
  (binding [*opts* {:inherit true}]
    (install)
    (clj "-M:dev:nrepl:cljs:shadow"
         "-m" "shadow.cljs.devtools.cli"
         :watch :extension)))

(defn -main [] (dev))
