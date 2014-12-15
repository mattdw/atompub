(ns atompub.handlers
  (:use [atompub.atom]
        [net.cgrand moustache]
        [clojure.stacktrace :only (print-cause-trace)])
  (:require [atompub.core :as a])
  (:import [atompub.core AtomEntry]))

;; `atompub.handlers` wraps up the Ring/HTTP portion of the necessary
;; communications. It mostly just marshalls in and out of XML/HTTP and
;; calls the methods provided by `IAtomCollection`.

(defn- error-middleware
  "A Ring middleware to catch all exceptions and send them back as 500
  errors."
  [app]
  (fn [req]
    (try
      (app req)
      (catch Exception e
        {:status 500
         :body (str "Server Error: " e
                    "\n\n"
                    (with-out-str (print-cause-trace e)))}))))

(defn feed-handler
  "Creates a Ring handler for an `IAtomFeed` feed. Appropriate for plain
  feeds (i.e. syndication) and also for editable collections."
  ([feed]
   (feed-handler feed atom-entry))
  ([feed mapper]
    (fn [_] (make-response "application/atom+xml; charset=utf-8"
                          (atom-feed feed
                                     (map mapper (a/feed-entries feed)))))))

(defn- collection-response
  "Turn (nearly) any kind of value into an appropriate Ring response. *Very*
  heuristic.

  * (arg → output)
  * `true` → 200 OK
  * Any map with a `:status` field → return the map
  * A number or string → redirect to the entry with the arg used as key.
  * Any map without a `:status` field → convert to `IAtomEntry`, return directly.
  * anything else → 500 Server Error"
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

(defn service-doc-handler
  "Ring handler for a 'service document', which lives at the root of an Atom
  Collection. Takes request and `IAtomFeed`."
  [req feed]
  (make-response "application/atomsvc+xml; charset=utf-8"
                 (service-doc (a/feed-url feed) (a/feed-title feed))))

(defn save-entry-handler
  "Handles POST/PUT of entries, wraps up the HTTP portion of
  `collection-save-entry`."
  [req feed id-or-nil]
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

(defn get-entry-handler
  "Wraps up the HTTP portion of `collection-get-entry`."
  [req feed id]
  (let [entry (a/collection-get-entry feed id)]
    (make-response atom-ctype (xml-to-str
                               (atom-edit-entry entry (a/feed-url feed))))))

(defn delete-entry-handler
  "Wraps up the HTTP portion of `collection-delete-entry`."
  [req feed id]
  (let [result (a/collection-delete-entry feed id)]
    (collection-response result nil)))

(defn collection-handler
  "Creates a ring handler for an Atom Publishing Protocol Collection.

  It's a bit ugly at the moment, making assumptions about URLs and doing a
  bit of string-bashing to piece them together. If anything needs to change
  in this library, it's this."
  [feed]
  (app

   ;; catch all exceptions and return them in a 500 body. Makes things a
   ;; lot easier to debug.
   error-middleware

   ;; the root of the collection gives us the service document
   [""] (delegate service-doc-handler feed)

   ;; GET of entries returns a feed of entries
   ;; POST to entries creates a new post
   ["entries" ""] {:get  (feed-handler feed
                           #(atom-edit-entry % (a/feed-url feed)))
                   :post (delegate save-entry-handler
                                   feed
                                   nil)}

   ;; GET to entries/id just gives us the entry
   ;; PUT saves/updates the entry
   ;; DELETE deletes the entry.
   ["entries" id] {:get    (delegate get-entry-handler
                                     feed
                                     id)
                   :put    (delegate save-entry-handler
                                     feed
                                     id)
                   :delete (delegate delete-entry-handler
                                     feed
                                     id)}

   ;; At [collection]/categories/ lives the categories document for the feed.
   ["categories" ""] (fn [_] (make-response
                             atom-ctype
                             (categories-doc "" (a/collection-category-scheme feed))))))
