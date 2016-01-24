package org.jw.basex.async;

import static org.basex.query.QueryError.castError;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.basex.query.QueryContext;
import org.basex.query.QueryException;
import org.basex.query.expr.Expr;
import org.basex.query.expr.XQFunction;
import org.basex.query.util.list.AnnList;
import org.basex.query.value.Value;
import org.basex.query.value.ValueBuilder;
import org.basex.query.value.array.Array;
import org.basex.query.value.item.FItem;
import org.basex.query.value.item.Int;
import org.basex.query.value.item.Item;
import org.basex.query.value.item.QNm;
import org.basex.query.value.item.Str;
import org.basex.query.value.map.Map;
import org.basex.query.value.node.FElem;
import org.basex.query.value.seq.Empty;
import org.basex.query.value.type.FuncType;
import org.basex.query.value.type.SeqType;
import org.basex.query.var.VarScope;
import org.basex.util.InputInfo;

/**
 * @author James Wright Represents a deferred object for use in the Promise
 *         pattern and async execution
 */
public class XqDeferred extends FItem implements XQFunction {

	/**
	 * Work to perform
	 */
	private FItem[] _work;

	// Work already executed
	List<Future<Value>> _futures;
	/**
	 * Arguments to pass to _work function.
	 */
	private Value _arguments;
	/**
	 * List of callback functions. Valid key values are: done then always fail
	 */
	private List<Entry<String, FItem>> callbacks;

	/**
	 * @param work - Function item representing work to perform
	 * @param args - Arguments to send to the function item
	 * @param callbacks - Map containing callback functions
	 */
	public XqDeferred(final FItem work, final Value args, final Map callbacksIn) throws QueryException {
		super(SeqType.ANY_FUN, new AnnList());
		XqPromise.ensureNotUpdatingFunction(work);
		_work = new FItem[] { work };
		_arguments = args;
		addCallbacks(callbacksIn);
	}

	public XqDeferred(final Value deferreds, final Map callbacksIn) throws QueryException {
		super(SeqType.ANY_FUN, new AnnList());
		addCallbacks(callbacksIn);
		_work = new FItem[(int) deferreds.size()];
		int i = 0;
		for (Item item : deferreds) {
			XqPromise.ensureNotUpdatingFunction((FItem) item);
			_work[i++] = (FItem) item;
		}
	}

	public XqDeferred(final List<Future<Value>> futures) {
		super(SeqType.ANY_FUN, new AnnList());
		_futures = futures;
	}

	public Value invValue(QueryContext qc, InputInfo ii, Value... args) throws QueryException {
		return processInvocation(qc, ii, _arguments);
	}

	public void addCallbacks(Map callbacksIn) throws QueryException {
		if (callbacksIn.contains(XqPromise.then, null)) {
			addCallbacks("then", callbacksIn.get(XqPromise.then, null));
		} else if (callbacksIn.contains(XqPromise.always, null)) {
			addCallbacks("always", callbacksIn.get(XqPromise.always, null));
		} else if (callbacksIn.contains(XqPromise.done, null)) {
			addCallbacks("done", callbacksIn.get(XqPromise.done, null));
		} else if (callbacksIn.contains(XqPromise.fail, null)) {
			addCallbacks("fail", callbacksIn.get(XqPromise.fail, null));
		}
	}

	public void addCallbacks(String name, Value... values) throws QueryException {
		if (values == null) {
			return;
		}
		if (_futures != null) {
			throw new QueryException(
					"Deferred busy due to an earlier call to 'fork'. Queue the callbacks before forking the work.");
		}

		if (name.matches("^(done|fail|always|then)$") == false) {
			throw new QueryException("Invalid callback name provided: " + name);
		}

		if (callbacks == null) {
			callbacks = new ArrayList<Entry<String, FItem>>();
		}

		for (Value callback : values) {
			if (callback instanceof FItem) {
				XqPromise.ensureNotUpdatingFunction((FItem) callback);
				callbacks.add(new AbstractMap.SimpleEntry<String, FItem>(name, (FItem) callback));
			} else if (callback.size() > 0) {
				addCallbacks(name, valueToArray(callback));
				return;
			} else {
				throw new QueryException(
						"Only function items accepted as callbacks. " + callback.getClass().getSimpleName());
			}
		}

	}

