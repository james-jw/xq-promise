package org.jw.basex.async;

import static org.basex.query.QueryError.*;

import java.util.*;
import java.util.List;

import org.basex.query.*;
import org.basex.query.expr.*;
import org.basex.query.util.list.*;
import org.basex.query.value.*;
import org.basex.query.value.array.Array;
import org.basex.query.value.item.*;
import org.basex.query.value.map.Map;
import org.basex.query.value.node.*;
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
  @SuppressWarnings("unused")
  private List<Map> _callbacks = new ArrayList<Map>();

  /**
   * @param work - Function item representing work to perform
   * @param args - Arguments to send to the function item
   * @param callbacks - Map containing callback functions
   */
  public XqDeferred(final FItem work, final Value args, final Map callbacks) {
    super(SeqType.ANY_FUN, new AnnList());
    _work = new FItem[] { work };
    _arguments = args;
    _callbacks.add(callbacks);
  }

  public XqDeferred(final Value deferreds, final Map callbacks) {
    super(SeqType.ANY_FUN, new AnnList());
    _callbacks.add(callbacks);
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

  public Value invValue(QueryContext qc, InputInfo ii, Value... args) throws QueryException {
    if(args[0].seqType() == SeqType.EMP) {
      return processInvocation(qc, ii, _arguments);
    } else if(args.length == 1) {
      _callbacks.add((Map)args[0]);
      return this;
    }

    return XqPromise.empty.value();
  }

  /**
   * @param type - Type of callback to return (then, done, always, fail)
   * @param ii - InputInfo
   * @return - A set of callbacks in execution order
   * @throws QueryException
   */
  private Value getCallbacks(Str type, InputInfo ii) throws QueryException {
    ValueBuilder vb = new ValueBuilder();
    for(Map set : _callbacks) {
      vb.add(set.get(type, ii));
    }
    return vb.value();
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
    ValueBuilder vb = new ValueBuilder();
    boolean failed = false;
    try {
      for(FItem work : _work) {
          vb.add(invokeFunctionItem(work, qc, ii, args));
      }
      out = vb.value();
    } catch (Exception e) {
      failed = true;
      notifyCallbacks(getCallbacks(XqPromise.always, ii), qc, ii, Str.get(e.getMessage()));
      out = notifyCallbacks(getCallbacks(XqPromise.fail, ii), qc, ii, args);
    }

    if(!failed) {
      out = processThen(out, qc, ii);
      notifyCallbacks(getCallbacks(XqPromise.done, ii), qc, ii, out);
      notifyCallbacks(getCallbacks(XqPromise.always, ii), qc, ii, out);
    }

    return out;
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
    int expected = _work.length; // Expected work to be returned  
    int actual = in.length; // Actual number of arguments

    if(funcItem instanceof XqDeferred) {
      out = funcItem.invokeValue(qc, ii, XqPromise.empty.value());
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
    Value handlers = getCallbacks(XqPromise.then, ii);
    Value lastResult = in;
    for(final Item item : handlers) {
      FItem funcItem = (FItem)item;
      lastResult = invokeFunctionItem(funcItem, qc, ii, lastResult);
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
  private Value notifyCallbacks(Value handlers, QueryContext qc, InputInfo ii, Value... args) throws QueryException {
    ValueBuilder vb = new ValueBuilder();
    for(final Item item : handlers) {
      FItem funcItem = (FItem)item;
      vb.add(invokeFunctionItem(funcItem, qc, ii, args));
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
    return 1;
  }

  public QNm funcName() {
    return null;
  }

  public QNm argName(int pos) {
    switch(pos) {
      case 0: return new QNm("deferreds", "");
      case 1: return new QNm("arguments", "");
      case 2: return new QNm("callbacks", "");
    }
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
    return _callbacks != null ? _callbacks.toString() : "";
  }

  @Override
  public void plan(FElem root) {
     // TODO
  }

}
