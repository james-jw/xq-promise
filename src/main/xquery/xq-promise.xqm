module namespace p = 'https://github.com/james-jw/xq-promise';
import module namespace promise = 'org.jw.basex.async.xq-promise'; 

(:~ 
 : Defers a piece of work, with the specified arguments and callbacks for later execution
 :) 
declare function p:defer($work as function(*), $args as item()*) {
  function () {
    fn:apply($work, if($args instance of array(*)) then $args else array { $args })
  }
};

(:~ 
 : Adds the callbacks provided to the then chain of the provided deferreds
 :)
declare function p:then($promise, $callback) {
  function () {
    let $out := p:invoke($promise, ())
    return 
      if($out instance of function(*) and 
         not($out instance of array(*) or $out instance of map(*))) 
      then p:then($out, $callback)
      else p:invoke($callback, $out) 
  }  
};

(:~ 
 : Adds the callbacks provided to the done chain of the provided deferreds
 :)
declare function p:done($promise, $callback) {
  function () {
    let $out := $promise()
    return 
       ($out, prof:void(p:invoke($callback, $out)))
  }
};

(:~ 
 : Combines a set of promises into a single promise
 :)
declare function p:when($inputs as item()*) {
  function () {
    array {
      for $input in $inputs
      return
        if($input instance of function(*))
        then $input() else $input 
    }
  }
};

(:~ 
 : Adds the callbacks provided to the always chain of the provided deferreds
 :)
declare function p:always($promise, $callback) {
  function () {
    try { 
      let $out := $promise()
      return
        ($out, prof:void(p:invoke($callback, $out)))
    } catch * {
      let $error := map {
          'code': $err:code,
          'description': $err:description,
          'module': $err:module,
          'line': $err:line-number,
          'column': $err:column-number,
          'value': $err:value,
          'additional': map {
            'deferred': $promise
          }
      }
        return
          p:invoke($callback, $error)
    } 
  }
};

declare function p:fail($promise, $handler) {
  function () {
    try {
      $promise()
    } catch * {
      let $error := map {
        'code': $err:code,
        'description': $err:description,
        'module': $err:module,
        'line': $err:line-number,
        'column': $err:column-number,
        'value': $err:value,
        'additional': map {
          'deferred': $promise
        }
     }
      return
        p:invoke($handler, $error)
    }
  }
};

declare function p:invoke($handler, $input) {
  if(fn:function-arity($handler) = 0) then $handler()
  else if (fn:function-arity($handler) > 1 and $input instance of array(*)) then
    fn:apply($handler, $input)
  else $handler($input)
};

(:~ 
 : Forks the provided functions or deferred work in a fork join fashion, returning the results once all
 : forked computation is complete.
 :)
declare function p:fork-join($work as function(*)*) as item()* {
  promise:fork-join($work)
};

(:~ 
 : Forks the provided functions or deferred work in a fork join fashion, returning the results once all
 : forked computation is complete.
 :)
declare function p:fork-join($work as function(*)*, 
                             $compute-size as xs:integer) as item()* {
  promise:fork-join($work, $compute-size)
};

(:~ 
 : Forks the provided functions or deferred work in a fork join fashion, returning the results once all
 : forked computation is complete.
 :)
declare function p:fork-join($work as function(*)*, 
                             $compute-size as xs:integer, 
                             $max-forks as xs:integer) as item()* {
  promise:fork-join($work, $compute-size, $max-forks)
};
