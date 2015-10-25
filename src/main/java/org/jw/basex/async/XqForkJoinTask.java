package org.jw.basex.async;

import java.util.*;
import java.util.concurrent.*;

import org.basex.query.*;
import org.basex.query.value.*;
import org.basex.query.value.item.*;
import org.basex.util.*;

/**
 * @author James Wright
 * Forks a set of tasks, performing their computation in parallel followed by rejoining the results.
 *
 */
public class XqForkJoinTask extends RecursiveTask<Value> {

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
    XqForkJoinTask[] subtasks = work.size() <= _computeSize
        ? new XqForkJoinTask[0] : splitRemainingWorkload(i);

    for(XqForkJoinTask task : subtasks) {
      task.fork();
    }

    // Perform my work
    try {
      for(FItem deferred : myWork) {
        vb.add(deferred.invokeValue(qc, ii, XqPromise.empty.value()));
      }
    } catch(QueryException ex) {
      Util.notExpected("Failed to process fork join", ex);
    }

    // Rejoin with the others.
    for(XqForkJoinTask task : subtasks) {
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
  private XqForkJoinTask[] splitRemainingWorkload(int taken) {
    List<XqForkJoinTask> subtasks = new ArrayList<XqForkJoinTask>();

    int length = Integer.parseInt(work.size() + "") - taken;

    Value firstHalf = length == 1 ? work.subSeq(taken, 1) : work.subSeq(taken, length/2  );
    subtasks.add(new XqForkJoinTask(firstHalf, new QueryContext(qc), ii, XqPromise.empty.value()));

    if(length > 1) {
      Value secondHalf = work.subSeq(taken + length/2, length - (length/2));
      subtasks.add(new XqForkJoinTask(secondHalf, new QueryContext(qc), ii, XqPromise.empty.value()));
    }

    return subtasks.toArray(new XqForkJoinTask[0]);
  }

}
