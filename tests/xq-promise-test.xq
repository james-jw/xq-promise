module namespace test = 'http://basex.org/modules/xqunit-tests';
import module namespace promise = 'https://github.com/james-jw/xq-promise';

declare %unit:test function test:promise() {
   let $work := function ($name) {
     'Hello ' || $name || '!' 
   }
   let $promise := promise:defer($work, 'world')
   return
      unit:assert-equals($promise(), 'Hello world!')
 };

declare %unit:test function test:callbacks-add() {
  for $type in ('then', 'done', 'always')
  return
    let $path := file:temp-dir() || $type || '.txt'
    let $callback := file:write-text($path, ?)
    let $work := promise:defer(trace(?, 'Hello world!'), $type)
    let $promise := promise:attach($work, (map { $type: $callback }))
    return
      ($promise(), unit:assert(file:exists($path)), file:delete($path))
};

declare %unit:test function test:callbacks-fail() {
  let $path := file:temp-dir() || 'fail.txt'
  let $callback := function () { file:write-text($path, 'Failed!') }
  let $work := promise:defer(function () { fn:error('Failed!') })
  let $promise := promise:attach($work, map { 'fail': $callback })
  return
    ($promise(), unit:assert(file:exists($path)), file:delete($path))
};

declare %unit:test function test:callbacks-fail-fix-value() {
  let $callback := function () { 'Hello world!' }
  let $work := promise:defer(function () { fn:error(xs:QName('promise:error'), 'Failed!') })
  let $promise := promise:attach($work, map { 'fail': $callback })
  return
    unit:assert-equals($promise(), 'Hello world!')
};

declare %unit:test function test:callbacks-fail-fatal() {
  let $callback := function () { fn:error('Callback Failed!') }
  let $work := promise:defer(function () { fn:error('Failed!') })
  let $promise := promise:attach($work, map { 'fail': $callback })
  let $result := 
    try { 
      unit:assert-equals($promise(), 'Hello world!')
    } catch * { ('Failed!') }
  return
   unit:assert-equals($result, 'Failed!')
};

declare %unit:test function test:callback-then() {
  let $greet := function ($name) { 'Hello ' || $name || '!'}
  let $promise := 
      promise:defer(trace(?), 'world', map {
          'then': $greet
      })
  return
    unit:assert-equals($promise(), 'Hello world!')
};

declare %unit:test function test:callback-always-on-fail() {
  let $type := 'always'
  let $path := file:temp-dir() || $type || '.txt'
  let $callback := function ($err) { file:write-text($path, $err?description) }
  let $failedWork := function () { fn:error(xs:QName('promise:error'), "Failed!") }
  let $work := promise:defer($failedWork, $type)
  let $promise := promise:attach($work, map { $type: $callback })
  return
    (try { $promise() } catch * {()}, unit:assert-equals(file:exists($path), true()), file:delete($path))
};

declare %unit:test function test:multiple-callbacks() {
  let $greet := function ($name) { 'Hello ' || $name }
  let $get-end := function ($name) { $name || '!' }
  let $promise := 
      promise:defer(trace(?), 'world', map {
          'then': ($get-end, $greet)
      })
  return
    unit:assert-equals($promise(), 'Hello world!')
};

declare %unit:test function test:callbacks-attached-after() {
  let $greet := function ($name) { 'Hello ' || $name }
  let $get-end := function ($name) { $name || '!' }
  let $promise := promise:defer(trace(?), 'world')
        => promise:then($get-end)
        => promise:then($greet)
  return
    unit:assert-equals($promise(), 'Hello world!')
};

declare %unit:test function test:when-with-same-arity() {
  let $greet := function ($greeting, $name) { trace($greeting || ' ' || $name || '!', 'Arity test: ')}
  let $promise := 
    promise:when((
      promise:defer(trace(?), 'Hello'),
      promise:defer(trace(?), 'world')),
      map { 'then': $greet }
  )
  return
   unit:assert-equals($promise(), 'Hello world!')
};

