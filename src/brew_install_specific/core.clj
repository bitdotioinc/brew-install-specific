(ns brew-install-specific.core
  (:gen-class)
  (:require [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [clj-http.client :as http]
            [net.cgrand.enlive-html :as html]
            [clansi :refer [style]]))

(defonce commits-url*
  "https://github.com/Homebrew/homebrew-core/commits/master/Formula/%s.rb")

(defonce commit-url*
  "https://github.com/Homebrew/homebrew-core/tree/%s/Formula/%s.rb")

(defonce install-url*
  "https://raw.githubusercontent.com/Homebrew/homebrew-core/%s/Formula/%s.rb")

(defn fetch-versions
  [package version data]
  (let [i (atom 0)]
    (for [commit-title-element (html/select data [:p.commit-title :a])
          :let [{:keys [aria-label href]} (:attrs commit-title-element)
                commit-msg aria-label
                commit-sha (-> href
                               (str/replace-first "/Homebrew/homebrew-core/commit/" "")
                               (str/split #"#diff")
                               first)]
          :when (str/includes? commit-msg version)]
      {:id (swap! i inc)
       :msg commit-msg
       :sha commit-sha
       :url (format commit-url* commit-sha package)})))

(defn -main
  [& [package-at-version]]
  (Thread/setDefaultUncaughtExceptionHandler
   (reify Thread$UncaughtExceptionHandler
     (uncaughtException [_ _ ex]
       (println (style "Something went wrong." :red)))))
  (if-not (string? package-at-version)
    (println "Usage: brew-install-specific package@version")
    (let [[package version] (->> package-at-version
                                 (partition-by #(= \@ %))
                                 ((fn [[p _ v]]
                                    [p v]))
                                 (map str/join))]
      (if-not (and (seq package)
                   (seq version))
        (println "Usage: brew-install-specific package@version")
        (let [commits-uri (-> commits-url*
                              (format package)
                              java.net.URI.)
              data (html/html-resource commits-uri)
              versions (fetch-versions package version data)]
          (if (empty? versions)
            (println (style "No matching commits found." :red))
            (do
              (println (style "Matching versions:" :yellow))
              (doseq [{:keys [id msg url]} (sort-by :id versions)]
                (print (style (str id ". ") :yellow))
                (println (style msg :green))
                (print (-> id str count (+ 2) (repeat " ") str/join))
                (println (style url :yellow :underline)))

              (print (style "\nSelect index: " :yellow))
              (flush)
              (let [sel-text (read-line)
                    sel-id (Integer/valueOf sel-text)
                    sel-item (nth versions (dec sel-id))]
                (println (style "Run:\n  brew install" :yellow)
                         (style (format install-url* (:sha sel-item) package)
                                :green :underline))))))))))
