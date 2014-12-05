(ns atompub.core)

;; ## Feed and Entry prototypes

;; The following section contains a series of default-property maps
;; and helper functions to create (where possible) spec-conforming
;; atom feeds. `feed-properties`, `atom-entry` and `edit-entry` are
;; the suggested way to create appropriate structs; they'll catch
;; missing required fields.

;; ;; Default value for required fields.

;; (def required-field (Exception. "Missing required field"))

;; ;; ### Top-level feed properties

;; (def default-feed-properties
;;   {:title         required-field
;;    :updated       required-field
;;    :url           required-field    ; url to this feed
;;    :home-url      nil               ; url to an appropriate homepage
;;                                     ; for this feed
;;    :author-name   required-field
;;    :author-email  nil})

(defprotocol IAtomFeed
  (feed-title [this] "The feed's title.")
  (feed-updated [this] "Must return something Joda DateTime can use.")
  (feed-url [this] "URL that points to this feed.")
  (feed-home-url [this] "A web link for this feed e.g. homepage. Optional, or nil.")
  (feed-author-name [this] "The primary author's name.")
  (feed-author-email [this] "An email for the primary author. Optional, or nil.")
  (feed-entries [this] "Returns a list of IAtomEntry, and optionally IAtomEditableEntry."))

(defprotocol IAtomCollection
  (collection-category-scheme [this])
  (collection-get-entry [this id])
  (collection-save-entry [this id-or-nil entry])
  (collection-delete-entry [this id]))

(defprotocol IAtomEntry
  (entry-title [this])
  (entry-id [this])
  (entry-updated [this])
  (entry-published [this])
  (entry-url [this])
  
  (entry-content [this])
  (entry-summary [this])
  
  (entry-author-name [this])
  (entry-author-email [this]))

(defprotocol IAtomEditableEntry
  (entry-draft? [this])
  (entry-categories [this]))


(defrecord AtomFeed [title updated url home-url author-name author-email entries]

  IAtomFeed
  (feed-title [this] (:title this))
  (feed-updated [this] (:updated this))
  (feed-url [this] (:url this))
  (feed-home-url [this] (:home-url this))
  (feed-author-name [this] (:author-name this))
  (feed-author-email [this] (:author-email this))
  (feed-entries [this] (:entries this)))


(defrecord AtomEntry [title id updated published url content summary
                      author-name author-email draft? categories]

  IAtomEntry
  (entry-title [this] (:title this))
  (entry-id [this] (:id this))
  (entry-updated [this] (:updated this))
  (entry-published [this] (:published this))
  (entry-url [this] (:url this))
  (entry-content [this] (:content this))
  (entry-summary [this] (:summary this))
  (entry-author-name [this] (:author-name this))
  (entry-author-email [this] (:author-email this))

  IAtomEditableEntry
  (entry-draft? [this] (:draft? this))
  (entry-categories [this] (:categories this)))

;; (defn feed-properties
;;   "Create a top-level feed-properties map."
;;   [props]
;;   (merge default-feed-properties props))

;; ;; ### Per-entry properties (syndication)

;; (def default-entry-properties
;;   {:title         required-field
;;    :id            required-field
;;    :updated       required-field
;;    :published     nil
;;    :url           nil
;;    :content       nil
;;    :summary       nil
;;    :author-name   nil
;;    :author-email  nil})

;; (defn atom-entry
;;   "Create an atom-entry map."
;;   [entry-map]
;;   (merge default-entry-properties entry-map))

;; ;; ### Extra per-entry properties for APP protocol.
;; ;; 
;; ;; Extends default-entry-properties.

;; (def default-edit-entry-properties
;;   {:draft? nil
;;    :categories nil})

;; (defn edit-entry
;;   "Create an edit-entry suitable for use with the APP protocol."
;;   [entry-map]
;;   (merge default-edit-entry-properties (atom-entry entry-map)))



;; #_(defprotocol Feed
;;   "Some methods can return nil to signal an ignored field. However,
;;   some are compulsory as according to the spec."
;;   (feed-title [this])
;;   (feed-home-url [this])
;;   (feed-author-name [this])
;;   (feed-author-email [this])
;;   (feed-updated [this])

;;   ;; all entry-* methods *except for Collection/save-entry* will deal with whatever
;;   ;; data-structure you provide via get-entr(y|ies).
;;   (get-entries [this] "Returns a sequence of anything your entry-* methods
;;                       can deal with.")  

;;   (entry-title [this item])
;;   (entry-id [this item])
;;   (entry-url [this item])
;;   (entry-published [this item])
;;   (entry-updated [this item])
;;   (entry-content [this item])
;;   (entry-summary [this item])
;;   (entry-author-name [this item])
;;   (entry-author-email [this item])
;;   )

;; #_(defprotocol Collection
;;   "If you implement Collection, you must also implement Feed; Collection depends
;;    on many of Feed's methods."
;;   (collection-url-root [this])
;;   (collection-category-scheme [this])
;;   (collection-categories [this])

;;   (get-entry [this id])
;;   ;; save-entry will receive a map with keys corresponding to the
;;   ;; elements of an atom entry.
;;   (save-entry [this id-or-nil entry])
;;   (delete-entry [this id])

;;   (entry-publish? [this item])
;;   (entry-categories [this item])
;;   )