declare %unit:test function test:when-with-diff-arity() {
  let $greet := function ($args) { $args?1 || ' ' || $args?2 || '!'}
  let $promise := 
    promise:when(
      (
        promise:defer(trace(?), 'Hello'),
        promise:defer(trace(?), 'world')),
        map { 'then': $greet }
    )
  return
   unit:assert-equals($promise(), 'Hello world!')
};

declare %unit:test function test:fork-join() {
  let $work := function ($input) { element text {$input} }
  let $promises := 
    for $i in (1 to 1000) return 
      promise:defer($work, $i)
  return
    let $out := promise:fork-join($promises)
    let $sum := sum($out)
    return
      unit:assert-equals($sum, 500500)
};

declare %unit:test function test:fork-join-shared-resource() {
  let $shared := array { for $i in (1 to 1001) return map { 'value': $i } }
  let $work := function ($input) { 
    let $value := $shared?*[?value = $input]
    return 
      element text {json:serialize($value)} 
  }
  let $promises := for $i in (1 to 1001) 
                   return promise:defer($work, $i)
  return
    let $out := promise:fork-join($promises, 100)
    let $sum := sum((($out ! promise:defer(parse-json(?), .)) 
       => promise:fork-join(100))?value)  
    return
      unit:assert-equals($sum, 501501)
};

declare %unit:test function test:is-promise() {
   let $promise := promise:defer(trace(?), '')
   return
   (
      unit:assert-equals(promise:is-promise($promise), true()),
      unit:assert-equals(promise:is-promise(trace(?)), false())
   )
};

declare %unit:test function test:fork-join-regular-functions() {
  let $work := for $i in (1 to 10) 
              return
                function() { trace(<value>{$i}</value>) }
  return 
    let $result := promise:fork-join($work)
    return
      unit:assert-equals(sum($result), 55)
};


declare %unit:test function test:multiple-arity-callback() {
  let $worker := function($fname, $lname) { 'Hello, ' || $fname || ' ' || $lname }
  let $promise := promise:defer($worker, ('every', 'one'), map {
    'then': function($p) { 'then: ' || $p },
    'fail': function ($result as item()*) {
              trace($result, 'Request failed!') => prof:void()
            }
  })
  return
   unit:assert-equals($promise(), 'then: Hello, every one')
};

declare %unit:test function test:fork() {
  let $worker := function($fname, $lname) { 'Hello, ' || $fname || ' ' || $lname }
  let $future := promise:fork($worker, ('every', 'one'))
  return
     unit:assert-equals($future(), 'Hello, every one')
};

declare %unit:test function test:fork-with-callback() {
  let $worker := function($fname) { 'Hello, ' || $fname }
  let $future := 
       promise:fork(
         promise:defer($worker, 'every') 
           => promise:then(function($str) { $str || ' one' })
       )
  return
     unit:assert-equals($future(), 'Hello, every one')
};

declare %unit:test function test:fork-when() {
  let $worker := function($fname, $lname) { 'Hello, ' || $fname || ' ' || $lname }
  let $worker2 := function($fname, $lname) { 'and ' || $fname || ' ' || $lname || '!' }
  let $combine := function ($start, $end) { $start || ' ' || $end }
  let $future := promise:fork($worker, ('every', 'one'))
  let $future2 := promise:fork($worker2, ('all', 'my peeps'))
  return
    unit:assert-equals(
     (promise:when(($future, $future2)) => promise:then($combine))(),
      'Hello, every one and all my peeps!'
    )
};

declare %unit:test function test:then-helper() {
  let $greet := function ($name) { 'Hello ' || $name || '!'}
  let $promise := 
      promise:defer(trace(?), 'world') 
        => promise:then($greet)
  return
    unit:assert-equals($promise(), 'Hello world!')
};

declare %unit:test function test:done-helper() {
  let $type := 'done'
  let $path := file:temp-dir() || $type || '.txt'
  let $callback := file:write-text($path, ?)
  let $promise := 
      promise:defer(trace(?), 'world') 
        => promise:done($callback)
  return
   ($promise(), unit:assert(file:exists($path)), file:delete($path))
};

declare %unit:test function test:always-helper() {
  let $type := 'always'
  let $path := file:temp-dir() || $type || '.txt'
  let $callback := file:write-text($path, ?)
  let $promise := 
      promise:defer(trace(?), 'world') 
        => promise:always($callback)
  return
   ($promise(), unit:assert(file:exists($path)), file:delete($path))
};

