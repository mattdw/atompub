# atompub

The `atompub` library contains a set of functions to create Atom feeds and collections, and Ring handlers for exposing them in Clojure web-applications.

It is currently still under development, and only syndication (not APP editing etc.) is functional.

## Usage

    (use 'atompub.handlers 'atompub.core)
    (feed-handler
      {:title feed-title
       :url   feed-url
       ...    etc.})      ; see atompub.core/feed-properties
      (map article-to-entry-map (fetch-articles 20)))
    ;; => returns a ring handler

Currently you'll need to handle most of the plumbing, particularly turning your articles/entries/posts into maps as described/assisted by `atompub.core/entry-map`.

`atompub.atom` contains all the more directly-used primitives and XML creation, if that's your thing.

## Maps and Fields

This library expects maps of specific key/values in a few places. The expected keys are listed here.

### Feed-level Properties

This is the data that belongs to the top-level of an atom feed. See `atompub.core/feed-properties`. Values are strings unless specified.

* `title` (required) - the title of the feed.
* `updated` (required, `java.util.Date` or `org.joda.time.DateTime`) - when the feed was last updated. e.g. the 'modified' date of the latest entry.* `url` (required) - the absolute url of the feed. Also used as the feed id.
* `home-url` - an absolute url to a homepage for the site to which the
  feed belongs, or similar.
* `author-name` - name of the author.
* `author-email` - email address of the author.

### Entry-level properties

These are the keys used in creating \<entry> elements. Again, strings unless specified, optional unless noted.

* `title` (required) - title of the entry.
* `id` (required) - a globally unique identifier for the entry. Should never change. Permalinks may be reliable enough.
* `updated` (required, `java.util.Date` or `org.joda.time.DateTime`) - the date of the last significant change to the entry. Might be 'modified date' if you store one, otherwise 'published date' is fine.
* `published` (optional, otherwise as `updated`) - date the entry was first published.
* `url` - a link to an alternative version of the entry. e.g. blog post permalink.
* `content` - the content for the entry. Assumes html.
* `summary` - similar to content. You probably only want one, unless this is an APP collection.
* `author-name` - allows you to override author details for a single entry.
* `author-email` - email address of the author for this post. Not required, even if `author-name` is provided.

In addition, entries destined for editing through APP collections can have the following keys:

* `draft?` - boolean, true for draft status, false to publish.
* `categories` - a vector of category names as strings


## License

Copyright (C) 2011 Matt Wilson

Distributed under the Eclipse Public License, the same as Clojure.
