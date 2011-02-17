(ns atompub.test.handlers
  (:use [atompub.handlers] :reload)
  (:use [clojure.test]
        [ring.util.test :only (string-input-stream)]))

(defn request [meth uri body]
  {:server-port    80
   :server-name    "localhost"
   :remote-addr    "127.0.0.1"
   :uri            uri
   :scheme         :http
   :request-method meth
   :headers        {}
   :body           (when body (string-input-stream body))})

(def static-data
  {"1" {:title   "Title 1"
        :updated (java.util.Date.)
        :content "Content 1"
        :id 1}
   "2" {:title   "Title 2"
        :updated (java.util.Date.)
        :content "Content 2"
        :id 2}})

(def static-methods
  {:get-entries    #(vals static-data)
   :get-entry      #(static-data %)
   :get-categories (constantly ["Category 1" "Category 2"])
   :delete-entry   (constantly {:status 200})
   :save-entry     (fn [id-or-nil _]
                     (static-data (or id-or-nil "1")))})

(def static-props
  {:title   "Test Collection"
   :url     "http://localhost/"
   :updated (java.util.Date.)})

(deftest give-responses
  (let [app (collection-handler static-props static-methods)
        svc (app (request :get "/" nil))
        entries (app (request :get "/entries/" nil))

        entry-1 (app (request :get "/entries/1" nil))
        new-entry (app (request :post "/entries/" "<entry><title>Title</title></entry>"))
        edit-entry (app (request :put "/entries/1" "<entry><title>Title</title></entry>"))
        delete-entry (app (request :delete "/entries/2" nil))

        categories (app (request :get "/categories/" nil))]

    ;; service doc
    (is (and (re-find #"\<service" (:body svc))
             (= (get-in svc [:headers "Content-Type"])
                "application/atomsvc+xml; charset=utf-8")))
    (is (re-find #"http://localhost/entries/" (:body svc)))
    (is (re-find #"http://localhost/categories/" (:body svc)))

    ;; all entries
    (is (= 200 (:status entries)))
    (is (re-find #"\<feed " (:body entries)))
    (is (re-find #"\<entry " (:body entries)))
    (is (re-find #"\<title>\s*Title 1" (:body entries)))
    (is (re-find #"/entries/1" (:body entries)))

    ;; single entry
    (is (= 200 (:status entry-1)))
    (is (re-find #"\<entry " (:body entry-1)))
    (is (re-find #"\<title>\s*Title 1" (:body entry-1)))
    (is (not (re-find #"\<feed " (:body entry-1))))

    ;; create entry
    (is (= 201 (:status new-entry)))
    (is (not (empty? (get-in new-entry [:headers "Location"]))))
    (is (re-find #"\<title>\s*Title 1" (:body new-entry)))

    ;; edit entry
    (is (= 200 (:status edit-entry)))
    (is (re-find #"\<title>\s*Title 1" (:body edit-entry)))

    ;; delete entry
    (is (= 200 (:status delete-entry)))
    (is (empty? (:body delete-entry)))

    ;; categories
    (is (= 200 (:status categories)))
    (is (re-find #"\<app:categories " (:body categories)))
    (is (re-find #"\<atom:category term=.Category 2" (:body categories)))
    ))

(def error-methods
  {:get-entries #(.trim nil)})

(deftest catch-errors
  (let [app (collection-handler static-props error-methods)
        response (app (request :get "/entries/" nil))]
    (prn response)
    (is (= 500 (:status response)))
    (is (re-find #"Server Error: " (:body response)))
    (is (re-find #"Exception" (:body response)))))
