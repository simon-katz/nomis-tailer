# nomis-tailer

A Clojure library for tailing growing files. Understands rollover.

Good for tailing log files.

Similar to `tail -F` in some Unix's.


## Usage


### Dependencies

Dependency information is at this clickable badge:

[![Clojars Project](https://img.shields.io/clojars/v/com.nomistech/nomis-tailer.svg)](https://clojars.org/com.nomistech/nomis-tailer)

Add the following to your `ns` declaration:

```
(:require [nomis-tailer.core :as tailer])
```

### Tailing a Single File

Use `tailer/make-single-file-tailer` to create a single-file-tailer.

The tailing starts from the end of the file.

Use `tailer/channel` to get a core.async channel to take from. The values taken
will be successive lines from the file.

Use `tailer/close!` to close a single-file-tailer.


### Tailing Using a File Pattern

Use `tailer/make-multi-file-tailer` to create a multi-file-tailer.

Looks for the most recent file in `dir` that matches `pattern` and looks
for new files every `file-change-delay-ms`.

The tailing starts from the end of any current file, and includes the
full content of subsequent files.

Use `tailer/channel` and `tailer/close!` as for a single-file-tailer.


### More Detail

See doc strings for more detail.


## License

Copyright © 2016 Simon Katz

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
