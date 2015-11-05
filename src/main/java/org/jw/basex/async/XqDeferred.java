package org.jw.basex.async;

import static org.basex.query.QueryError.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.basex.query.*;
import org.basex.query.expr.*;
import org.basex.query.util.list.*;
import org.basex.query.value.*;
import org.basex.query.value.array.Array;
import org.basex.query.value.item.*;
import org.basex.query.value.map.Map;
import org.basex.query.value.node.*;
import org.basex.query.value.seq.Empty;
import org.basex.query.value.type.*;
import org.basex.query.var.*;
import org.basex.util.*;

/**
 * @author James Wright
 * Represents a deferred object for use in the Promise pattern and async execution
 */
public class XqDeferred extends FItem implements XQFunction {

  /**
   * Work to perform
   */
  private FItem[] _work;
  
  private java.util.Map<String, List<FItem>> callbacks;

  // Work already executed
  List<Future<Value>> _futures;
  /**
   * Arguments to pass to _work function.
   */
  private Value _arguments;
  /**
   * Map of callback functions. Valid key values are:
   * done
   * then
   * always
   * fail
   */

  /**
   * @param work - Function item representing work to perform
   * @param args - Arguments to send to the function item
   * @param callbacks - Map containing callback functions
   */
  public XqDeferred(final FItem work, final Value args, final Map callbacksIn) throws QueryException {
    super(SeqType.ANY_FUN, new AnnList());
    _work = new FItem[] { work };
    _arguments = args;
    addCallbacks(callbacksIn);
  }

  public XqDeferred(final Value deferreds, final Map callbacksIn) throws QueryException {
    super(SeqType.ANY_FUN, new AnnList());
    addCallbacks(callbacksIn);
    _work = new FItem[(int) deferreds.size()];
    int i = 0;
    for(Item item : deferreds) {
      _work[i++] = (FItem) item;
    }
  }

  public XqDeferred(final FItem[] deferreds) {
    super(SeqType.ANY_FUN, new AnnList());
    _work = deferreds;
  }

  public XqDeferred(final List<Future<Value>> futures) {
    super(SeqType.ANY_FUN, new AnnList());
    _futures = futures;
  }

  public Value invValue(QueryContext qc, InputInfo ii, Value... args) throws QueryException {
     return processInvocation(qc, ii, _arguments);
  }

  public void addCallbacks(Map callbacksIn) throws QueryException {
    if(callbacksIn.contains(XqPromise.then, null)) {
      addCallbacks("then", callbacksIn.get(XqPromise.then, null)); 
    } else if(callbacksIn.contains(XqPromise.always, null)) {
      addCallbacks("always", callbacksIn.get(XqPromise.always, null)); 
    } else if(callbacksIn.contains(XqPromise.done, null)) {
      addCallbacks("done", callbacksIn.get(XqPromise.done, null)); 
    } else if(callbacksIn.contains(XqPromise.fail, null)) {
      addCallbacks("fail", callbacksIn.get(XqPromise.fail, null)); 
    }
  }
  
  public void addCallbacks(String name, Value... callbacksIn) throws QueryException {
    if(callbacksIn == null) { return; }
    if(name.matches("^(done|fail|always|then)$") == false) {
      throw new QueryException("Invalid callback name provided: " + name); 
    }

    if(callbacks == null) {
      callbacks = new HashMap<String, List<FItem>>();
    }

    List<FItem> existing = callbacks.get(name);
    if(existing == null) {
      existing = new ArrayList<FItem>();
    }

    for(Value callback : callbacksIn) {
      if(callback instanceof FItem) {
        existing.add((FItem)callback);
      } else if(callback.size() > 0) {
        addCallbacks(name, valueToArray(callback));
        return;
      } else {
        throw new QueryException("Only function items accepted as callbacks. " + callback.getClass().getSimpleName());
      }
    }
    
    callbacks.put(name, existing);
  }

  private Value processFutures() throws QueryException {
    ValueBuilder vb = new ValueBuilder();
    for(Future<Value> future : _futures) {
      try {
        vb.add(future.get());
      } catch (InterruptedException | ExecutionException e) {
        throw new QueryException(e);
      }
    }
    return vb.value();
  }

  /**
   * @param type - Type of callback to return (then, done, always, fail)
   * @param ii - InputInfo
   * @return - A set of callbacks in execution order
   * @throws QueryException
   */
  private List<FItem> getCallbacks(Str type, InputInfo ii) throws QueryException {
     return callbacks != null ? callbacks.get(type.toJava()) : null; 
  }

