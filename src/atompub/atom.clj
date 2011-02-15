(ns atompub.atom
  (:use
   [clojure.contrib.prxml :only (prxml)]
   [clojure.contrib.zip-filter.xml :only (xml-> xml1-> tag= attr text)])
  (:require 
   [clojure.contrib.zip-filter :as zf]
   [clojure.zip :as zip]
   [clojure.xml :as xml])
  (:import [org.joda.time DateTime DateTimeZone]))

;; ## Utility functions

(defn make-response [ctype body]
  {:status 200
   :headers {"Content-Type" ctype}
   :body body})

(defn text*
  "Returns the textual contents of the given location, similar to
  xpaths's value-of. Doesn't collapse whitespace, unlike
  contrib.zip-filter.xml/text."
  [loc]
  (apply str (xml-> loc zf/descendants zip/node string?)))

(defn entry-map
  "Takes an XML zipper and finds Atom fields and values."
  [xml-entry extra-fields]
  (let [title (xml1-> xml-entry (tag= :title) text)
        content (xml1-> xml-entry (tag= :content) text*)
        summary (xml1-> xml-entry (tag= :summary) text*)
        draft (xml1-> xml-entry
                      (tag= :app:control)
                      (tag= :app:draft) text)
        categories (xml-> xml-entry (tag= :category) (attr :term))
        id (xml1-> xml-entry (tag= :id) text)]
    (merge extra-fields {:title title
                         :content content
                         :summary summary
                         :draft? (if (= draft "yes") true false)
                         :categories (vec categories)
                         :id id})))

(defn parse-request
  "Parse the body of a ring request into a hash-map of the appropriate
   Atom fields."
  [request]
  (entry-map (-> (:body request) xml/parse zip/xml-zip)
             {:slug (get-in request [:headers "Slug"])}))

;; ## Generating Atom feeds

(defn atom-date
  "Create an atom-format date from anything joda.org.time.DateTime has
  a constructor for -- at least joda DateTime and java.util.Date."
  [x]
  (str (.toDateTime (DateTime. x) DateTimeZone/UTC)))

(defn atom-entry
  "Convert an atompub.core/atom-entry map to XML."
  [item]
  [:entry {:xmlns "http://www.w3.org/2005/Atom"      
           :xmlns:app "http://www.w3.org/2007/app"}
   [:title (:title item)]
   [:id (:id item)]
   [:updated (atom-date (:updated item))]
   (when (:published item)
     [:published (atom-date (:published item))])
   (when (:url item)
     [:link {:href (:url item) :rel "alternate"}])
   (when (:content item)
     [:content {:type "html"} (:content item)])
   (when (:summary item)
     [:summary {:type "html"} (:content item)])])

(defn atom-edit-entry
  "Convert an atompub.core/edit-entry map to XML."
  [item]
  [:entry {:xmlns "http://www.w3.org/2005/Atom"
           :xmlns:app "http://www.w3.org/2007/app"}
   [:title (:title item)]
   [:id (:id item)]
   [:link {:href (:link item) :rel "edit"}]
   [:link {:href (:link item) :rel "self"}]
   (when (:url item)
     [:link {:href (:url item) :rel "alternate" :type "text/html"}])
   [:updated (atom-date (:updated item))]
   [:app:edited (atom-date (:updated item))]
   (when (:published item)
     [:published (atom-date (:published item))])
   (when (not (nil? (:draft? item)))
     [:app:control
      [:app:draft (if (:draft? item) "yes" "no")]])
   (when (:content item)
     [:content {:type "text"} [:cdata! (:content item)]])
   (when (:summary item)
     [:summary {:type "text"} [:cdata! (:summary item)]])
   (when (:categories item)
     (map (fn [cat] [:category {:term cat}]) (:categories item)))])

(defn atom-feed
  "Atom feed document, for both syndication and APP. `entries` should
   be already transformed to XML, as either prxml tree or string."
  [props entries]
  (with-out-str (prxml
    [:decl!]
    [:feed {:xmlns "http://www.w3.org/2005/Atom"}
     (concat
      [[:title (:title props)]
       [:id (:url props)]
       [:link {:rel "self" :href (:url props)}]
       [:link {:rel "alternate" :type "text/html" :href (:home-url props)}]
       [:updated (atom-date (:updated props))]
       (let [name (:author-name props)
             email (:author-email props)]
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
                :xmlns:atom "http://www.w3.org/2005/Atom"}
      [:workspace
       [:atom:title title]
       [:collection {:href (str prefix "/entries/")}
        [:atom:title title]
        [:categories {:href (str prefix "/categories/")}]]]])))

(defn categories-doc
  "Categories document for APP."
  [scheme categories]
  (with-out-str
    (prxml
     [:decl!]
     [:app:categories {:xmlns:app "http://www.w3.org/2007/app"
                       :xmlns:atom "http://www.w3.org/2005/Atom"
                       :fixed "yes"
                       :scheme scheme}
      (for [category categories]
        [:atom:category {:term category}])])))
