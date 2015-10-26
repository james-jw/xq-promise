package org.jw.basex.async;

import java.util.concurrent.*;

import org.basex.query.*;
import org.basex.query.value.*;
import org.basex.query.value.item.*;
import org.basex.query.value.map.*;

/**
 * @author James Wright
 * Implements the promise pattern as well as fork-join for async processing
 */
public class XqPromise extends QueryModule  {
   public static final ValueBuilder empty = new ValueBuilder();

   public static final Str fail = Str.get("fail");
   public static final Str done = Str.get("done");
   public static final Str always = Str.get("always");
   public static final Str then = Str.get("then");

   private static int threads = Runtime.getRuntime().availableProcessors();
   private static ForkJoinPool pool = new ForkJoinPool(threads);

   /**
   * @param work - function of work to defer
   * @param args - arguments to pass to the work function.
   * @param callbacks - callbacks to excecute upon completion and or failure.
   * @return - Deferred function item.
   */
  public FItem defer(final FItem work, final Value args, final Map callbacks ) {
      return new XqDeferred(work, args, callbacks);
   }

  /**
  * @param work - function of work to defer
  * @param args - arguments to pass to the work function.
  * @return - Deferred function item.
  */
  public FItem defer(final FItem work, final Value args ) {
     return new XqDeferred(work, args, Map.EMPTY);
  }

  /**
   * @param work - function of work to defer
   * @return - Deferred function item.
   */
  public FItem defer(final FItem work) {
	 return new XqDeferred(work, XqPromise.empty.value(), Map.EMPTY);
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
  public FItem when(final Value deferreds, final Map callbacks ) {
     return new XqDeferred(deferreds, callbacks);
   }

   /**
   * @param deferreds - List of deferred promises to combine.
   * @return - Deferred function item.
   */
  public Value when(final Value deferreds) {
     return new XqDeferred(deferreds, Map.EMPTY);
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
    XqForkJoinTask task = new XqForkJoinTask(deferreds, Integer.parseInt(workSplit.toString() + ""), new QueryContext(queryContext), null, vb.value());
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
    XqForkJoinTask task = new XqForkJoinTask(deferreds, Integer.parseInt(workSplit.toString() + ""), new QueryContext(queryContext), null, vb.value());
    Value out = customPool.invoke(task);
    customPool.shutdown();
    return out;
  }
}


