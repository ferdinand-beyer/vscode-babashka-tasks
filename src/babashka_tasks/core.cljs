(ns babashka-tasks.core
  (:require ["util" :as util]
            ["vscode" :as vscode]
            [cljs-bean.core :refer [bean]]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [promesa.core :as p]))

(def task-type "babashka")

(defn reload
  []
  (.log js/console "Reloading...")
  (js-delete js/require.cache (js/require.resolve "./vs-code")))

(defn- log-error [e]
  (.error js/console "babashka-tasks: An error occurred" e))

(defn- decode-text [x]
  (.decode (util/TextDecoder.) x))

(def ^:dynamic *ctx* nil)

(defn- subscribe! [disposable]
  (.push (:subscriptions *ctx*) disposable))

(defn- make-execution [_uri name]
  (vscode/ShellExecution. (str "bb run " name)))

(defn- make-task [folder uri name detail]
  (let [task (vscode/Task. #js {:type task-type
                                :file uri}
                           folder
                           name
                           task-type
                           (make-execution uri name)
                           #js [])]
    (when detail
      (set! (.-detail task) detail))
    task))

(defn- detail [task]
  (when (map? task)
    (:doc task)))

(defn- load-bb-edn [folder uri data]
  (for [[sym task] (:tasks data)
        :when (symbol? sym)
        :let  [task-name (name sym)]
        :when (not (str/starts-with? task-name "-"))]
    (make-task folder uri task-name (detail task))))

(defn- read-file [folder uri]
  (-> (.. vscode/workspace -fs (readFile uri))
      (.then #(->> % decode-text edn/read-string (load-bb-edn folder uri)))
      (.catch log-error)))

(defn- pmapcat [f collp]
  (-> collp
      (p/then #(p/all (map f %)))
      (p/then #(into-array (eduction cat %)))))

(defn- find-workspace-folder-tasks [folder]
  (->> (vscode/RelativePattern. folder "**/bb.edn")
       (.findFiles vscode/workspace)
       (pmapcat (partial read-file folder))))

(defn- find-all-tasks []
  (->> (.-workspaceFolders vscode/workspace)
       (pmapcat find-workspace-folder-tasks)))

(def tasks (atom nil))

(defn- invalidate-tasks! [_uri]
  (reset! tasks nil))

;; TODO: Smarter updates?
(defn- watch-babashka-files! []
  (doto (.createFileSystemWatcher vscode/workspace "**/bb.edn")
    (.onDidChange invalidate-tasks!)
    (.onDidCreate invalidate-tasks!)
    (.onDidDelete invalidate-tasks!)
    (subscribe!)))

(defn- provide-tasks [_token]
  (swap! tasks #(or % (find-all-tasks))))

;; TODO: Create a task from a task definition
(defn- resolve-task [task _token]
  (.log js/console "Asked to resolve" task)
  js/undefined)

(defn- babashka-task-provider []
  #js {:provideTasks provide-tasks
       :resolveTask  resolve-task})

(defn activate [^js ctx]
  (set! *ctx* (bean ctx))
  (watch-babashka-files!)
  (subscribe! (.registerTaskProvider vscode/tasks task-type
                                     (babashka-task-provider))))

(defn deactivate [])

(def ^:export exports #js {:activate activate
                           :deactivate deactivate})
