(ns atompub.handlers
  (:use [atompub.atom]
        [net.cgrand moustache]
        [clojure.contrib.prxml :only (prxml)]))

(defn feed-handler
  "Creates a ring handler for an Atom Syndication feed

   - `feed-props` is a map like `atompub.core/feed-properties` returns.
   - `entry-seq-fun` is a function which returns a sequence of
      `atompub.core/atom-entry` maps.
   - optionally takes a third argument, which is a function that is mapped
      across the items from `entry-seq-fun`, and returns an XML atom entry."
  ([feed-props entry-seq-fun]
     (feed-handler feed-props entry-seq-fun atom-entry))
  ([feed-props entry-seq-fun entry-mapper]
     (fn [_] (let [entry-seq (entry-seq-fun)
                  updated (:updated (first entry-seq))]
              (make-response "application/atom+xml; charset=utf-8"
                             (atom-feed (assoc feed-props :updated updated)
                                        (map entry-mapper entry-seq)))))))

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
                    (atom-edit-entry val prefix))
   :else
     {:status 500}))

(defn service-doc-handler [req feed-props]
  (make-response "application/atomsvc+xml; charset=utf-8"
                 (service-doc (:url feed-props) (:title feed-props))))

(defn save-entry-handler [req feed-props save-func id-or-nil]
  (let [result (save-func id-or-nil (parse-request req))
        entry-response (if (:status result)
                         nil
                         (make-response atom-ctype
                          (xml-to-str (atom-edit-entry result (:url feed-props)))))]
    (cond
     ;; new post, new-id is not a response
     (and (nil? id-or-nil) (:id result))
     (-> (assoc entry-response :status 201)
         (assoc-in [:headers "Location"] (str (:url feed-props) "entries/"
                                              (or (:id result) result))))

     ;; new-id is a response
     (:status result)
       result

     :else
       entry-response
       )))

(defn get-entry-handler [req feed-props get-func id]
  (let [entry (get-func id)]
    (make-response atom-ctype (xml-to-str
                               (atom-edit-entry entry (:url feed-props))))))

(defn delete-entry-handler [req del-func id]
  (let [result (del-func id)]
    (collection-response result nil)))

(defn collection-handler
  "Creates a ring handler for an Atom Publishing Protocol Collection"
  [feed-props method-map]
  (app
   [""] (delegate service-doc-handler feed-props)

   ["entries" ""] {:get  (feed-handler
                          (assoc feed-props
                            :url (str (:url feed-props) "entries/"))
                          (:get-entries method-map)
                          #(atom-edit-entry % (:url feed-props)))
                   :post (delegate save-entry-handler
                                   feed-props
                                   (:save-entry method-map)
                                   nil)}
   
   ["entries" id] {:get    (delegate get-entry-handler
                                     feed-props
                                     (:get-entry method-map)
                                     id)
                   :put    (delegate save-entry-handler
                                     feed-props
                                     (:save-entry method-map)
                                     id)
                   :delete (delegate delete-entry-handler
                                     (:delete-entry method-map)
                                     id)}

   ["categories" ""] (fn [_] (make-response
                             atom-ctype
                             (categories-doc "" ((:get-categories method-map)))))
   ))
