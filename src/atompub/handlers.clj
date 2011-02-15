(ns atompub.handlers
  (:use [atompub.atom]
        [net.cgrand moustache]
        [clojure.contrib.prxml :only (prxml)]))

(defn feed-handler
  "Creates a ring handler for an Atom Syndication feed

   - `feed-props` is a map like `atompub.core/feed-properties` returns.
   - `entry-seq` is a sequence of `atompub.core/atom-entry` maps."
  [feed-props entry-seq]
  (fn [_] (make-response "application/atom+xml; charset=utf-8"
                        (atom-feed feed-props (map atom-entry entry-seq)))))

(defn collection-handler
  "Creates a ring handler for an Atom Publishing Protocol Collection"
  []
  )

(defn atom-collection
  "Create a ring-handler for an APP collection."
  [{:keys [to-atom from-atom items get-item save-item
                               delete-item categories for-url prefix
                               title scheme]
                        :or {:to-atom identity :from-atom identity}}]
  ;; maybe side-effecty register collection somewhere, for service doc purposes.
  (letfn [(get-entry-response [id]
           (make-response
            "application/atom+xml; charset=utf-8"
            (with-out-str
              (prxml [:decl!]
                     (atom-edit-entry (to-atom (get-item id))
                                      {:prefix prefix})))))]
   (app
    ;; Service document, containing a single collection
    [""] (fn [_] (make-response "application/atomsvs+xml; charset=utf-8"
                              (service-doc prefix title)))

    ;; Collection handlers
    ;; - get: an atom-feed of the items provided by :items
    ;; - post: create a new item
    ["entries" ""] {:get (fn [_]
                           (make-response
                            "application/atom+xml; charset=utf-8"
                            (atom-feed
                             {:url for-url :title for-url
                              :feed-url (str prefix "/entries/") :scheme scheme}
                             (map #(atom-edit-entry (to-atom %) {:prefix prefix})
                                  (items)))))
                    :post #(let [id (save-item nil (from-atom (parse-request %)))]
                             (if (map? id)
                               id
                               (-> (get-entry-response id)
                                   (assoc-in [:headers "Location"]
                                             (str prefix "/entries/" id))
                                   (assoc :status 201))))}

    ;; Single entry handlers
    ;; - get: return a single entry response
    ;; - put: save a new entry
    ;; - delete: delete the entry
    ["entries" id] {:get (fn [_]
                           (if-let [entry (get-item id)]
                             (get-entry-response id)
                             {:status 404 :body "Nonexistent ID"}))
                    :put #(save-item id (from-atom (parse-request %)))
                    :delete (fn [_] (if (delete-item id)
                                     {:status 200}
                                     {:status 404}))}

    ;; Categories document
    ["categories" ""] (fn [_] (make-response "application/atomcat+xml; charset=utf-8"
                                           (categories-doc scheme (categories)))))))
