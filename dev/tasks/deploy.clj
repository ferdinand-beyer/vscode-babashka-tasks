(ns tasks.deploy
  (:require [tasks.tools :refer [npx]]))

(defn- deploy []
  (npx :vsce :publish))

(defn -main []
  (deploy))
