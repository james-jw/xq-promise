module namespace test = 'http://basex.org/modules/xqunit-tests';
import module namespace p = 'https://github.com/james-jw/xq-promise';

declare %unit:test function test:promise() {
   let $work := function ($name) {
     'Hello ' || $name || '!' 
   }
   let $promise := p:defer($work, 'world')
   return
      unit:assert-equals($promise(), 'Hello world!')
 };


declare %unit:test function test:callbacks-fail() {
  let $path := file:temp-dir() || 'fail.txt'
  let $callback := function () { file:write-text($path, 'Failed!') }
  let $work := function () { fn:error('Failed!') }
  let $promise := p:fail($work,  $callback )
  return
    ($promise(), unit:assert(file:exists($path)), file:delete($path))
};

declare %unit:test function test:callbacks-fail-mitigate() {
  let $add-enthusiasm := function ($phrase) { $phrase || '!' }
  let $quote := function($phrase) { "'" || $phrase || "'" }
  let $fail := function () { fn:error('Failed!') }
  let $promise := p:defer($add-enthusiasm, 'Hello World')
      => p:then($fail)
      => p:fail(function () { () })
      => p:then($quote)
  return
    (unit:assert($promise(), '"Hello World!"'))
};

declare %unit:test function test:callbacks-fail-fix-value() {
  let $callback := function () { 'Hello world!' }
  let $work := function () { fn:error(xs:QName('p:error'), 'Failed!') }
  let $promise := p:fail($work, $callback)
  return
    unit:assert-equals($promise(), 'Hello world!')
};

declare %unit:test function test:callbacks-fail-fatal() {
  let $callback := function () { fn:error('Callback Failed!') }
  let $work := function () { fn:error('Failed!') }
  let $promise := p:fail($work, $callback)
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
      p:defer(trace(?), 'world')
        => p:then($greet)
  return
    unit:assert-equals($promise(), 'Hello world!')
};

declare %unit:test function test:callback-always-on-fail() {
  let $type := 'always'
  let $path := file:temp-dir() || $type || '.txt'
  let $callback := function ($err) { file:write-text($path, $err?description) }
  let $failedWork := function () { fn:error(xs:QName('p:error'), "Failed!") }
  let $work := p:defer($failedWork, $type)
  let $promise := p:always($work, $callback )
  return
    (try { $promise() } catch * {()}, unit:assert-equals(file:exists($path), true()), file:delete($path))
};

declare %unit:test function test:multiple-callbacks() {
  let $greet := function ($name) { 'Hello ' || $name }
  let $get-end := function ($name) { $name || '!' }
  let $promise := 
      p:defer(trace(?), 'world')
        => p:then($get-end)
        => p:then($greet)
  return
    unit:assert-equals($promise(), 'Hello world!')
};

declare %unit:test function test:callbacks-attached-after() {
  let $greet := function ($name) { 'Hello ' || $name }
  let $get-end := function ($name) { $name || '!' }
  let $promise := p:defer(trace(?), 'world')
        => p:then($get-end)
        => p:then($greet)
  return
    unit:assert-equals($promise(), 'Hello world!')
};

declare %unit:test function test:when-with-same-arity() {
  let $greet := function ($greeting, $name) { trace($greeting || ' ' || $name || '!', 'Arity test: ')}
  let $promise := 
    p:when((
      p:defer(trace(?), 'Hello'),
      p:defer(trace(?), 'world')))
      => p:then($greet)
  return
   unit:assert-equals($promise(), 'Hello world!')
};

declare %unit:test function test:when-with-diff-arity() {
  let $greet := function ($args) { $args?1 || ' ' || $args?2 || '!'}
  let $promise := 
    p:when(
      (
        p:defer(trace(?), 'Hello'),
        p:defer(trace(?), 'world')))
          => p:then($greet)
  return
   unit:assert-equals($promise(), 'Hello world!')
};

declare %unit:test function test:fork-join() {
  let $work := function ($input) { element text {$input} }
  let $promises := 
    for $i in (1 to 1000) return 
      p:defer($work, $i)
  return
    let $out := p:fork-join($promises)
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
                   return p:defer($work, $i)
  return
    let $out := p:fork-join($promises, 100)
    let $sum := sum((($out ! p:defer(parse-json(?), .)) 
       => p:fork-join(100))?value)  
    return
      unit:assert-equals($sum, 501501)
};

declare %unit:test function test:fork-join-regular-functions() {
  let $work := for $i in (1 to 10) 
              return
                function() { trace(<value>{$i}</value>) }
  return 
    let $result := p:fork-join($work)
    return
      unit:assert-equals(sum($result), 55)
};


declare %unit:test function test:multiple-arity-callback() {
  let $worker := function($fname, $lname) { 'Hello, ' || $fname || ' ' || $lname }
  let $promise := p:defer($worker, ('every', 'one'))
     => p:then(function($p) { 'then: ' || $p })
     => p:fail(function ($result as item()*) {
              trace($result, 'Request failed!') => prof:void()
        })
  return
   unit:assert-equals($promise(), 'then: Hello, every one')
};

declare %unit:test function test:then-helper() {
  let $greet := function ($name) { 'Hello ' || $name || '!'}
  let $promise := 
      p:defer(trace(?), 'world') 
        => p:then($greet)
  return
    unit:assert-equals($promise(), 'Hello world!')
};

