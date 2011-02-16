# atompub

The `atompub` library contains a set of functions to create Atom feeds and collections, and Ring handlers for exposing them in Clojure web-applications.

It is currently still under development.

## Usage

    (use 'atompub.handlers)
    (feed-handler
      {:title feed-title
       :url   feed-url
       ...    etc.})      ; see atompub.core/feed-properties
      #(map my-article-to-entry-map-function (fetch-articles 20)))
    ;; => returns a ring handler

`atompub.handlers` contains the two main entry-points, `feed-handler` and `collection-handler`, which both return [Ring](https://github.com/mmcgrana/ring) handlers.

`atompub.atom` contains all the more directly-used primitives and XML creation, if that's your thing.

## Maps and Fields

This library expects maps of specific key/values in a few places. The expected keys are listed here.

### Feed-level Properties

This is the data that belongs to the top-level of an atom feed. See `atompub.core/feed-properties`. Values are strings unless specified.

* `:title` (required) - the title of the feed.

* `:url` (required) - the absolute url of the feed. Also used as the feed id.

* `:home-url` - an absolute url to a homepage for the site to which the
  feed belongs, or similar.

* `:author-name` - name of the author.

* `:author-email` - email address of the author.

Additionally, `:updated` is required for the lower-level functionality in `atompub.atom`, but `atompub.handlers/feed-handler` assumes that the feed's `:updated` is the same as the `:updated` of the first entry in the feed (i.e. it assumes reverse-chronological ordering of feed entries by `:updated`).

### Entry-level properties

These are the keys used in creating \<entry> elements. Again, strings unless specified, optional unless noted.

* `:title` (required) - title of the entry.

* `:id` (required) - for syndication, a globally unique identifier for the entry. Should never change. Permalinks may be reliable enough. For APP editable entries, it only needs to be locally unique, so it should just be something like a numeric primary key, as it ends up in URLs.

* `:updated` (required, `java.util.Date` or `org.joda.time.DateTime`) - the date of the last significant change to the entry. Might be 'modified date' if you store one, otherwise 'published date' is fine.

* `:published` (optional, otherwise as `:updated`) - date the entry was first published.

* `:url` - a link to an alternative version of the entry. e.g. blog post permalink.

* `:content` - the content for the entry. Assumes html.

* `:summary` - similar to `:content`. You probably only want one, unless this is an APP collection.

* `:author-name` - allows you to override author details for a single entry.

* `:author-email` - email address of the author for this post. Not required, even if `:author-name` is provided.

In addition, entries destined for editing through APP collections can have the following keys:

* `:draft?` - boolean, `true` for draft status, `false` to publish.

* `:categories` - a vector of category names as strings

## Atom Publishing Protocol: A Guide

The Atom Publishing Protocol is, for better or worse (in my opinion better) tied pretty closely to HTTP. As such, this library has a few general principles for integration.

### Error handling

The methods you provide (callbacks, roughly speaking) should signal errors by returning [Ring](https://github.com/mmcgrana/ring) responses, with the appropriate HTTP status code to signal the error. The body of the response may be any textual information you wish to provide, but it's not necessary. Some examples:

* "`:get-entry` was called with an id that doesn't seem to exist":

        {:status 404, :body "No such entry."}
      
* "`:delete-entry` failed because the database didn't respond":

        {:status 500}

* "`:delete-entry` failed because this user doesn't have the right permissions to delete this item":

        {:status 401}

A quick list of useful response codes you might have reason to use:

    200 OK
    201 Created
    401 Unauthorized
    404 Not Found
    409 Conflict
    410 Gone
    500 Internal Server Error

This library doesn't care what status codes are used; if you return a Ring response map, it'll just be passed back to the client.

### General Use

You expose an APP collection with the `collection-handler` function, which returns a Ring handler. You'll probably want to wrap it in some kind of authentication middleware.

`collection-handler` takes a set of feed-properties as described above, with the following caveats:

* The `:url` key must have a trailing slash, and will point to the Atom Service Document for the collection, not the feed itself (which nests underneath the service document.) You should consider all sub-urls of `:url` to be reserved.

  A good example might be the following moustache snippet (which ignores authentication for simplicity):
  
        ["admin" "blog-posts"] (collection-handler
                                 {:url "http://example.com/admin/blog-posts/" ...}
                                 ...)

`collection-handler`'s second argument is a map of functions. All functions may return Ring responses for errors, as described above. The required functions are the following:

* `:get-entries`
  * Takes no arguments
	* Returns a sequence of maps as described in the "Entry-level properties" section (optionally including the `:draft?` and `:categories` keys.)

* `:get-categories`
  * Takes no arguments
  * Returns a sequence of category names as strings.

* `:get-entry`
  * Takes an id (as you provided in your `:get-entries` maps.)
  * Returns a single map as per `:get-entries` above.

* `:delete-entry`
  * Takes an id
  * Returns `true`, or an appropriate Ring response

* `:save-entry`
  * First argument may be either `nil` or an id. If it's `nil`, the function should create a new entry. If non-nil, it should be treated as the id of the post to be updated.
  * Second argument is a map of atom fields. It may not contain all the fields you provided. It may also contain fields you haven't provided, e.g. `:draft?`.
  * Returns a Ring response in case of error, otherwise an entry map describing the new (saved) entry.

## License

Copyright (C) 2011 Matt Wilson

Distributed under the Eclipse Public License, the same as Clojure.
