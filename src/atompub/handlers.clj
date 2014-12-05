(ns atompub.handlers
  (:use [atompub.atom]
        [net.cgrand moustache]
        [clojure.xml :only (emit)]
        [clojure.stacktrace :only (print-cause-trace)])
  (:require [atompub.core :as a])
  (:import [atompub.core AtomEntry]))

(defn- error-middleware [app]
  (fn [req]
    (try
      (app req)
      (catch Exception e
        {:status 500
         :body (str "Server Error: " e
                    "\n\n"
                    (with-out-str (print-cause-trace e)))}))))

;; (defn feed-handler
;;   "Creates a ring handler for an Atom Syndication feed

;;    - `feed-props` is a map like `atompub.core/feed-properties` returns.
;;    - `entry-seq-fun` is a function which returns a sequence of
;;       `atompub.core/atom-entry` maps.
;;    - optionally takes a third argument, which is a function that is mapped
;;       across the items from `entry-seq-fun`, and returns an XML atom entry."
;;   ([feed-props entry-seq-fun]
;;      (feed-handler feed-props entry-seq-fun atom-entry))
;;   ([feed-props entry-seq-fun entry-mapper]
;;      (fn [_] (let [entry-seq (entry-seq-fun)
;;                   updated (:updated (first entry-seq))]
;;               (make-response "application/atom+xml; charset=utf-8"
;;                              (atom-feed (assoc feed-props :updated updated)
;;                                         (map entry-mapper entry-seq)))))))

(defn feed-handler
  ([feed]
   (feed-handler feed atom-entry))
  ([feed mapper]
    (fn [_] (make-response "application/atom+xml; charset=utf-8"
                          (atom-feed feed
                                     (map mapper (a/feed-entries feed)))))))

(defn- collection-response
  "Turn (nearly) any kind of value into an appropriate Ring response. *Very* heuristic."
  [val prefix]
  (cond
   (true? val)
     {:status 200}
   (:status val)
     val
   (or (number? val)
       (string? val))
     {:status 302
      :headers {"Location" (str prefix "/entries/" val)}}
   (map? val)
     (make-response "application/atom+xml; charset=utf-8"
                    (atom-edit-entry (a/map->AtomEntry val) prefix))
   :else
     {:status 500}))

(defn service-doc-handler [req feed]
  (make-response "application/atomsvc+xml; charset=utf-8"
                 (service-doc (a/feed-url feed) (a/feed-title feed))))

(defn save-entry-handler [req feed id-or-nil]
  (let [result (a/collection-save-entry feed id-or-nil (parse-request req))
        entry-response (if (:status result)
                         nil
                         (make-response atom-ctype
                          (xml-to-str (atom-edit-entry result (a/feed-url feed)))))]
    (cond
     ;; new post, new-id is not a response
     (and (nil? id-or-nil) (:id result))
     (-> (assoc entry-response :status 201)
         (assoc-in [:headers "Location"] (str (a/feed-url feed) "entries/"
                                              (or (:id result) result))))

     ;; new-id is a response
     (:status result)
       result

     :else
       entry-response
       )))

(defn get-entry-handler [req feed id]
  (let [entry (a/collection-get-entry feed id)]
    (make-response atom-ctype (xml-to-str
                               (atom-edit-entry entry (a/feed-url feed))))))

(defn delete-entry-handler [req feed id]
  (let [result (a/collection-delete-entry feed id)]
    (collection-response result nil)))

(defn collection-handler
  "Creates a ring handler for an Atom Publishing Protocol Collection"
  [feed]
  (app
   error-middleware
   [""] (delegate service-doc-handler feed)

   ["entries" ""] {:get  (feed-handler
                          #_(assoc feed
                              :url (str (:url feed-props) "entries/"))
                          ;(:get-entries method-map)
                          #(atom-edit-entry % (a/feed-url feed)))
                   :post (delegate save-entry-handler
                                   feed
                                   nil)}
   
   ["entries" id] {:get    (delegate get-entry-handler
                                     feed
                                     id)
                   :put    (delegate save-entry-handler
                                     feed
                                     id)
                   :delete (delegate delete-entry-handler
                                     feed
                                     id)}

   ["categories" ""] (fn [_] (make-response
                             atom-ctype
                             (categories-doc "" ((a/collection-category-scheme feed)))))
   ))
