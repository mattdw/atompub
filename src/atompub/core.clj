(ns atompub.core)

;; # Atom Syndication and Publishing Protocol

;; This library assumes you are at least passingly familiar with the
;; Atom format and protocol. If not, start here:
;;
;; * <http://atomenabled.org>
;; * <http://www.ietf.org/rfc/rfc4287.txt>
;; * <http://bitworking.org/projects/atom/draft-ietf-atompub-protocol-04.html>
;;
;; This namespace defines all the necessary protocols, and helpful
;; defrecords as shortcuts for feeds (non-editable) and entries.
;;
;; This library uses "feed" and "collection" in the same sense as the
;; Atom documentation; "feed" is a single read-only XML document, and
;; "collection" is (essentially) a feed with extra logic provided for
;; adding, deleting, editing etc.

;; ## Feeds

(defprotocol IAtomFeed

  "This protocol is required for any type of feed; whether purely for
  syndication, or to be exposed via the publishing protocol."

  (feed-title [this]
    "The feed's title.")
  (feed-updated [this]
    "Updated date/time. Can return anything that Joda DateTime can
    use.")
  (feed-url [this]
    "The URL that points to this feed.")
  (feed-home-url [this]
    "A web link for this feed e.g. homepage. Optional, or nil.")
  (feed-author-name [this]
    "The primary author's name.")
  (feed-author-email [this]
    "An email for the primary author. Optional, or nil.")
  (feed-entries [this]
    "Must returns a list of IAtomEntry, and optionally
    IAtomEditableEntry if intended as an editable collection."))

(defprotocol IAtomCollection

  "This protocol must also be implemented in order to expose an
  editable collection. Library code assumes that any object
  implementing this protocol has also implemented IAtomFeed; that is,
  IAtomFeed is a requirement of IAtomCollection.

  The ids used by this protocol are largely opaque, but must be
  appropriate as URL segments. That is, they must be stringifiable, and
  this library currently won't handle anything with special characters,
  as it does no escaping. Integers or simple strings are recommended."

  (collection-category-scheme [this]
    "A seq of strings. May be empty.")
  (collection-get-entry [this id]
    "Retrieve an entry by (opaque) id.")
  (collection-save-entry [this id-or-nil entry]
    "Save an entry. `entry` will be provided as an instance of
    AtomEntry below. If `id-or-nil` is nil, create a new record.
    Otherwise update an existing one.")
  (collection-delete-entry [this id]
    "Delete the entry at `id`."))

;; ## Entries

(defprotocol IAtomEntry

  "IAtomEntry should be implemented for both editable and simple
  subscription entries. However, `entry-id` should return a guid
  (frequently a permalink) for subscription-type entries, but a simple
  key for editable entries. (See the note in IAtomCollection regarding
  ids.)"

  (entry-title [this]
    "The title of the entry")
  (entry-id [this]
    "The id of the entry. A GUID or permalink for non-editable entries,
    or a simple key for editable entries. See notes above.")
  (entry-updated [this]
    "Date/time updated. As for feeds, takes anything Joda DateTime can
    use. Commpulsory per spec.")
  (entry-published [this]
    "As per `updated`, and will frequently be the same. Optional per
    spec.")
  (entry-url [this]
    "The canonical URL (permalink) for this entry.")

  ;; For syndication, only one of these should return content, the
  ;; other should return nil.

  (entry-content [this]
    "The entry's main content. Likely HTML for syndication, or for
    editing whatever your raw format is e.g. Markdown, plain text,
    HTML.")
  (entry-summary [this]
    "A summary or excerpt of the entry's content.")

  (entry-author-name [this]
    "The author's name. Optional.")
  (entry-author-email [this]
    "The author's email. Optional."))

(defprotocol IAtomEditableEntry
  (entry-draft? [this]
    "Is this entry a draft? Returns boolean.")
  (entry-categories [this]
    "A list of categories this entry belongs to. Should use keys found
    in `collection-category-scheme` above."))

;; ## Helper defrecords

;; The AtomFeed record type is of limited use as `entries` is just a static
;; field.

(defrecord AtomFeed 
  [title updated url home-url author-name author-email entries]

  IAtomFeed
  (feed-title [this] (:title this))
  (feed-updated [this] (:updated this))
  (feed-url [this] (:url this))
  (feed-home-url [this] (:home-url this))
  (feed-author-name [this] (:author-name this))
  (feed-author-email [this] (:author-email this))
  (feed-entries [this] (:entries this)))

;; The AtomEntry record allows you to use a simple map as an AtomEntry.

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


