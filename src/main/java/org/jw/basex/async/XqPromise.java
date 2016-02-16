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

   private static int threads = Runtime.getRuntime().availableProcessors();
   private static ForkJoinPool pool = new ForkJoinPool(threads);

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
    pool.shutdown();
  }
}