declare %unit:test function test:fail-helper() {
  let $type := 'fail'
  let $path := file:temp-dir() || $type || '.txt'
  let $callback := function ($err) { file:write-text($path, $err?description) }
  let $promise := 
      promise:defer(function () { error(xs:QName('promise:error')) }, 'world') 
        => promise:fail($callback)
  return
   ($promise(), unit:assert(file:exists($path)), file:delete($path))
};

declare %unit:test function test:helper-chain() {
  let $type := 'done'
  let $path := file:temp-dir() || $type || '.txt'
  let $callback := file:write-text($path, ?)
  let $greet := function ($name) { 'Hello ' || $name || '!'}
  let $promise := 
      promise:defer(trace(?), 'world') 
        => promise:then($greet)
        => promise:done($callback)
  return
    (
      unit:assert-equals($promise(), 'Hello world!'),
      ($promise(), unit:assert-equals(file:read-text($path), 'Hello world!'), file:delete($path))
    )
};

declare %unit:test function test:fork-eval() {
   let $query := '1 to 100 ! (.)'
   let $promise := promise:fork(xquery:eval(?), $query)
   return 
     (unit:assert-equals(count($promise()),100 ))
};

declare %unit:test function test:ad-callback-to-busy-deferred() {
  try {
   let $query := '1 to 100 ! (.)'
   let $promise := promise:fork(xquery:eval(?), $query)
           => promise:then(trace(?, 'The end!'))
   return 
         (unit:assert-equals(count($promise()),100 ))
  } catch * {
    if($err:description => matches('busy')) then ()
    else error(xs:QName('test:error'), ('Should have thrown busy deferred error'))
  }
};

declare updating function test:updating($item) {
  db:create('test-updating-function')
};

declare %unit:test function test:updating-deferred-not-allowed() {
  try { (
    promise:defer(test:updating(?)),
    fn:error(xs:QName('test:error'), 'Should have thrown updating deferred error') 
  )} catch * {
    if($err:description => matches('Updating expressions are not allowed')) then ()
    else fn:error(xs:QName('test:error'), 'Should have thrown updating deferred error') 
  } 
};

declare %unit:test function test:updating-fork-not-allowed() {
  try { (
    promise:fork(test:updating(?)),
    fn:error(xs:QName('test:error'), 'Should have thrown updating deferred error') 
  )} catch * {
    if($err:description => matches('Updating expressions are not allowed')) then ()
    else fn:error(xs:QName('test:error'), 'Should have thrown updating deferred error') 
  } 
};

declare %unit:test function test:updating-callback-not-allowed() {
  try { (
     promise:defer(trace(?))
       => promise:done(test:updating(?)),
    fn:error(xs:QName('test:error'), 'Should have thrown updating deferred error') 
  )} catch * {
    if($err:description => matches('Updating expressions are not allowed')) then ()
    else fn:error(xs:QName('test:error'), 'Should have thrown updating deferred error') 
  } 
};

declare function test:transform-test($item, $i) {
  <element>{$i}</element>
};

declare %unit:test function test:fork-transformation() {
  let $node := <test-element></test-element>
  let $promises := 
    for $i in (1 to 100) return
    promise:defer(test:transform-test(?, $i), $node)
  return
    unit:assert-equals(sum(promise:fork-join($promises)), 5050)
};

(:

import module namespace p = 'https://github.com/james-jw/xq-promise';

declare function local:fail($err) {
  if($err?description != 'Failed, but its okay...') 
  then 1000
  else error(xs:QName('p:failure'), 'Could not cope.')
};

declare function local:is-copable($err) {
  if($err?description != 'Could not cope.')
  then error(xs:QName('p:failure'), 'Fatal Error!')
  else 2222
};

declare function local:work($input) {
  if($input = (1, 5, 12))
  then error(xs:QName('p:failure'), 'Failed, but its okay...')
  else $input
};

(for $in in (1 to 100)
return
 p:defer(local:work(?), $in) 
   => p:fail(local:fail(?))
   => p:fail(local:is-copable(?))
 )! .()

:)