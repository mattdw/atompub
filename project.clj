(defproject atompub "1.0.0-SNAPSHOT"
  :description "An implementation of Atom Syndication and the Atom Publishing Protocol."
  :url "http://github.com/mattdw/atompub"
  :license "Eclipse Public License 1.0"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/data.zip "0.1.1"]
                 [joda-time "1.6"]
                 [net.cgrand/moustache "1.0.0-SNAPSHOT"]]
  :plugins [[lein-marginalia "0.8.0"]])
