# xq-promise
An implementation of the promise pattern, as well as fork-join async processing for XQuery 3.1

## What is it?
This library to implement the [promise][0] pattern as seen in many other languages and frameworks. Most notably those in the javascript community. 

The pattern resolves around the idea of <code>deferred</code> execution through what is often called a <code>defer</code> object. When an action is deferred, it returns a function, known as <code>promise</code> that when executed at a later time will perform and return the results of the work it deferred. 

Additionally, with the <code>defer</code> and <code>promise</code> functions comes the ability to attach further processing at a later date, prior to actual execution via callback functions. This may sound confusing and hard to image; however, the examples below should hopefully make clearer.

## Why?
The main driver behind implementing the promise pattern was to realize <code>async</code> execution of XQuery code within a single query. If this sounds enticing, keep reading!

In my initial testing, many queries execute in under 1/5 sometimes, 1/10th the time.
Take for example making 50 http requests. Without fork join, this takes upwards of 50 seconds, with 5.

## Thanks!
I want to thank the [BaseX][1] team for their wonder implementation of ``XQuery`` and the BaseX system in general.
It is because of their hard work, great documentation, code samples and stellar, architecture, code quality and readability that this module was made possible! 

## Version BETA
This module is currently in Beta and should be used with caution especially in scenarios involving the
writing of sensitive data. 

## Dependencies
This is currently dependent on [Basex][1] and is not implementation agnostic.

## Installation
Copy the ``xq-promise-x.jar`` into your ``basex\lib`` directory 

Or use [xqpm][5] to do it for you:
```
xqpm xq-promise
```

Then import the
``org.jw.basex.async.xq-promse` module.


## Tests
Clone the repo and run ``basex -t`` within repo's directory to run the unit tests.

## Declaration
To use the module in your scripts simple import it like so:

```xquery
import module namespace promise = 'org.jw.basex.async.xq-promise';
```

## Whats included?
#### Methods:
* defer
* when
* fork-join

In its current iteration the library includes 3 methods with several overloads. The methods are as follows:

### defer
```xquery
defer($work as function(*), 
      $arguments as item()*, 
      map(xs:string,function(*)*)?) 
  as function(map(xs:string,function(*)*))
```

The signature may look daunting but the pattern is simple. Use the <code>defer</code> method to defer a piece of work for later execution by passing in a function item and the future arguments. Lets see how this works with an example:

```xquery
import module namespace promise = 'org.basex.query.func.async.xq-promise';
let $work := function($name) {
   'Hello ' || $name || '!'
}
let $promise := promise:defer($work, 'world')
return
  $promise
```
In the above example, we defer the execution of the $work method until we return <code>$promise</code> 

But wait! If you examine the output in [basex][1]. The value returned is: <code>function (anonymous)#1</code>. This is not at all what we want.

This is where the power of the promise pattern starts to be realized. Formost, a mentioned prior, a promise '`is`' a function. To retrieve its value, it must be called:

```xquery
$promise(())
```
The above modifcation will result in the expected answer: <code>Hello world!</code>

Now you may be wondering about the <code>$promise(())</code>, in particular the passing of the ``()`` empty sequence. By passing an empty sequence into the promises method we instruct it to exceute its work and return the results. The alternative is to pass in a ``map(xs:string, function(*))`` of callbacks!

### Callbacks
In the above example we deferred a simple piece of work and then learned how to execute it at a later time by passing in the empty sequence. Now let me introduce the real power of the [promise][0] pattern with <code>callbacks</code>

A ``callback`` is a function which will be executed on the success or failure of some defered work. The available callback events to subscribe to are:

##### then
Called on success of a deferred exectuion. Acts a pipeline function for transforming response for successive callback attachements.

##### done
Called on success. Has not affect on response

##### always
Same as done, but also called on failure

##### fail
Called if the action fails. Returning a result mitigates the failure while throwing an exception propegates it.

#### Adding callbacks
There are two ways to add callbacks. During its creation, or after.

Lets see an example of the first case. Imagine we want to make a request using the standard ``http:send-request`` method and then extract the results in a single streamlined call. Here is how this could be accomplished using <code>promises</code> pattern and attaching a ``then`` <code>callback</code>
```xquery
let $req := <http:request method="GET" />
let $request := http:send-request($req, ?)
let $extract-body := function ($res) { $res[2] }
let $promise := promise:defer($request, 'http://www.google.com', map { 'then': $extract-body })
return
  $promise(())
