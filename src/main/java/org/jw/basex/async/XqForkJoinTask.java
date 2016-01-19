package org.jw.basex.async;

import java.util.concurrent.Callable;
import java.util.concurrent.RecursiveTask;

import org.basex.query.QueryContext;
import org.basex.query.QueryException;
import org.basex.query.value.Value;
import org.basex.query.value.ValueBuilder;
import org.basex.query.value.item.FItem;
import org.basex.query.value.seq.Empty;
import org.basex.util.InputInfo;
import org.basex.util.Util;

/**
 * @author James Wright Forks a set of tasks, performing their computation in
 *         parallel followed by rejoining the results.
 *
 */
public class XqForkJoinTask<T extends Value> extends RecursiveTask<Value>implements Callable<T> {

	static final long serialVersionUID = 0L;

	private Value work;
	@SuppressWarnings("javadoc")
	private QueryContext qc;
	@SuppressWarnings("javadoc")
	private InputInfo ii;
	
	/**
	 * Number of workers to compute per fork
	 */
	private int _computeSize;
	private long _start;
	private long _end;

	/**
	 * @param deferreds - Deferred work to fork and rejoin
	 * @param qc - QueryContext
	 * @param ii - Input Information
	 * @param value - Arguments.
	 * @throws QueryException 
	 */
	public XqForkJoinTask(Value deferreds, int computeSize, long start, long end, QueryContext qcIn, InputInfo iiIn, Value... value) throws QueryException {
		work = deferreds;
		
		for(Value deferred : deferreds) {
			if(deferred instanceof FItem && !(deferred instanceof XqDeferred)) {
				XqPromise.ensureNotUpdatingFunction((FItem) deferred);
				if (((FItem)deferred).arity() != 0) {
					throw new QueryException("Invalid input: fork-join can only accept deferred objects, or zero arity functions.");
				}
			}
		}
		
		qc = qcIn;
		ii = iiIn;
		_start = start;
		_end = end;
		_computeSize = computeSize;
		
	}

	public XqForkJoinTask(Value deferreds, long start, long end, QueryContext qcIn, InputInfo iiIn, Value... args) throws QueryException {
		this(deferreds, 2, start, end, qcIn, iiIn, args);
	}

	@SuppressWarnings("unchecked")
	@Override
	public T call() throws Exception {
		return (T) compute();
	}

	@Override
	protected Value compute() {
		ValueBuilder vb = new ValueBuilder();
		long length = _end - _start;
		if (length <= _computeSize) {
			// Perform the work
			try {
				for (long i = _start, j = 0; i < _end && j < _computeSize; i++, j++) {
					FItem deferred = (FItem) work.itemAt(i);
					vb.add(deferred.invokeValue(qc, ii));
				}
			} catch (QueryException ex) {
				this.completeExceptionally(ex);
				this.cancel(true);
			}
		} else {
			// Split the work
			long split = length / 2;
			XqForkJoinTask<Value> second;
			try {
				second = new XqForkJoinTask<Value>(work, _start + split, _end, new QueryContext(qc), ii, Empty.SEQ);
				_end = _start + split;
				
				second.fork(); 
				vb.add(this.compute());
				vb.add((Value)second.join());
			} catch (QueryException e) {}
		}
		
		return vb.value();
	}
}
