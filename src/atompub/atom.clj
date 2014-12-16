(ns atompub.atom
  (:use
   [prxml :only (prxml)]
   [clojure.data.zip.xml :only (xml-> xml1-> tag= attr text)])
  (:require
   [atompub.core :as a]
   [clojure.data.zip :as zf]
   [clojure.zip :as zip]
   [clojure.xml :as xml])
  (:import [org.joda.time DateTime DateTimeZone]
           [atompub.core AtomFeed AtomEntry IAtomEntry IAtomEditableEntry
            IAtomFeed IAtomCollection]))

;; ## Utility functions


(def
  ^{:doc "HTTP Content-Type for Atom feeds"}
  atom-ctype "application/atom+xml; charset=utf-8")

(defn make-response
  "Format a Ring response with a given Content-Type and body."
  [ctype body]
  {:status 200
   :headers {"Content-Type" ctype}
   :body body})

(defn xml-to-str
  "Convert a nested structure in prxml format (see below) to an XML document
  as a string."
  [xml-struct]
  (with-out-str (prxml xml-struct)))

(defn text*
  "Returns the textual contents of the given location, similar to
  xpaths's value-of. Doesn't collapse whitespace, unlike
  `contrib.zip-filter.xml/text`."
  [loc]
  (apply str (xml-> loc zf/descendants zip/node string?)))

(defn entry-from-xml
  "Takes an XML zipper and returns an `AtomEntry` record."
  [xml-entry extra-fields]
  (let [title (xml1-> xml-entry (tag= :title) text)
        content (xml1-> xml-entry (tag= :content) text*)
        summary (xml1-> xml-entry (tag= :summary) text*)
        draft (xml1-> xml-entry
                      (tag= :app:control)
                      (tag= :app:draft) text)
        categories (xml-> xml-entry (tag= :category) (attr :term))
        id (xml1-> xml-entry (tag= :id) text)]
    (atompub.core/map->AtomEntry
     (merge extra-fields {:title title
                          :content content
                          :summary summary
                          :draft? (if (= draft "yes") true false)
                          :categories (vec categories)
                          :id id}))))

(defn parse-request
  "Parse the body of a ring request (assuming an XML AtomEntry) into an
  `AtomEntry` record from the appropriate Atom fields."
  [request]
  (entry-from-xml (-> (:body request) xml/parse zip/xml-zip)
                  {:slug (get-in request [:headers "Slug"])}))

;; ## Generating Atom feeds

(defn atom-date
  "Create an atom-format date from anything joda.org.time.DateTime has
  a constructor for -- at least joda `DateTime` and `java.util.Date`."
  [x]
  (str (.toDateTime (DateTime. x) DateTimeZone/UTC)))

(defn atom-entry
  "Return a prxml-format struct from an IAtomEntry."
  [^IAtomEntry item]
  [:entry {:xmlns "http://www.w3.org/2005/Atom"
           :xmlns/app "http://www.w3.org/2007/app"}
   [:title (a/entry-title item)]
   [:id (a/entry-id item)]
   [:updated (atom-date (a/entry-updated item))]
   (if-let [pub (a/entry-published item)]
     [:published (atom-date pub)])
   (if-let [url (a/entry-url item)]
     [:link {:href url :rel "alternate"}])
   (let [name (a/entry-author-name item)
         email (a/entry-author-email item)]
     (when name
       [:author
        [:name name]
        (when email [:email email])]))
   (if-let [content (a/entry-content item)]
     [:content {:type "html"} content])
   (if-let [summary (a/entry-summary item)]
     [:summary {:type "html"} summary])])

(defn atom-edit-entry
  "Convert an IAtomEntry+IAtomEditableEntry to a prxml struct."
  [^IAtomEditableEntry item prefix]
  [:entry {:xmlns "http://www.w3.org/2005/Atom"
           :xmlns/app "http://www.w3.org/2007/app"}
   [:title (a/entry-title item)]
   [:id (a/entry-id item)]
   [:link {:href (str prefix "entries/" (a/entry-id item)) :rel "edit"}]
   [:link {:href (str prefix "entries/" (a/entry-id item)) :rel "self"}]
   (if-let [url (a/entry-url item)]
     [:link {:href url :rel "alternate" :type "text/html"}])
   [:updated (atom-date (a/entry-updated item))]
   [:app/edited (atom-date (a/entry-updated item))]
   (if-let [pub (a/entry-published item)]
     [:published (atom-date pub)])
   (when (not (nil? (a/entry-draft? item)))
     [:app/control
      [:app/draft (if (a/entry-draft? item) "yes" "no")]])
   (if-let [content (a/entry-content item)]
     [:content {:type "text"} [:cdata! content]])
   (if-let [summary (a/entry-summary item)]
     [:summary {:type "text"} [:cdata! summary]])
   (let [name (a/entry-author-name item)
         email (a/entry-author-email item)]
     (when name
       [:author
        [:name name]
        (when email [:email email])]))
   (if-let [cats (a/entry-categories item)]
     (map (fn [cat] [:category {:term cat}]) cats))])

(defn atom-feed
  "Atom feed document, for both syndication and APP. `entries` should
   be already transformed to XML, as either prxml tree or string."
  [feed entries]
  (with-out-str (prxml
    [:decl!]
    [:feed {:xmlns "http://www.w3.org/2005/Atom"}
     (concat
      [[:title (a/feed-title feed)]
       [:id (a/feed-url feed)]
       [:link {:rel "self" :href (a/feed-url feed)}]
       [:link {:rel "alternate" :type "text/html" :href (a/feed-home-url feed)}]
       [:updated (atom-date (a/feed-updated feed))]
       (let [name (a/feed-author-name feed)
             email (a/feed-author-email feed)]
         (when name
           [:author
            [:name name]
            (when email [:email email])]))]
      entries)])))

(defn service-doc
  "Return a service document describing a collection. `prefix` is the
   url root for this collection, and title is a human-readable name for
   it."
  [prefix title]
  (with-out-str
    (prxml
     [:decl!]
     [:service {:xmlns "http://www.w3.org/2007/app"
                :xmlns/atom "http://www.w3.org/2005/Atom"}
      [:workspace
       [:atom/title title]
       [:collection {:href (str prefix "entries/")}
        [:atom/title title]
        [:categories {:href (str prefix "categories/")}]]]])))

(defn categories-doc
  "Categories document for APP."
  [scheme categories]
  (with-out-str
    (prxml
     [:decl!]
     [:app/categories {:xmlns/app "http://www.w3.org/2007/app"
                       :xmlns/atom "http://www.w3.org/2005/Atom"
                       :fixed "yes"
                       :scheme scheme}
      (for [category categories]
        [:atom/category {:term category}])])))
