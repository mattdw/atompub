(ns atompub.atom
  (:use
   ;[clojure.xml :only (StringEscapeUtils)]
   [clojure.data.zip.xml :only (xml-> xml1-> tag= attr text)])
  (:require 
   [atompub.core :as a]
   [clojure.data.zip :as zf]
   [clojure.zip :as zip]
   [clojure.xml :as xml])
  (:import [org.joda.time DateTime DateTimeZone]
           [atompub.core AtomFeed AtomEntry IAtomEntry IAtomEditableEntry
            IAtomFeed IAtomCollection]
           [org.apache.commons.lang StringEscapeUtils]))

;; ## Utility functions

(defn emit-element [e]
  (if (instance? String e)
    (print (StringEscapeUtils/escapeXml e))
    (do
      (print (str "<" (name (:tag e))))
      (when (:attrs e)
	(doseq [attr (:attrs e)]
	  (print (str " " (name (key attr)) "='" (StringEscapeUtils/escapeXml (val attr)) "'"))))
      (if (:content e)
	(do
	  (print ">")
	  (doseq [c (:content e)]
	    (emit-element c))
	  (print (str "</" (name (:tag e)) ">")))
	(print "/>")))))

(defn emit
  ([x]
   (println "<?xml version='1.0' encoding='UTF-8'?>")
   (emit-element x))
  ([_ x]
   (emit x)))

(def atom-ctype "application/atom+xml; charset=utf-8")

(defn make-response [ctype body]
  {:status 200
   :headers {"Content-Type" ctype}
   :body body})

(defn xml-to-str [xml-struct]
  (with-out-str (emit xml-struct)))

(defn text*
  "Returns the textual contents of the given location, similar to
  xpaths's value-of. Doesn't collapse whitespace, unlike
  contrib.zip-filter.xml/text."
  [loc]
  (apply str (xml-> loc zf/descendants zip/node string?)))

(defn entry-from-xml
  "Takes an XML zipper and returns an AtomEntry record."
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
  "Parse the body of a ring request into a hash-map of the appropriate
   Atom fields."
  [request]
  (entry-from-xml (-> (:body request) xml/parse zip/xml-zip)
                  {:slug (get-in request [:headers "Slug"])}))

;; ## Generating Atom feeds

(defn atom-date
  "Create an atom-format date from anything joda.org.time.DateTime has
  a constructor for -- at least joda DateTime and java.util.Date."
  [x]
  (str (.toDateTime (DateTime. x) DateTimeZone/UTC)))

(defn atom-entry
  "Convert an IAtomEntry to XML."
  [^IAtomEntry item]
  [:entry {:xmlns "http://www.w3.org/2005/Atom"      
           :xmlns:app "http://www.w3.org/2007/app"}
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
  "Convert an IAtomEntry+IAtomEditableEntry to XML."
  [^IAtomEditableEntry item prefix]
  [:entry {:xmlns "http://www.w3.org/2005/Atom"
           :xmlns:app "http://www.w3.org/2007/app"}
   [:title (a/entry-title item)]
   [:id (a/entry-id item)]
   [:link {:href (str prefix "entries/" (a/entry-id item)) :rel "edit"}]
   [:link {:href (str prefix "entries/" (a/entry-id item)) :rel "self"}]
   (if-let [url (a/entry-url item)]
     [:link {:href url :rel "alternate" :type "text/html"}])
   [:updated (atom-date (a/entry-updated item))]
   [:app:edited (atom-date (a/entry-updated item))]
   (if-let [pub (a/entry-published item)]
     [:published (atom-date pub)])
   (when (not (nil? (a/entry-draft? item)))
     [:app:control
      [:app:draft (if (a/entry-draft? item) "yes" "no")]])
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
  [props entries]
  (with-out-str
    (emit
     {:tag :feed
      :attrs {:xmlns "http://www.w3.org/2005/Atom"}
      :content
      (concat
       [{:tag :title :content [(a/feed-title props)]}
        {:tag :id :content [(a/feed-url props)]}
        {:tag :link :attrs {:rel "self" :href (a/feed-url props)}}
        {:tag :link
         :attrs {:rel "alternate" :type "text/html" :href (a/feed-home-url props)}}
        {:tag :updated :content [(atom-date (a/feed-updated props))]}
        (let [name (a/feed-author-name props)
              email (a/feed-author-email props)]
          (if name
            {:tag :author
             :content
             [{:tag :name :content [name]}
              (if email {:tag :email :content [email]} "")]}
            ""))]
       entries)})))

(defn service-doc
  "Return a service document describing a collection. `prefix` is the
   url root for this collection, and title is a human-readable name for
   it."
  [prefix title]
  (with-out-str
    (emit
     {:tag :service
      :attrs {:xmlns "http://www.w3.org/2007/app"
              :xmlns:atom "http://www.w3.org/2005/Atom"}
      :content
      [{:tag :workspace
        :content
        [{:tag :atom:title :content [title]}
         {:tag :collection
          :attrs {:href (str prefix "entries/")}
          :content
          [{:tag :atom:title :content [title]}
           {:tag :categories :attrs {:href (str prefix "categories/")}}]}]}]})))

(defn categories-doc
  "Categories document for APP."
  [scheme categories]
  (with-out-str
    (emit
     {:tag :app:categories
      :attrs {:xmlns:app "http://www.w3.org/2007/app"
              :xmlns:atom "http://www.w3.org/2005/Atom"
              :fixed "yes"
              :scheme scheme}
      :content
      (for [category categories]
        {:tag :atom:category :attrs {:term category}})})))
