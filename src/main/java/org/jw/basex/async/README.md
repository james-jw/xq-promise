### Implementation Details

This library is implemented for [BaseX][1] via the [QueryModule][4] class. It leverages Jave 7's [ForkJoinPool][2] and [RecursiveTasks][3] classes and patterns. There are three ``java`` source files as part of the implementation:
* XqPromise.java
* XqDeferred.java
* XqForkJoinTask.java

#### XqPromise
The XqPromise class implements [QueryModule][4] from the BaseX API and exposes the methods described earlier:
* defer
* when
* fork-join
* is-promise

#### XqDeferred
This class is at the core of the [promise][1] pattern and represents a unit of work to perform in the future. It implements the ``XQFunction`` interface from the [BaseX][1] API. and thus is a function. 

If passed an empty sequence, it executes it's work.

If provided a map of callback functions, the callbacks are added but no execution is performed. The return value
is a new deferred which will include the added callback in it's pipeline.

#### XqForkJoinTask
Implements [RecursiveTask][3] and performs the forking process leveraging a fixed [ForkJoinPool][2]

The pool size is deteremined by default by the number of CPU cores. See the Advanced section for overriding this.
