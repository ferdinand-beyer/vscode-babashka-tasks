(ns babashka-tasks.core
  (:require ["util" :as util]
            ["vscode" :as vscode]
            [promesa.core :as p]
            [cljs-bean.core :refer [bean]]
            [clojure.edn :as edn]))

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

(def tasks (atom nil))

(defn- make-execution [_uri name]
  (vscode/ShellExecution. (str "bb run " name)))

(defn- make-task [folder uri name detail]
  (let [task (vscode/Task. #js {:type "babashka"
                                :file uri}
                           folder
                           name
                           "babashka"
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
        :when (symbol? sym)]
    (make-task folder uri (name sym) (detail task))))

(defn- read-file [folder uri]
  (-> (.. vscode/workspace -fs (readFile uri))
      (.then decode-text)
      (.then edn/read-string)
      (.then (partial load-bb-edn folder uri))
      (.catch log-error)))

(defn- pmapcat [f collp]
  (-> collp
      (p/then #(p/all (map f %)))
      (p/then #(into-array (eduction cat %)))))

(defn- find-workspace-folder-tasks [folder]
  (->> (.findFiles vscode/workspace (vscode/RelativePattern. folder "**/bb.edn"))
       (pmapcat (partial read-file folder))))

(defn- find-all-tasks []
  (->> (.-workspaceFolders vscode/workspace)
       (pmapcat find-workspace-folder-tasks)))

(defn- file-changed [uri] (.log js/console "FILE CHANGED" uri))
(defn- file-created [uri] (.log js/console "FILE CREATED" uri))
(defn- file-deleted [uri] (.log js/console "FILE DELETED" uri))

(defn- provide-tasks [_token]
  (swap! tasks #(or % (find-all-tasks))))

(defn- resolve-task [task _token]
  (.log js/console "Asked to resolve" task)
  js/undefined)

(defn- babashka-task-provider []
  #js {:provideTasks provide-tasks
       :resolveTask resolve-task})

(defn- watch-babashka-files! []
  (doto (.createFileSystemWatcher vscode/workspace "**/bb.edn")
    (.onDidChange file-changed)
    (.onDidCreate file-created)
    (.onDidDelete file-deleted)
    (subscribe!)))

(defn activate [^js ctx]
  (set! *ctx* (bean ctx))
  (watch-babashka-files!)
  (reset! tasks (find-all-tasks))
  (subscribe! (.registerTaskProvider vscode/tasks "babashka"
                                     (babashka-task-provider))))

(defn deactivate [])

(def exports #js {:activate activate
                  :deactivate deactivate})
