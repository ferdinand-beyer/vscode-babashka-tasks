(ns tasks.version
  (:require [clojure.string :as str]
            [cheshire.core :as json]
            [tasks.info :as info]
            [tasks.tools :refer [git]])
  (:import [java.time LocalDateTime]
           [java.time.format DateTimeFormatter]))

(defn- current-version []
  (-> (slurp "package.json")
      (json/parse-string true)
      :version))

(defn- today []
  (.format (DateTimeFormatter/ofPattern "yyyy-MM-dd")
           (LocalDateTime/now)))

(defn- update-changelog [content current-version next-version date]
  (-> content
      (str/replace "## [Unreleased]\n"
                   (str "## [Unreleased]\n\n"
                        "## [" next-version "] (" date ")\n"))
      (str/replace (str "v" current-version "...HEAD\n")
                   (str "v" next-version "...HEAD\n"
                        "[" next-version "]: " info/github-url "/compare/v"
                        current-version "...v" next-version "\n"))))

(defn- changelog [current-version next-version]
  (let [content (slurp "CHANGELOG.md")]
    (when-not (str/includes? content (str "[" next-version "]"))
      {"CHANGELOG.md" (update-changelog content current-version next-version (today))})))

(def files
  ["README.md"
   "package.json"])

(defn- version-updates [next-version]
  (let [current-version (current-version)]
    (merge
     (changelog current-version next-version)
     (zipmap
      files
      (for [file files]
        (str/replace (slurp file) current-version next-version))))))

(defn- set-version [version]
  (doseq [[file-name content] (version-updates version)]
    (spit file-name content)))

(defn tag []
  (set-version info/version)
  (git :add "-u")
  (git :commit "-m" (str "Release " info/tag))
  (git :tag info/tag))

(defn -main [version]
  (set-version version))