  /**
   * @param qc - Query Context
   * @param ii - Input Info
   * @param args - Arguments to invoke the worker with.
   * @return - Values returned from the work or callbacks
   * @throws QueryException
   */
  private Value processInvocation(QueryContext qc, InputInfo ii, Value... args) throws QueryException {
    Value out;
    boolean failed = false;
    try {
      if(_futures != null) {
        out = processFutures();
      } else {
        out = processNormalInvocation(qc, ii, args);
      }
    } catch (QueryException e) {
      failed = true;
      String msg = e.getMessage();
      notifyCallbacks(getCallbacks(XqPromise.always, ii), qc, ii, Str.get(msg == null ? "" : msg));
      out = notifyCallbacks(getCallbacks(XqPromise.fail, ii), qc, ii, args);
    }

    if(!failed) {
      out = processThen(out, qc, ii);
      notifyCallbacks(getCallbacks(XqPromise.done, ii), qc, ii, out);
      notifyCallbacks(getCallbacks(XqPromise.always, ii), qc, ii, out);
    }

    return out;
  }
  
  private Value processNormalInvocation(QueryContext qc, InputInfo ii, Value... args) throws QueryException {
    ValueBuilder vb = new ValueBuilder();
    for(FItem work : _work) {
      vb.add(invokeFunctionItem(work, qc, ii, args));
    }
    return vb.value();
  }

  private Value[] emptyValueArray = new Value[0];
  private Value[] valueToArray(Value in) {
    List<Value> argsArray = new ArrayList<Value>();
    for(int i = 0; i < in.size(); i++) { argsArray.add(in.itemAt(i)); };
    return argsArray.toArray(emptyValueArray);
  }


  /**
   * Invokes a method, transforming the arguments to match the function item provided.
   *
   * @param funcItem - Function to invoke
   * @param qc - Query context
   * @param ii - Input info
   * @param in - Value to provide during invocation.
   * @return - Value returned from function invocation.
   * @throws QueryException
   */
  private Value invokeFunctionItem(FItem funcItem, QueryContext qc, InputInfo ii, Value... in) throws QueryException {
    Value out;

    int arity = funcItem.arity(); // Arity of the function to call
    int expected = _work == null ? _futures.size() : _work.length; // Expected work to be returned  
    int actual = in.length; // Actual number of arguments

    if(funcItem instanceof XqDeferred) {
      out = funcItem.invokeValue(qc, ii, Empty.SEQ);
    } else if(actual == arity && arity == expected)  {
      out = funcItem.invokeValue(qc, ii, in);
    } else if(arity == 0) {
      out = funcItem.invokeValue(qc, ii);
    } else if(actual != expected || arity > actual) {
      Value args = in[0];
      if(args.size() == 1) {
        out = funcItem.invokeValue(qc, ii, args);
      } else if(expected > arity) {
        out = funcItem.invokeValue(qc, ii, Array.from(valueToArray(args)));
      } else {
        out = funcItem.invokeValue(qc, ii, valueToArray(args));
      }
    } else {
      throw new QueryException("Invalid number of arguments returned. Expected " + expected + " but was " + actual);
    }

    return out;
  }


  /**
   * @param qc - Query Context
   * @param ii - Input Info
   * @param args - Arguments to execute with
   * @return - The result of the the processing
   * @throws QueryException
   */
  @SuppressWarnings("javadoc")
  private Value processThen(Value in, QueryContext qc, InputInfo ii) throws QueryException {
    List<FItem> handlers = getCallbacks(XqPromise.then, ii);
    Value lastResult = in;
    if(handlers != null) {
      for(final FItem item : handlers) {
        lastResult = invokeFunctionItem(item, qc, ii, lastResult);
      }
    }
    return lastResult;
  }

  /**
   * @param handlers - Callbacks to notify
   * @param qc Query Context
   * @param ii Input Info
   * @param args Arguments to pass to the callbacks
   * @return Nothing
   * @throws QueryException
   */
  private Value notifyCallbacks(List<FItem> handlers, QueryContext qc, InputInfo ii, Value... args) throws QueryException {
    ValueBuilder vb = new ValueBuilder();
    if(handlers != null) {
      for(final FItem item : handlers) {
        vb.add(invokeFunctionItem(item, qc, ii, args));
      }
    }

    return vb.value();
  }

  public Item invItem(QueryContext qc, InputInfo ii, Value... args) throws QueryException {
    return invValue(qc, ii, args).item(qc, ii);
  }

  public int stackFrameSize() {
    return 0;
  }

  public int arity() {
    return 0;
  }
  private static QNm qname = new QNm("Promise", "");
  public QNm funcName() {
    return qname;
  }

  public QNm argName(int pos) {
    return null;
  }

  public FuncType funcType() {
    return FuncType.get(SeqType.ITEM_ZM, SeqType.ITEM_ZM);
  }

  public Expr inlineExpr(Expr[] exprs, QueryContext qc, VarScope scp, InputInfo ii)
      throws QueryException {
    return null;
  }

  @Override
  public FItem coerceTo(FuncType ft, QueryContext qc, InputInfo ii, boolean opt)
      throws QueryException {
    if(instanceOf(ft)) return this;
    throw castError(ii, this, ft);
  }

  @Override
  public Object toJava() throws QueryException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String toString() {
    return callbacks != null ? callbacks.toString() : "";
  }

  @Override
  public void plan(FElem root) {
     // TODO
  }

}
