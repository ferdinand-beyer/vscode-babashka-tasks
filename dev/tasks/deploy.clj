(ns tasks.deploy
  (:require [tasks.build :refer [build]]
            [tasks.tools :refer [npx]]))

(defn- deploy []
  (build)
  (npx :vsce :publish))

(defn -main []
  (deploy))
