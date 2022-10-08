(ns babashka-tasks.core
  (:require ["util" :as util]
            ["vscode" :as vscode]
            [applied-science.js-interop :as j]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [promesa.core :as p]))

(def task-type "babashka")

(defn ^:export reload []
  (j/call js/console :log "Reloading...")
  (js-delete js/require.cache (js/require.resolve "./babashka-tasks")))

(defn- log-error [e]
  (j/call js/console :error "babashka-tasks: An error occurred" e))

(defn- decode-text [x]
  (j/call (util/TextDecoder.) :decode x))

(def ^:dynamic *ctx* nil)

(defn- manage! [disposable]
  (j/push! (j/get *ctx* :subscriptions) disposable))

(defn- make-execution [file task-name]
  (-> ["bb"]
      (cond-> file (conj "--config" file))
      (conj "run" task-name)
      (->> (str/join " "))
      (vscode/ShellExecution.)))

(defn- make-task [folder uri task-name detail]
  (let [file (j/get uri :fsPath)
        task (vscode/Task. #js {:type task-type
                                :task task-name
                                :file file}
                           folder
                           task-name
                           task-type
                           (make-execution file task-name)
                           #js [])]
    (when detail
      (j/assoc! task :detail detail))
    task))

(defn- detail [task]
  (when (map? task)
    (:doc task)))

(defn- bb-edn-tasks [folder uri config]
  (for [[sym task] (:tasks config)
        :when (symbol? sym)
        :let  [task-name (name sym)]
        :when (not (str/starts-with? task-name "-"))]
    (make-task folder uri task-name (detail task))))

(defn- read-file [folder uri]
  (-> (j/call-in vscode/workspace [:fs :readFile] uri)
      (p/then #(->> % decode-text edn/read-string (bb-edn-tasks folder uri)))
      (p/catch log-error)))

(defn- pmapcat [f collp]
  (-> collp
      (p/then #(p/all (map f %)))
      (p/then #(into-array (eduction cat %)))))

(defn- find-workspace-folder-tasks [folder]
  (->> (vscode/RelativePattern. folder "**/bb.edn")
       (j/call vscode/workspace :findFiles)
       (pmapcat (partial read-file folder))))

(defn- find-all-tasks []
  (->> (j/get vscode/workspace :workspaceFolders)
       (pmapcat find-workspace-folder-tasks)))

(def tasks (atom nil))

(defn- clear-tasks! [_uri]
  (reset! tasks nil))

;; TODO: Smarter updates?
(defn- watch-babashka-files! []
  (doto (j/call vscode/workspace :createFileSystemWatcher "**/bb.edn")
    (j/call :onDidChange clear-tasks!)
    (j/call :onDidCreate clear-tasks!)
    (j/call :onDidDelete clear-tasks!)
    (manage!)))

(defn- provide-tasks [_token]
  (swap! tasks #(or % (find-all-tasks))))

(defn- resolve-task [task _token]
  (let [definition (j/get task :definition)]
    (when-let [task-name (j/get definition :task)]
      (vscode/Task. definition
                    (or (j/get task :scope) (j/get vscode/TaskScope :Workspace))
                    task-name
                    task-type
                    (make-execution (j/get definition :file) task-name)
                    #js []))))

(def task-provider #js {:provideTasks provide-tasks
                        :resolveTask  resolve-task})

(defn activate [^js ctx]
  (set! *ctx* ctx)
  (watch-babashka-files!)
  (manage! (j/call vscode/tasks :registerTaskProvider task-type task-provider)))

(defn deactivate [])

(def ^:export exports #js {:activate activate
                           :deactivate deactivate})
