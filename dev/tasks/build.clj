(ns tasks.build
  (:require [babashka.fs :as fs]
            [tasks.tools :refer [*cwd* npm npx shadow]]))

(defn install []
  (when (seq
         (fs/modified-since
          "node_modules"
          ["package.json" "package-lock.json"]))
    (npm :install)))

(defn build []
  (install)
  (shadow :release :extension)
  (npx :vsce :package))

(defn -main [] (build))
