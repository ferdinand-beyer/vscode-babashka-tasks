(ns tasks.version
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [semver.core :as semver]
            [tasks.info :as info]
            [tasks.tools :refer [*cwd* git npm]])
  (:import [java.time LocalDateTime]
           [java.time.format DateTimeFormatter]))

(defn- current-version []
  (require :reload '[tasks.info :as info])
  @(resolve 'info/version))

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

(defmulti ^:private update-file
  (fn [filepath _current-version _next-version]
    (fs/file-name filepath)))

(defmethod update-file :default
  [filepath current-version next-version]
  (-> (slurp filepath)
      (str/replace current-version next-version)
      (->> (spit filepath))))

(defmethod update-file "CHANGELOG.md"
  [filepath current-version next-version]
  (let [content (slurp "CHANGELOG.md")]
    (when-not (str/includes? content (str "[" next-version "]"))
      (spit filepath (update-changelog content current-version next-version (today))))))

(defmethod update-file "package.json"
  [filepath _ next-version]
  (let [dir (some-> (fs/parent filepath) str)]
    (binding [*cwd* (when (seq dir) dir)]
      (npm :version next-version "--git-tag-version" "false"))))

(def ^:private files
  ["dev/tasks/info.clj"
   "CHANGELOG.md"
   "README.md"
   "package.json"])

(defn- swap-version [current-version next-version]
  (run! #(update-file % current-version next-version) files))

(defn- set-version [next-version]
  (swap-version (current-version) next-version))

(defn- transform [f]
  (let [current (current-version)]
    (swap-version current (semver/transform f current))))

(defn major []
  (transform semver/increment-major))

(defn minor []
  (transform semver/increment-minor))

(defn patch []
  (transform semver/increment-patch))

(defn version [s]
  (case (name s)
    "major" (major)
    "minor" (minor)
    "patch" (patch)
    (set-version (str/replace s #"^v" ""))))

(defn tag []
  (let [tag (str "v" (current-version))
        msg (str "Release " tag)]
    (git :add "-u")
    (git :commit "-m" msg)
    (git :tag "-a" "-m" msg tag)))

(defn -main [& [s]]
  (if (some? s)
    (version s)
    (println (current-version))))
