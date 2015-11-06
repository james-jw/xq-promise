package org.jw.basex.async;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

import org.basex.query.*;
import org.basex.query.value.*;
import org.basex.query.value.item.*;
import org.basex.query.value.map.*;
import org.basex.query.value.seq.Empty;

/**
 * @author James Wright
 * Implements the promise pattern as well as fork-join for async processing
 */
public class XqPromise extends QueryModule implements QueryResource  {

   public static final Str fail = Str.get("fail");
   public static final Str done = Str.get("done");
   public static final Str always = Str.get("always");
   public static final Str then = Str.get("then");

   private static int threads = Runtime.getRuntime().availableProcessors();
   private static ForkJoinPool pool = new ForkJoinPool(threads);
   private static ExecutorService executor = Executors.newCachedThreadPool();

   /**
   * @param work - function of work to defer
   * @param args - arguments to pass to the work function.
   * @param callbacks - callbacks to excecute upon completion and or failure.
   * @return - Deferred function item.
   */
  public FItem defer(final FItem work, final Value args, final Map callbacks ) throws QueryException {
      return new XqDeferred(work, args, callbacks);
   }

  /**
  * @param work - function of work to defer
  * @param args - arguments to pass to the work function.
  * @return - Deferred function item.
  */
  public FItem defer(final FItem work, final Value args ) throws QueryException {
     return new XqDeferred(work, args, Map.EMPTY);
  }

  /**
   * @param work - function of work to defer
   * @return - Deferred function item.
   */
  public FItem defer(final FItem work) throws QueryException {
	 return new XqDeferred(work, Empty.SEQ, Map.EMPTY);
  }

  /**
   * Determines if an item is a deferred promise.
   * @param funcItem - Item to test.
   * @return True if the item is a promise
   */
  public static Bln isPromise(final FItem funcItem) {
    return Bln.get(funcItem instanceof XqDeferred);
  }

   /**
   * @param deferreds - List of deferred promises to combine.
   * @param callbacks - map of call backs: then, done, always, fail
   * @return - Deferred function item.
   */
  public FItem when(final Value deferreds, final Map callbacks ) throws QueryException {
     return new XqDeferred(deferreds, callbacks);
  }

   /**
   * @param deferreds - List of deferred promises to combine.
   * @return - Deferred function item.
   */
  public Value when(final Value deferreds) throws QueryException {
     return new XqDeferred(deferreds, Map.EMPTY);
  }

  public Value attach(final Value deferred, final Map callbacks) throws QueryException {
     if(deferred instanceof XqDeferred) {
       ((XqDeferred)deferred).addCallbacks(callbacks);
       return deferred;
     } else { throw new QueryException("Can only add callbacks to deferreds."); } 
  }
  /* Attaches one or more then callbacks to an existing promise
   * @param promise - Promise to attach callback too
   * @param callbacks - Callbacks to attach
   * @return - The promise passed in as the first argument. Useful in chaining
   */
  public Value then(final Value deferred, final Value callbacks) throws QueryException {
     if(deferred instanceof XqDeferred) {
       ((XqDeferred)deferred).addCallbacks("then", callbacks);
       return deferred;
     } else { throw new QueryException("Can only add callbacks to deferreds."); } 
  }
  
  /* Attaches one or more done callbacks to an existing promise
   * @param promise - Promise to attach callback too
   * @param callbacks - Callbacks to attach
   * @return - The promise passed in as the first argument. Useful in chaining
   */
  public Value done(final Value deferred, final Value callbacks) throws QueryException {
     if(deferred instanceof XqDeferred) {
       ((XqDeferred)deferred).addCallbacks("done", callbacks);
       return deferred;
     } else { throw new QueryException("Can only add callbacks to deferreds."); } 
  }
  
  /* Attaches one or more always callbacks to an existing promise
   * @param promise - Promise to attach callback too
   * @param callbacks - Callbacks to attach
   * @return - The promise passed in as the first argument. Useful in chaining
   */
  public Value always(final Value deferred, final Value callbacks) throws QueryException {
     if(deferred instanceof XqDeferred) {
       ((XqDeferred)deferred).addCallbacks("always", callbacks);
       return deferred;
     } else { throw new QueryException("Can only add callbacks to deferreds."); } 
  }
  
  /* Attaches one or more fail callbacks to an existing promise
   * @param promise - Promise to attach callback too
   * @param callbacks - Callbacks to attach
   * @return - The promise passed in as the first argument. Useful in chaining
   */
  public Value fail(final Value deferred, final Value callbacks) throws QueryException {
     if(deferred instanceof XqDeferred) {
       ((XqDeferred)deferred).addCallbacks("fail", callbacks);
       return deferred;
     } else { throw new QueryException("Can only add callbacks to deferreds."); } 
  }

  /* Forks a piece of work or an unexecuted promise.
   * @param - Work or promise chaine to execute
   * @return - A promise to retrieve the result from, if required.
   */
	public Value fork(final Value promises) {
		List<Future<Value>> out = new ArrayList<Future<Value>>((int) promises.size());

		if (executor.isShutdown() || executor.isTerminated()) {
			executor = Executors.newCachedThreadPool();
		}

		for (Value p : promises) {
			XqForkJoinTask<Value> task = new XqForkJoinTask<Value>(p, 1, new QueryContext(queryContext), null);
			out.add(executor.submit(task));
		}

		return new XqDeferred(out);
	}

  /* Forks a piece of work. 
   * @param work - Work to fork
   * @param arguments - Arguments to provide to the work
   * @return - A promise to retrieve the result from, if required.
   */
  public Value fork(final FItem work, final Value args) throws QueryException {
    return fork(defer(work, args));
  }

  public Value forkJoin(final Value deferreds) throws QueryException {
    return forkJoin(deferreds, Int.get(2)); 
  }

  /**
   * @param deferreds - Deferred work to fork
   * @param workSpit - How many deferreds each thread should handle. Defaults to 2
   * @return - Joined value from the forking in order.
   * @throws QueryException
   */
  public Value forkJoin(final Value deferreds, Int workSplit) throws QueryException {
    ValueBuilder vb = new ValueBuilder();
    XqForkJoinTask<Value> task = new XqForkJoinTask<Value>(deferreds, Integer.parseInt(workSplit.toString() + ""), new QueryContext(queryContext), null, vb.value());
    
    if(pool.isShutdown() || pool.isTerminated()) {
	   pool = new ForkJoinPool(threads);
	  }
    
    return pool.invoke(task);
  }

  /**
   * @param deferreds - Deferred work to fork
   * @param workSpit - How many deferreds each thread should handle. Defaults to 2
   * @param threadsIn - Number of threads to allow in the pool
   * @return - Joined value from the forking in order.
   * @throws QueryException
   */
  public Value forkJoin(final Value deferreds, Int workSplit, Int threadsIn) throws QueryException {
    ValueBuilder vb = new ValueBuilder();
    ForkJoinPool customPool = new ForkJoinPool(Integer.parseInt(threadsIn + ""));
    XqForkJoinTask<Value> task = new XqForkJoinTask<Value>(deferreds, Integer.parseInt(workSplit.toString() + ""), new QueryContext(queryContext), null, vb.value());
    Value out = customPool.invoke(task);
    customPool.shutdown();
    return out;
  }

  @Override
	public void close() {
		executor.shutdown();
		pool.shutdown();
	}
}