```
In the above example we attached a ``then`` callback. As stated above, this function has the ability to transform the output of its parent ``promise``. With this in the mind it should be clear that the ``$extract-body`` methods return value will be realized on the call to ``$promise(())``. Since the ``$extract-body's`` input will be the result of its parent ``promise`` the result will simply be the response body of the http request.

Multiple callbacks can be attached to one of the 4 events. For example:
```xquery
 ... same $req, etc.. from above ...
let $extract-links := function ($res) { $res//a }
let $promise := promise:defer($request, 'http://www.google.com', map { 
    'then': ($extract-body, $extract-links),
    'fail': trace(?, 'Execution failed: ')
})
return
  $promise(())
```
Foremost, note the addition of a second ``then`` callback. These will be called in order and thus the result of the firsts callback will be passed to the seconds. In this example the result is all of the ``a`` links in the document!

Second, note the ``fail`` callback. In this case we used the power of XQuery 3.0 and function items to simply add a trace call if any part of the execution fails, how convenient!

Hopefully its starting to come clear how the ``promise`` pattern can be quite useful.

#### when
Another critical method in the [promise][0] pattern is the ``when`` function.
```xquery
when($promises as function(map(xs:string,function(*)), 
     $callbacks as map(*,function(*))) 
     as function(map(*,function(*)))
```

The purpose of  ``when`` is to combine 2 or more promised actions during execution. This is extremly powerful. Like the ``defer`` method disscussed earlier, the ``when`` method also returns a deferred ``promise`` itself and also, accepts callbacks just the same.

For example:
```xquery
let $write-and-return-users:= function ($name, $users) as item()* {(
      file:write($name, $users),
      $users
)}
let $extractDocName := promise:defer(doc(?), $doc-uri, map { 'then': extract-name(?) })
let $extractUsers := promise:defer(json-doc(?), $uri, map { 'then': $extractListItems }) 
let $users:= promise:when(($extractDocName, $extractUsers), map { 
               'then': $write-and-return-users,
               'fail': trace(?, 'Requesting users failed: ')
})
return
    $users(()) ! trace(.?username, 'Retrieved: ')
```

In this example we perform two deferred actions and then merge their results in the ``$write-and-return-users`` callback. Since this item is attached to the ``when's`` promise on the ``then`` callback, its result will be seen on the call to ``$users(())``.

We could continue to attach callbacks as needed until we are ready. There is no limit.

#### Attach after creation
So far, all the examples have attached ``callbacks`` during the call with ``defer`` or ``when`` ;however there is another, even more powerful way. 
A ``promise`` can accept callbacks too!

For example:
```xquery
(: Same $worker, $req as above etc... :)
let $extractListItems := function ($res as map(*)) { $res?list?* } 
let $error := function ($result as item()*) {
     trace($result, 'Request failed!') => prof:void()
}
let $retrieve := proc:defer($worker, ($req, $uri), map { 
           'then': parse-json(?), 
           'fail': $error 
}) 
let $extract = $retrieve(map { 'then': $extractListItems  })
return
   $retrieve(())
```

## The Power of Async!
Hopefully its clear now, how to defer work, what a promise is and how to join multiple promises. It still may not be entirely clear the benefit with this pattern XQuery; however that is about to change.

Let me introduce one last method, and the whole reason I wrote this library.

### Fork-join
```xquery
fork-join($promises as function(*)*) as item()*
```

It is the simplest yet most powerful of the methods. It accepts a sequence of promises and executes them in a fork join fashion, spawning threads as needed 
depending on the work load, followed by rejoining the work on the main thread. 
As seen earlier, ``promises`` can be used to build up a piece of work for later execution. With this ability, coupled with ``fork-join``, async ``XQuery`` processing becomes a reality. 

Lets see how we can use this capability by comparing a simple example involving making http requests, using deferred ``promised`` execution but without ``fork-join`` just yet. 

```xquery
import module namespace async = 'org.jw.basex.async.xq-promise';
let $work := http:send-request(<http:request method="GET" />, ?)
let $extract-doc := function ($res) { $res[2] }
let $extract-links := function ($res) { $res//a[@href => matches('^http')] }
let $promises :=
  for $uri in ((1 to 5) !  ('http://www.google.com', 'http://www.yahoo.com', 'http://www.amazon.com', 'http://cnn.com', 'http://www.msnbc.com'))
  let $defer := async:defer($work, $uri, map {
       'then': ($extract-djc),
       'done': trace(?, 'Results found: ')})
  return 
     $defer(map {'then': $extract-links })
return 
 $promises ! .(())
```

In the above example, we use promises to queue up 25 requests and then execute them in order with:
```xquery
 $promises ! .(())
```
If you run this example in BaseX GUI and watch the output window, you will see the requests come in as the query executes. 
This is due to the addition of the ``trace? 'Results Found: '`` callback.

Also notice, only one request is executed at a time. Each request must wait for the full response and processing of the previous. 
This is a current limitation of BaseX since by design, runs queries in a single thread. There are several workarounds such as splitting up the work via a
 master query, or using XQuery expression as a string to spawn another process. Although effective, all these workarounds require extra effort 
and multiple components. Additionally they leave the language domain..

Luckily, with the introduction this module ``xq-promise``, this is no longer the case. Lets change the example above to use the newly introduced ``fork-join`` method to speed up this process by splitting the request work into multiple threads before returning to the parent querie's thread.

Luckily the example above already uses ``defer`` and ``promises`` so the change is only one line. Replace:
```xquery
$promises ! .(())
```
which manually executes each promise on the main thread, with:
```xquery
promise:fork-join($promises)
```

If you watch this execute in BaseX you will quickly see its executing much faster, with multiple requests being processed at once. 

On my machine, the first example without ``fork-join`` took roughly on average, 55 seconds. With ``fork-join`` this time dropped to 6 seconds!
That is a clear advantage! Playing around with ``compute size`` and ``max forks`` I have been able to get this even lower to around 3 seconds on average!

#### Interacting with shared resources
With any async process comes the possibility of synchronization problems. Fortunately, Basex from my observation during this work, appears to be rather thread safe and the promise pattern 
helps ensure your queries are too. There are a few things to note however when using ``fork-join``

###### Never attempt to write to a database within a fork
Luckily this does ``not`` restrict you from writing to databases, it just means: compute in forks, write after you have rejoined.
Fortunately you can be sure anything returned from the ``fork-join`` operation was returned on the main thread and thus is safe! 

For example:
```xquery
(: lots of computations chaining :)
let $result := promise:fork-join($promises)
return
  for $result in $results
  return db:add($db, $path, $result)
```

###### Do not open disc resources (databases, files) from multiple forks. 

Now this may seem like a major limitation, but its not. You can still interact and even open these resources in callbacks, 
and thus parallelized forks, however be cautious to try and open a resource only once and hopefully in a single piece of work.

```xquery
let $compute := function ($doc) {
   for sliding window $w in string-to-codepoints($doc)
   start at $spos when true()
   end at $epos when $epos - $spos = 25
   return $w
}
let $promises := db:list() ! promise:defer(db:open(?), ., map {
     'then': $compute
}
return
  promise:fork-join($promises)
```

Its important to note that all callbacks will be executed in the fork they originated from. So in this case, opening each database and computing the windows will occur in each fork.
If you attached an additional callback after $computer, it too would execute in its origin fork, and not the master thread. Amazing!

In regards to database access, or any resources for that matter. Notice how I ensure to only open one database per fork. 
Although this is not a strict limitation its a recommendation.

As an alternative, queue up the large resource prior to the ``forki-join`` and use it in the callbacks:
```xquery
let $largeResource := doc('...')
let $compute :=  function ($res) {
  $res?*[. = $largeResource//name]
}
let $promises := ...
return
  promise:fork-join($promises)
```

##### Other words of caution!
* Not everything should be parallelized.

For example, disc writes and other operations should be handled with care when using ``fork-join``

#### Advanced Forking
Certain scenarios can be optimized by changing the:
* compute size - Number of deferred jobs to process per thread
* max forks - Max number of forked threads to allow at once.

For example:
```xquery
promise:fork-join($promises, 1)
```

The above query sets the compute size to 1. 
* The default ``compute size`` is 2. 

Depending on the level of effort in performing an individual task, this option can
be highly beneficial. For example, when computing millions of small computations, it may be worthwhile to set this to some high number like ``1000``. 
In contrary, when doing very computationally expensive tasks, it may be worth while to leave this alone, or set it to 1.

The following query sets the ``compute size`` to 1 and the ``max forks`` to 20:
```xquery
promise:fork-join($promises, 1, 20)
```

For some operations, such as http requests, this can decrease script execution time.
* By default max forks is equal to the number of processor cores.

Here is the complete signature:
```xquery
promse:fork-join($promises as function(*,map(*)), $compute-size as xs:integer?, $max-forks as xs:integer) as item()*
```

##### Fork in Fork?
Why not?! You may wonder if you can fork in a forked callback. The answer is YES! Generally this would not be advised however in certain 
scenarios this is beneficial. Since all fork-joins share the same pool, inner forks merely ensure every thread is
used to its maximum. However with anything, too many can be detrimental and is dependent on the type of work being performed.  

 Here is an example:

```xquery
let $request := http:send-request($req, ?)
let $request-all-links := function ($res) {
  let $promises := $res//a/@href ! promise:defer($request, .)
  return
    promise:fork-join($promises)
}
let $work := 
  for $uri in $uris
  return promise:defer($request, $uri, map { 'then': $request-all-links })
return
  promise:fork-join($work)
```

In this case, since the inner ``fork-join`` simply makes lots of external requests, this actually can improves execution time.

### Limitations
With any async process their are limitations. So far these are the only noticed limitations:
* Updating database nodes in a callback

### Implementation Details

This library is implemented for [BaseX][1] via the [QueryModule][4] class. It leverages Jave 7's [ForkJoinPool][2] pool and [RecursiveTasks][3] classes and patterns. There are three ``java`` files as part of the implementation:
* XqPromise.java
* XqDeferred.java
* XqForkJoin.java

#### XqPromise
The XqPromise class implements [QueryModule][4] from the BaseX implementation and exposes the methods described earlier:
* defer
* when
* fork-join

#### XqDeferred
This class is at the core of the [promise][1] pattern and represents a unit of work to perform in the future. It implements in the ``FItem`` class from the [BaseX][1] implementation and thus is a function. 

If passed a empty sequence, it executes its work.

If provided a map of callback functions, the callbacks are added but no execution is performed.

#### XqForkJoinTask
Implements [RevursiveTask][3] and performs the forking processing leveraging a fixed [ForkJoinPool][2]

Currently the pool uses the number of CPUs to determine max thread count. See the Advanced section for overriding this.

### Shout Out!
If you like what you see here please star the repo and follow me on [github][7] or [linkedIn][6]

Happy forking!!

[0]: 'https://www.promisejs.org/patterns/'
[1]: 'http://www.basex.org'
[2]: 'https://docs.oracle.com/javase/tutorial/essential/concurrency/forkjoin.html'
[3]: 'https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/RecursiveTask.html'
[4]: 'http://docs.basex.org/javadoc/org/basex/query/QueryModule.html'
[5]: 'https://github.com/james-jw/xqpm'
[6]: 'https://www.linkedin.com/pub/james-wright/61/25a/101'
[7]: 'https://github.com/james-jw'
