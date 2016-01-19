module namespace p = 'https://github.com/james-jw/xq-promise';
import module namespace promise = 'org.jw.basex.async.xq-promise'; 

(:~ 
 : Maps an error array from the xq-promise.jar Java code
 : into an XQuery map for easier error handling
 :)
declare %private function p:map-error($err) as map(*) {
  map {
    'code': $err[1],
    'description': $err[2],
    'module': $err[3],
    'line': $err[4],
    'column': $err[5],
    'value': $err[8],
    'additional': map {
      'deferred': if(count($err[6]) = 1) then $err[6]?1 else $err[6] ,
      'arguments': $err[7]
    }
  }
};

(: Provide the map-error function to the Java QueryModule :)
declare variable $p:init := promise:init(p:map-error(?));

(:~ 
 : Defers a piece of work for later execution
 :) 
declare function p:defer($work as function(*)) as function(*) {
  (promise:init(p:map-error(?)), promise:defer($work))
};

(:~ 
 : Defers a piece of work, with the specified arguments for later execution
 :) 
declare function p:defer($work as function(*), $args as item()*) as function(*) {
  (promise:init(p:map-error(?)), promise:defer($work, $args))
};

(:~ 
 : Defers a piece of work, with the specified arguments and callbacks for later execution
 :) 
declare function p:defer($work as function(*), $args as item()*, $callbacks as map(*)) as function (*) {
  (promise:init(p:map-error(?)), promise:defer($work, $args, $callbacks))
};

(:~ 
 : Adds the callbacks provided to the then chain of the provided deferreds
 :)
declare function p:then($deferred as function(*), $callbacks as function(*)*) as function(*) {
  promise:then($deferred, $callbacks)
};

(:~ 
 : Adds the callbacks provided to the done chain of the provided deferreds
 :)
declare function p:done($deferred as function(*), $callbacks as function(*)*) as function (*) {
  promise:done($deferred, $callbacks)
};

(:~ 
 : Adds the callbacks provided to the always chain of the provided deferreds
 :)
declare function p:always($deferred as function(*), $callbacks as function(*)*) as function (*) {
  promise:always($deferred, $callbacks)
};

(:~ 
 : Adds the callbacks provided to the failure chain of the provided deferreds
 :)
declare function p:fail($deferred as function(*), $callbacks as function(*)*) as function(*) {
  promise:fail($deferred, $callbacks)
};

(:~ 
 : Combines a set of promises into a single promise
 :)
declare function p:when($deferreds as function(*)*) as function (*) {
  promise:when($deferreds)
};

(:~ 
 : Adds the callbacks provided to the appropriate chains on the provided deferreds
 :)
declare function p:attach($deferred as function(*), $callbacks as map(*)) as function (*) {
  promise:attach($deferred, $callbacks)
};

(:~ 
 : Forks the provided functions or deferred work in a new thread. Returns a sealed promise
 : which can no long accept callbacks.
 :)
declare function p:fork($work as function(*)) as function(*) {
  promise:fork($work)
};

(:~ 
 : Forks the provided functions or deferred work in a fork join fashion, returning the results once all
 : forked computation is complete.
 :)
declare function p:fork-join($work as function(*)*) as item()* {
  promise:fork-join($work)
};

