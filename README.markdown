rccoreutil
----------
Hi there!

You've stumbled on my project of core java utilities that I use as a starting point on new projects.  Feel free to browse, but be aware that I did not make much of an effort to make any of this usable or documented to anyone but myself.  Still, there are some tricky integrations in here and it may be useful for learning value or as example code.  If you find any of this useful, drop me a line.

Most of the asynchronous IO bits are based on netty.  Some of the older ones originally used grizzly so there may still be some funky things in there from the refactor.

Contents
--------

* Asynchronous programming helpers (net.rcode.core.async). Promise and Flow abstractions.
* Asynchronous CouchDb client (net.rcode.core.couchdb)
* Asynchronous http client (net.rcode.core.http)
* Asynchronous http server tools (net.rcode.core.httpserver)
* General IO utilities (net.rcode.core.io).  Stream and Reader utilities, async dns.
* Redis based queue (net.rcode.core.queue)
* Asynchronous Redis client driver (net.rcode.core.redis)
* High level web abstractions (net.rcode.core.web).  Static file handling, mime types, templates, asset management


Thanks for visiting.

stella