# atompub

The `atompub` library contains a set of functions to create Atom feeds and collections, and Ring handlers for exposing them in Clojure web-applications.

It is currently still under development, and only syndication (not APP editing etc.) is functional.

## Usage

    (use 'atompub.handlers 'atompub.core)
    (feed-handler
      (feed-properties {:title feed-title
                        :url   feed-url
                        ...    etc.})      ; see atompub.core/feed-properties
      (map article-to-entry-map (fetch-articles 20)))
    ;; => returns a ring handler

Currently you'll need to handle most of the plumbing, particularly turning your articles/entries/posts into maps as described/assisted by `atompub.core/entry-map`.

`atompub.atom` contains all the more directly-used primitives and XML creation, if that's your thing.

## License

Copyright (C) 2010 Matt Wilson

Distributed under the Eclipse Public License, the same as Clojure.
