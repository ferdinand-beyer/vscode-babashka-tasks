(ns tasks.build
  (:require [babashka.fs :as fs]
            [tasks.tools :refer [npm npx shadow]]))

(defn install []
  (when (seq
         (fs/modified-since
          "node_modules"
          ["package.json" "package-lock.json"]))
    (npm :ci)))

(defn build []
  (install)
  (shadow :release :extension)
  (npx :vsce :package))

(defn -main [] (build))
