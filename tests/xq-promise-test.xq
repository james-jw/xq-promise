(:
 : @author James Wright
 : Tests for xq-promise library
 :)
module namespace test = 'http://basex.org/modules/xqunit-tests';
import module namespace promise = 'org.jw.basex.async.xq-promise'; 

declare %unit:test function test:promise() {
   let $work := function ($name) {
     'Hello ' || $name || '!' 
   }
   let $promise := promise:defer($work, 'world')
   return
      unit:assert-equals($promise(()), 'Hello world!')
 };

declare %unit:test function test:callbacks-add() {
  for $type in ('then', 'done', 'always')
  return
    let $path := file:temp-dir() || $type || '.txt'
    let $callback := file:write-text($path, ?)
    let $work := promise:defer(trace(?, 'Hello world!'), $type)
    let $promise := $work(map { $type: $callback })
    return
      ($promise(()), file:exists($path), file:delete($path))
};

declare %unit:test function test:callbacks-fail() {
  let $path := file:temp-dir() || 'fail.txt'
  let $callback := function () { file:write-text($path, 'Failed!') }
  let $work := promise:defer(function () { fn:error('Failed!') })
  let $promise := $work(map { 'fail': $callback })
  return
    ($promise(()), unit:assert-equals(file:exists($path),true()), file:delete($path))
};

declare %unit:test function test:callbacks-fail-fix-value() {
  let $callback := function () { 'Hello world!' }
  let $work := promise:defer(function () { fn:error('Failed!') })
  let $promise := $work(map { 'fail': $callback })
  return
    unit:assert-equals($promise(()), 'Hello world!')
};

declare %unit:test function test:callbacks-fail-fatal() {
  let $callback := function () { fn:error('Callback Failed!') }
  let $work := promise:defer(function () { fn:error('Failed!') })
  let $promise := $work(map { 'fail': $callback })
  let $result := 
    try { 
      unit:assert-equals($promise(()), 'Hello world!')
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
    unit:assert-equals($promise(()), 'Hello world!')
};

declare %unit:test function test:callback-always-on-fail() {
  let $type := 'always'
  let $path := file:temp-dir() || $type || '.txt'
  let $callback := file:write-text($path, ?)
  let $failedWork := function () { fn:error("Failed!") }
  let $work := promise:defer($failedWork, $type)
  let $promise := $work(map { $type: $callback })
  return
    ($promise(()), unit:assert-equals(file:exists($path), true()), file:delete($path))
};

declare %unit:test function test:multiple-callbacks() {
  let $greet := function ($name) { 'Hello ' || $name }
  let $get-end := function ($name) { $name || '!' }
  let $promise := 
      promise:defer(trace(?), 'world', map {
          'then': ($get-end, $greet)
      })
  return
    unit:assert-equals($promise(()), 'Hello world!')
};

declare %unit:test function test:callbacks-attached-after() {
  let $greet := function ($name) { 'Hello ' || $name }
  let $get-end := function ($name) { $name || '!' }
  let $work := promise:defer(trace(?), 'world')
  let $get := $work(map { 'then': $get-end })
  let $promise := $get(map { 'then': $greet })
  return
    unit:assert-equals($promise(()), 'Hello world!')
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
   unit:assert-equals($promise(()), 'Hello world!')
};

declare %unit:test function test:when-with-diff-arity() {
  let $greet := function ($args) { $args?1 || ' ' || $args?2 || '!'}
  let $promise := 
    promise:when(
      (
        promise:defer(trace(?), 'Hello'),
        promise:defer(trace(?), 'world')
      ),
      map { 'then': $greet }
    )
  return
   unit:assert-equals($promise(()), 'Hello world!')
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
