# nomis-tailer

A Clojure library for tailing growing files. Understands rollover.

Good for tailing log files.

Similar to `tail -F` in some Unix's.


## Usage


### Tailing a Single File

Use `make-tailer-and-channel` to create a `tailer-and-channel`.

The tailing starts from the end of the file.

Use `channel` to get a core.async channel to take from. The values taken
will be successive lines from the file.

Use `close!` to close a `tailer-and-channel`.


### Tailing Using a File Pattern

Use `make-multi-tailer-and-channel` to create a `multi-tailer-and-channel`.

Looks for the most recent file in `dir` that matches `pattern` and looks
for new files every `file-change-delay-ms`.

The tailing starts from the end of any current file, and includes the
full content of subsequent files.

Use `channel` and `close!` as for `tailer-and-channel`.


### More Detail

See doc strings for more detail.


## License

Copyright © 2016 Simon Katz

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
