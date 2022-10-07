(ns tasks.info)

(def github-url "https://github.com/ferdinand-beyer/vscode-babashka-tasks")
(def version "0.1.0")

(def tag (str "v" version))

(defn github-actions []
  (println (str "::set-output name=version::" version))
  (println (str "::set-output name=tag::" tag)))
