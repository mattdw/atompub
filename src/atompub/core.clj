(ns atompub.core)

;; ## Feed and Entry prototypes

;; The following section contains a series of default-property maps
;; and helper functions to create (where possible) spec-conforming
;; atom feeds. `feed-properties`, `atom-entry` and `edit-entry` are
;; the suggested way to create appropriate structs; they'll catch
;; missing required fields.

;; Default value for required fields.

(def required-field (Exception. "Missing required field"))

;; ### Top-level feed properties

(def default-feed-properties
  {:title         required-field
   :url           required-field    ; url to this feed
   :home-url      nil               ; url to an appropriate homepage
                                    ; for this feed
   :author-name   required-field
   :author-email  nil
   :updated       required-field})

(defn feed-properties
  "Create a top-level feed-properties map."
  [props]
  (merge default-feed-properties props))

;; ### Per-entry properties (syndication)

(def default-entry-properties
  {:title         required-field
   :id            required-field
   :updated       required-field
   :published     nil
   :url           nil
   :content       nil
   :summary       nil})

(defn atom-entry
  "Create an atom-entry map."
  [entry-map]
  (merge default-entry-properties entry-map))

;; ### Extra per-entry properties for APP protocol.
;; 
;; Extends default-entry-properties.

(def default-edit-entry-properties
  {:draft? nil
   :categories nil})

(defn edit-entry
  "Create an edit-entry suitable for use with the APP protocol."
  [entry-map]
  (merge default-edit-entry-properties (atom-entry entry-map)))



#_(defprotocol Feed
  "Some methods can return nil to signal an ignored field. However,
  some are compulsory as according to the spec."
  (feed-title [this])
  (feed-home-url [this])
  (feed-author-name [this])
  (feed-author-email [this])
  (feed-updated [this])

  ;; all entry-* methods *except for Collection/save-entry* will deal with whatever
  ;; data-structure you provide via get-entr(y|ies).
  (get-entries [this] "Returns a sequence of anything your entry-* methods
                      can deal with.")  

  (entry-title [this item])
  (entry-id [this item])
  (entry-url [this item])
  (entry-published [this item])
  (entry-updated [this item])
  (entry-content [this item])
  (entry-summary [this item])
  (entry-author-name [this item])
  (entry-author-email [this item])
  )

#_(defprotocol Collection
  "If you implement Collection, you must also implement Feed; Collection depends
   on many of Feed's methods."
  (collection-url-root [this])
  (collection-category-scheme [this])
  (collection-categories [this])

  (get-entry [this id])
  ;; save-entry will receive a map with keys corresponding to the
  ;; elements of an atom entry.
  (save-entry [this id-or-nil entry])
  (delete-entry [this id])

  (entry-publish? [this item])
  (entry-categories [this item])
  )