	private Value processFutures() throws QueryException {
		ValueBuilder vb = new ValueBuilder();
		for (Future<Value> future : _futures) {
			try {
				vb.add(future.get());
			} catch (InterruptedException | ExecutionException e) {
				throw new QueryException(e);
			}
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
		try {
			if (_futures != null) {
				out = processFutures();
			} else {
				out = processNormalInvocation(qc, ii, args);
			}
		} catch (QueryException e) {
			Value err = this.mapError(e, qc, ii, args);
			out = processFailureCallback(err, qc, ii, e, 0);
		} catch (Throwable e) {
			try {
				File writer = new File("C:\\xq-promise-deferred.log");
				PrintStream ps = new PrintStream(writer);
				e.printStackTrace(ps);
			} catch (IOException e1) {
				e1.printStackTrace();
			}

			throw e;
		}

		return processSuccessCallback(out, qc, ii, 0);
	}

	private Value processFailureCallback(Value error, QueryContext qc, InputInfo ii, QueryException rawError,
			int callbackIndex) throws QueryException {
		Value out = error;
		boolean mitigated = false;
		int i = callbackIndex;
		if (callbacks != null) {
			for (; i < callbacks.size(); i++) {
				Entry<String, FItem> cbs = callbacks.get(i);
				String key = cbs.getKey();
				try {
					if (key.equals(XqPromise.always.toJava())) {
						invokeFunctionItem(cbs.getValue(), qc, ii, out);
					} else if (key.equals(XqPromise.fail.toJava())) {
						out = invokeFunctionItem(cbs.getValue(), qc, ii, out);
						mitigated = true;
						break;
					}
				} catch (QueryException e) {
					out = processFailureCallback(out, qc, ii, e, ++i);
					break;
				}
			}
		}

		if (!mitigated)
			throw rawError;
		return processSuccessCallback(out, qc, ii, ++i);
	}

	private Value processSuccessCallback(Value in, QueryContext qc, InputInfo ii, int callbackIndex)
			throws QueryException {
		Value out = in;
		if (callbacks != null) {
			for (int i = callbackIndex; i < callbacks.size(); i++) {
				Entry<String, FItem> cbs = callbacks.get(i);
				String key = cbs.getKey();
				try {
					if (key.equals(XqPromise.then.toJava())) {
						out = invokeFunctionItem(cbs.getValue(), qc, ii, out);
					} else if (key.equals(XqPromise.done.toJava())) {
						invokeFunctionItem(cbs.getValue(), qc, ii, out);
					} else if (key.equals(XqPromise.always.toJava())) {
						invokeFunctionItem(cbs.getValue(), qc, ii, out);
					}
				} catch (QueryException e) {
					out = processFailureCallback(out, qc, ii, e, ++i);
					continue;
				}
			}
		}
		return out;
	}

	private Value mapError(QueryException e, QueryContext qc, InputInfo ii, Value... args) throws QueryException {
		ValueBuilder eb = new ValueBuilder();

		eb.add(Str.get(e.error() == null ? e.qname().toString() : e.error().code));
		eb.add(Str.get(e.getLocalizedMessage()));
		if(e.file() != null) {
			eb.add(Str.get(e.file()));
		} else {
			eb.add(Str.get(""));
		}
		eb.add(Int.get(e.line()));
		eb.add(Int.get(e.column()));
		eb.add(Array.from(this._work));
		eb.add(Array.from(args));

		if (e.value() != null) {
			eb.add(e.value());
		}

		return XqPromise.get_errorMapFunction().invokeValue(qc, ii, eb.value());
	}

	private Value processNormalInvocation(QueryContext qc, InputInfo ii, Value... args) throws QueryException {
		ValueBuilder vb = new ValueBuilder();
		for (FItem work : _work) {
			vb.add(invokeFunctionItem(work, qc, ii, args));
		}
		return vb.value();
	}

	private Value[] emptyValueArray = new Value[0];

	private Value[] valueToArray(Value in) {
		List<Value> argsArray = new ArrayList<Value>();
		for (int i = 0; i < in.size(); i++) {
			argsArray.add(in.itemAt(i));
		}
		;
		return argsArray.toArray(emptyValueArray);
	}
	
	private Value[] arrayToValueArray(Array in) {
		Value[] vb = new Value[(int) in.arraySize()];
		for(int i = 0; i < in.arraySize(); i++) {
			vb[i] = in.get(i);
		}
		return vb;
	}

	/**
	 * Invokes a method, transforming the arguments to match the function item
	 * provided.
	 *
	 * @param funcItem *            - Function to invoke
	 * @param qc - Query context
	 * @param ii - Input info
	 * @param in - Value to provide during invocation.
	 * @return - Value returned from function invocation.
	 * @throws QueryException
	 */
	private Value invokeFunctionItem(FItem funcItem, QueryContext qc, InputInfo ii, Value... in) throws QueryException {
		Value out;

		int arity = funcItem.arity(); // Arity of the function to call
		int expected = _work == null ? _futures.size() : _work.length; // Expected  work to be returned
		int actual = in.length; // Actual number of arguments

		if (funcItem instanceof XqDeferred) {
			out = funcItem.invokeValue(qc, ii, Empty.SEQ);
		} else if (actual == arity && arity == expected) {
			out = funcItem.invokeValue(qc, ii, in);
		} else if (arity == 0) {
			out = funcItem.invokeValue(qc, ii);
		} else if (actual != expected || arity > actual) {
			Value args = in[0];
			if (args.size() == 1 && args instanceof Array) {
			    Value[] vb = arrayToValueArray((Array) args);	
			    if(funcItem.arity() != vb.length) {
			       throw new QueryException("Invalid number of arguments returned. Expected " + funcItem.arity() + " but was " + vb.length);
			    }
			    out = funcItem.invokeValue(qc, ii, vb);
			} else if (expected > arity) {
				out = funcItem.invokeValue(qc, ii, Array.from(valueToArray(args)));
			} else {
				out = funcItem.invokeValue(qc, ii, valueToArray(args));
			}
		} else {
			throw new QueryException("Invalid number of arguments returned. Expected " + expected + " but was " + actual);
		}

		return out;
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

	public Expr inlineExpr(Expr[] exprs, QueryContext qc, VarScope scp, InputInfo ii) throws QueryException {
		return null;
	}

	@Override
	public FItem coerceTo(FuncType ft, QueryContext qc, InputInfo ii, boolean opt) throws QueryException {
		if (instanceOf(ft))
			return this;
		throw castError(ii, this, ft);
	}

	@Override
	public Object toJava() throws QueryException {
		return this;
	}

	@Override
	public String toString() {
		return callbacks != null ? callbacks.toString() : "";
	}

	@Override
	public void plan(FElem root) {
	}

}
