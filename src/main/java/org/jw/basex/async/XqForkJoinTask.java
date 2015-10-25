package org.jw.basex.async;

import java.util.*;
import java.util.concurrent.*;

import org.basex.query.*;
import org.basex.query.value.*;
import org.basex.query.value.item.*;
import org.basex.util.*;

/**
 * @author James Wright
 * Forks a set of tasks, performing their computation in parrallel followed by rejoining the results.
 *
 */
public class XqForkJoinTask extends RecursiveTask<Value> {

  private Value work;
  @SuppressWarnings("javadoc")
  private QueryContext qc;
  @SuppressWarnings("javadoc")
  private InputInfo ii;
  /**
   * Number of worker to compute per fork
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

    // My Work
    if(work.size() <= _computeSize) {
        try {
          for(Item deferred : work) {
            Value v = ((FItem)deferred).invokeValue(qc, ii, XqPromise.empty.value());
            vb.add(v);
          }
       } catch(QueryException ex) {
         Util.notExpected("Failed to process fork join", ex);
       }

       return vb.value();
    }

    // Have someone else do this work
    XqForkJoinTask[] subtasks = splitRemainingWorkload();
    for(XqForkJoinTask task : subtasks) {
      task.fork();
    }

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
  private XqForkJoinTask[] splitRemainingWorkload() {
    List<XqForkJoinTask> subtasks = new ArrayList<XqForkJoinTask>();

    int length = Integer.parseInt(work.size() + "");

    Value firstHalf = length == 1 ? work : work.subSeq(0, length/2  );
    subtasks.add(new XqForkJoinTask(firstHalf, new QueryContext(qc), ii, XqPromise.empty.value()));

    if(length > 1) {
      Value secondHalf = work.subSeq(length/2, length - (length/2));
      subtasks.add(new XqForkJoinTask(secondHalf, new QueryContext(qc), ii, XqPromise.empty.value()));
    }

    return subtasks.toArray(new XqForkJoinTask[0]);
  }

}
