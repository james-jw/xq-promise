package org.jw.basex.async;

import java.util.*;
import java.util.concurrent.*;

import org.basex.query.*;
import org.basex.query.value.*;
import org.basex.query.value.item.*;
import org.basex.query.value.seq.Empty;
import org.basex.util.*;

/**
 * @author James Wright
 * Forks a set of tasks, performing their computation in parallel followed by rejoining the results.
 *
 */
public class XqForkJoinTask<T extends Value> extends RecursiveTask<Value> implements Callable<T> {

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

  /**
   * @param deferreds - Deferred work to fork and rejoin
   * @param qc - QueryContext
   * @param ii - Input Information
   * @param args - Arguments.
   */
  public XqForkJoinTask(Value deferreds, int computeSize, QueryContext qcIn, InputInfo iiIn, Value... args) {
    work = deferreds;
    qc = qcIn;
    ii = iiIn;
    _computeSize = computeSize;
  }

  public XqForkJoinTask(Value deferreds, QueryContext qcIn, InputInfo iiIn, Value... args) {
    this(deferreds, 2, qcIn, iiIn, args);
  }

  @SuppressWarnings("unchecked")
  @Override
  public T call() throws Exception {
    return (T) compute();
  }

  @Override
  protected Value compute() {
    ValueBuilder vb = new ValueBuilder();
    List<FItem> myWork = new ArrayList<FItem>(_computeSize);

    // Determine my work
    int i = 0;
    for(Item chunk : work) {
      myWork.add((FItem)chunk);
      if(i++ == _computeSize - 1) {
         break;
      }
    }

    // Let others start the remaining work
    XqForkJoinTask<Value>[] subtasks = work.size() <= _computeSize
        ? new XqForkJoinTask[0] : splitRemainingWorkload(i);

    for(XqForkJoinTask<Value> task : subtasks) {
      task.fork();
    }

    // Perform my work
    try {
      for(FItem deferred : myWork) {
        if(XqPromise.isPromise(deferred).bool(ii)) {
          vb.add(deferred.invokeValue(qc, ii, Empty.SEQ));
        } else if(deferred.arity() == 0) {
          vb.add(deferred.invokeValue(qc, ii));
        } else {
          Util.notExpected("Invalid input: fork-join can only accept deferred objects or functions with an arity of 0.");
        }
      }
    } catch(QueryException ex) {
      Util.notExpected("Failed to process fork join", ex);
    }

    // Rejoin with the others.
    for(XqForkJoinTask<Value> task : subtasks) {
      try {
        vb.add(task.get());
      } catch(InterruptedException e) {
        Util.notExpected("Failed to process fork join: ", e);
      } catch(ExecutionException e) {
        Util.notExpected("Failed to process fork join: ", e);
      }
    }

    return vb.value();
  }

  /**
   * @return The combined value of all the split operations results.
   */
  private XqForkJoinTask<Value>[] splitRemainingWorkload(int taken) {
    List<XqForkJoinTask<Value>> subtasks = new ArrayList<XqForkJoinTask<Value>>();

    int length = Integer.parseInt(work.size() + "") - taken;

    Value firstHalf = length == 1 ? work.subSeq(taken, 1) : work.subSeq(taken, length/2  );
    subtasks.add(new XqForkJoinTask<Value>(firstHalf, new QueryContext(qc), ii, Empty.SEQ));

    if(length > 1) {
      Value secondHalf = work.subSeq(taken + length/2, length - (length/2));
      subtasks.add(new XqForkJoinTask<Value>(secondHalf, new QueryContext(qc), ii, Empty.SEQ));
    }

    return subtasks.toArray(new XqForkJoinTask[0]);
  }

}
