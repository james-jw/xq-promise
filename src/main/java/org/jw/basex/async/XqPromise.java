package org.jw.basex.async;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.basex.query.*;
import org.basex.query.ann.Annotation;
import org.basex.query.util.list.AnnList;
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
   private static FItem _errorMapFunction;
   
   /**
    * Sets the array to a mapping function provided by the XQuery environment
    * @param mapFunction
    */
   public Value init(final FItem mapFunction) {
	   set_errorMapFunction(mapFunction); 
	   return (new ValueBuilder().value());
   }

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
  
  /** 
   * Attaches one or more then callbacks to an existing promise
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
  
  /**
   * Attaches one or more done callbacks to an existing promise
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
  
  /**
   * Attaches one or more always callbacks to an existing promise
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
  
  /**
   * Attaches one or more fail callbacks to an existing promise
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

  /**
   *  Forks a piece of work or an unexecuted promise.
   * @param - Work or promise chaine to execute
   * @return - A promise to retrieve the result from, if required.
 * @throws QueryException 
   */
	public Value fork(final Value promises) throws QueryException {
		List<Future<Value>> out = new ArrayList<Future<Value>>((int) promises.size());

		if (executor.isShutdown() || executor.isTerminated()) {
			executor = Executors.newCachedThreadPool();
		}

		for (Value p : promises) {
			XqForkJoinTask<Value> task = new XqForkJoinTask<Value>(p, 2, 0l, promises.size(), new QueryContext(queryContext), null);
			out.add(executor.submit(task));
		}

		return new XqDeferred(out);
	}

  /**
   *  Forks a piece of work. 
   * @param work - Work to fork
   * @param arguments - Arguments to provide to the work
   * @return - A promise to retrieve the result from, if required.
   */
  public Value fork(final FItem work, final Value args) throws QueryException {
    return fork(defer(work, args));
  }

  public Value forkJoin(final Value deferreds) throws QueryException {
    return forkJoin(deferreds, Int.get(1)); 
  }

  /**
   * @param deferreds - Deferred work to fork
   * @param workSpit - How many deferreds each thread should handle. Defaults to 2
   * @return - Joined value from the forking in order.
   * @throws QueryException
   */
  public Value forkJoin(final Value deferreds, Int workSplit) throws QueryException {
    ValueBuilder vb = new ValueBuilder();
    XqForkJoinTask<Value> task = new XqForkJoinTask<Value>(deferreds, Integer.parseInt(workSplit.toString() + ""), 0l, 
        deferreds.size(), new QueryContext(queryContext), null, vb.value());

    if(pool.isShutdown() || pool.isTerminated()) {
      pool = new ForkJoinPool(threads);
    }

    try { 
      return pool.invoke(task); 
    } catch (Exception e) { 
      String path = System.getProperty("user.home") + File.separator + "xq-promise.log";

      try {
        File writer = new File(path);
        PrintStream ps = new PrintStream(writer);
        e.printStackTrace(ps);
      } catch (IOException e1) {
        e1.printStackTrace();
      }

      QueryException cause = (QueryException) findQueryException(e);
      if(cause != null) {
        throw cause;
      }

      String msg = "Fork-join failed: POTENTIAL BUG with xq-promise! ... Stack Trace: (" + path + ")";
      throw new QueryException(msg + e.toString());
    }
  }
  
  private Throwable findQueryException(Throwable e) {
	  Throwable out = e.getCause();
	  if(out == null || out instanceof QueryException) {
		  return out;
	  }
	  return findQueryException(out);
  }

  /**
   * @param deferreds - Deferred work to fork
   * @param workSpit - How many deferreds each thread should handle. Defaults to 2
   * @param threadsIn - Number of threads to allow in the pool
   * @return - Joined value from the forking in order.
   * @throws QueryException
   */
  public Value forkJoin(final Value deferreds, Int workSplit, Int threadsIn) throws QueryException {
    ForkJoinPool customPool = new ForkJoinPool(Integer.parseInt(threadsIn + ""));
    XqForkJoinTask<Value> task = new XqForkJoinTask<Value>(deferreds, Integer.parseInt(workSplit.toString() + ""), 0l, deferreds.size(), new QueryContext(queryContext), null);
    Value out = customPool.invoke(task);
    customPool.shutdown();
    return out;
  }

  @Override
  public void close() {
    executor.shutdown();
    try {
      executor.awaitTermination(1, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    pool.shutdown();
  }

  public static void ensureNotUpdatingFunction(FItem item) throws QueryException {
    FItem cb = (FItem) item;
    AnnList anns = cb.annotations();
    if(anns != null && anns.contains(Annotation.UPDATING)) {
      throw new QueryException("Error: Updating expressions are not allowed in 'xq-promise' callbacks.");
    }
  }

	public static FItem get_errorMapFunction() {
		return _errorMapFunction;
	}

	public static void set_errorMapFunction(FItem mapFunction) {
		_errorMapFunction = mapFunction;
	}
}