declare %unit:test function test:done-helper() {
  let $type := 'done'
  let $path := file:temp-dir() || $type || '.txt'
  let $callback := file:write-text($path, ?)
  let $promise := 
      p:defer(trace(?), 'world') 
        => p:done($callback)
  return
   ($promise(), unit:assert(file:exists($path)), file:delete($path))
};

declare %unit:test function test:always-helper() {
  let $type := 'always'
  let $path := file:temp-dir() || $type || '.txt'
  let $callback := file:write-text($path, ?)
  let $promise := 
      p:defer(trace(?), 'world') 
        => p:always($callback)
  return
   ($promise(), unit:assert(file:exists($path)), file:delete($path))
};

declare %unit:test function test:fail-helper() {
  let $type := 'fail'
  let $path := file:temp-dir() || $type || '.txt'
  let $callback := function ($err) { file:write-text($path, $err?description) }
  let $promise := 
      p:defer(function () { error(xs:QName('p:error')) }, 'world') 
        => p:fail($callback)
  return
   ($promise(), unit:assert(file:exists($path)), file:delete($path))
};

declare %unit:test function test:helper-chain() {
  let $type := 'done'
  let $path := file:temp-dir() || $type || '.txt'
  let $callback := file:write-text($path, ?)
  let $greet := function ($name) { 'Hello ' || $name || '!'}
  let $promise := 
      p:defer(trace(?), 'world') 
        => p:then($greet)
        => p:done($callback)
  return
    (
      unit:assert-equals($promise(), 'Hello world!'),
      ($promise(), unit:assert-equals(file:read-text($path), 'Hello world!'), file:delete($path))
    )
};

declare %unit:test function test:array-arguments-not-flattened() {
  let $arguments := array { "a", "b", "c" }
  let $promise := p:defer(fn:concat#3, $arguments)
  return  
    unit:assert-equals("abc", $promise())
};

declare %unit:test function test:array-as-arguments-invalid-count() {
  let $arguments := array { "a", "b", "c" }
  return try {
      p:defer(function ($x, $y) { $x || $y }, $arguments)(),
      error(xs:QName('test:error'), 'Should have thrown exception')
    } catch * {
      if(trace($err:description) => matches('Arity and number')) then ()
      else error(xs:QName('test:error'), 'Should have throw invalid arguments exception')
    }
};

declare %unit:test function test:fork-eval() {
   let $query := '1 to 100 ! (.)'
   let $promise := p:defer(xquery:eval(?), $query)
   return 
     (unit:assert-equals(count(p:fork-join($promise)),100 ))
};

declare updating function test:updating($item) {
  db:create('test-updating-function')
};

declare function test:transform-test($item, $i) {
  copy $out := $item
  modify (
    rename node $out as 'test-renamed',
    insert node element value {$i} into $out
  ) return $out
};

declare function test:insert-attribute($item, $i) {
  copy $out := $item
  modify insert node attribute id {$i + 1} into $out
  return $out
};

declare %unit:test function test:fork-transformation() {
  let $node := <test-element></test-element>
  let $promises := 
    for $i in (1 to 1000) return
    p:defer(test:transform-test(?, $i), $node)
     => p:then(test:insert-attribute(?, $i))
  let $results := p:fork-join($promises) 
  return (
    unit:assert-equals(sum($results), 500500),
    unit:assert-equals(sum($results/@id), 501500)
  )
};

declare %unit:test function test:when-with-non-functions() {
   let $simple := <a href="test" /> 
   let $greet := function ($name) { 'Hello ' || $name }
   let $greeting := p:defer($greet, 'world')
   let $promise := p:when(($simple, $greeting))
      => p:then(function ($node, $text) {
          ($simple, $text)
      })
   let $result := $promise()
   return (
     unit:assert-equals($result[1]/@href/data(), 'test'),
     unit:assert-equals($result[2], 'Hello world')
   )
};

declare %unit:test function test:then-reject() {
  let $type := 'handler'
  let $path := file:temp-dir() || $type || '.txt'
  let $failed := function ($err) { file:write-text($path, $err?description) }
  let $promise := 
      p:defer(function () { fn:error() }, 'world')
        => p:then((), $failed)
  return
   ($promise(), unit:assert(file:exists($path)), file:delete($path))
};

declare %unit:test function test:then-reject-to-second() {
  let $type := 'handler'
  let $path := file:temp-dir() || $type || '.txt'
  let $failed := function ($err) { file:write-text($path, $err?description) }
  let $promise := (function () { '' })
        => p:then(fn:error#0)
        => p:fail(function ($e) { $e?code })
  return
   unit:assert-equals($promise(), xs:QName('err:FOER0000'))
};

declare %unit:test function test:doc-uri() {
  (: doc(<x>x.xml</x>) looks stupid, but it prevents opening of x.xml at : compile time :)
  (file:write('x.xml', <x>test</x>),
  let $func := function($i) { doc('x.xml') }
  let $nodes := p:fork-join ( (1 to 10000) ! p:defer($func, . ))
  (: expected to yield true :)
  return unit:assert(every $n in $nodes satisfies $n is $nodes[1])
 )
};
