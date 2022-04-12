package com.github.pgsqlio.benchmarksql.jtpcc;

import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * jTPCCScheduler - Event scheduler for jTPCC
 */
public class jTPCCScheduler implements Runnable {
  private static Logger log = LogManager.getLogger(jTPCCScheduler.class);
  public final static int SCHED_TERM_LAUNCH = 0, SCHED_SUT_LAUNCH = 1, SCHED_BEGIN = 2,
      SCHED_TERMINAL_DATA = 3, SCHED_DELIVERY_DATA = 4, SCHED_END = 5, SCHED_DONE = 6,
      SCHED_TERM_LAUNCH_DONE = 7, SCHED_SUT_LAUNCH_DONE = 8, SCHED_REPORT = 9,
      SCHED_DUMMY_RESULT = 10;


  private jTPCC gdata;
  private long start_ms;
  private long duration_ms;
  private jTPCCTData avl_root = null;
  private Object avl_lock;
  private int avl_num_nodes = 0;
  private int avl_max_nodes = 0;
  private int avl_max_height = 0;
  private Random random;
  private jTPCCResult dummy_result;

  private long current_trans_count = 0;
  private long current_neword_count = 0;

  public jTPCCScheduler(jTPCC gdata) {
    this.gdata = gdata;
    this.start_ms = gdata.csv_begin;
    this.duration_ms = (gdata.rampupMins + gdata.runMins) * 60000;
    this.random = new Random(System.currentTimeMillis());
    this.avl_lock = new Object();
    this.dummy_result = new jTPCCResult();
  }

  public void run() {
    long now;
    jTPCCTData tdata;

    log.info("Scheduler, ready");

    for (;;) {
      /*
       * Fetch the next event from the "timestamp sorted" scheduler event queue.
       */
      synchronized (avl_lock) {
        try {
          for (;;) {
            /*
             * If the queue is empty, we wait without a timeout until somebody is putting an event
             * here. This actually should never happen because the main thread is placing the
             * SCHED_DONE event into the queue on startup and this scheduler is going to exit when
             * it recieves that event.
             */
            if ((tdata = avl_first()) == null) {
              log.trace("Scheduler, queue empty");
              avl_lock.wait();
              continue;
            }

            /*
             * If the event at the head of the queue is not due yet, we wait until it is. We can get
             * interrupted if someone is placing an event in front of the current queue head. In
             * Java we cannot distinguish between that and the timeout expiring or spurious wakeups,
             * so we always recheck the head entry.
             */
            now = System.currentTimeMillis();
            if (tdata.trans_due > now) {
              log.trace("Scheduler, next event due at {}", new java.sql.Timestamp(tdata.trans_due));
              avl_lock.wait(tdata.trans_due - now);
              continue;
            }

            /*
             * We received an event that is due now (or in the past). Consume it from the queue and
             * exit the wait loop.
             */
            log.trace("Scheduler, removing {}", tdata.dumpHdr());
            avl_remove(tdata);
            break;
          }
        } catch (InterruptedException e) {
          log.error("Scheduler, InterruptedException: {}", e.getMessage());
          return;
        }
      }

      /*
       * If this is the SCHED_DONE event, the benchmark duration has elapsed and we can exit.
       */
      if (tdata.sched_code == SCHED_DONE)
        break;

      /*
       * This is a normal runtime event (probably some terminal data is due to be sent to the SUT
       * for processing).
       */
      switch (tdata.sched_code) {
        case SCHED_TERMINAL_DATA:
          gdata.systemUnderTest.queueAppend(tdata);
          switch (tdata.trans_type) {
            case jTPCCTData.TT_NEW_ORDER:
              current_trans_count++;
              current_neword_count++;
              break;
            case jTPCCTData.TT_PAYMENT:
            case jTPCCTData.TT_ORDER_STATUS:
            case jTPCCTData.TT_STOCK_LEVEL:
            case jTPCCTData.TT_DELIVERY:
              current_trans_count++;
              break;
            default:
              break;
          }
          break;

        case SCHED_TERM_LAUNCH:
          gdata.monkeys.queueAppend(tdata);
          break;

        case SCHED_SUT_LAUNCH:
          gdata.systemUnderTest.launchSUTThread(tdata);
          break;

        case SCHED_BEGIN:
          log.info("Scheduler, rampup done - measurement begins");
          break;

        case SCHED_END:
          log.info("Scheduler, run done - measurement ends");
          break;

        case SCHED_TERM_LAUNCH_DONE:
          log.info("Scheduler, all simulated terminals active");
          break;

        case SCHED_SUT_LAUNCH_DONE:
          log.info("Scheduler, all SUT threads active");
          break;

        case SCHED_REPORT:
          long elapsed_ms = System.currentTimeMillis() - start_ms;
          log.info("Scheduler, Current TPM={}, NOPM={}, Progress={}%",
              (current_trans_count * 60) / jTPCC.reportIntervalSecs,
              (current_neword_count * 60) / jTPCC.reportIntervalSecs,
              elapsed_ms * 100 / duration_ms);
          current_trans_count = 0;
          current_neword_count = 0;
          this.at(tdata.trans_due + jTPCC.reportIntervalSecs * 1000, SCHED_REPORT, tdata);
          break;

        case SCHED_DUMMY_RESULT:
          dummy_result.emit(tdata.trans_due);
          this.at(tdata.trans_due + jTPCC.resultIntervalSecs * 1000, SCHED_DUMMY_RESULT, tdata);
          break;

        default:
          log.error("Scheduler, unknown scheduler code {}", tdata.sched_code);
          break;
      }
    }

    log.info("Scheduler, done");
  }

  public void at(long when, int code, jTPCCTData tdata) {
    tdata.sched_code = code;
    tdata.trans_due = when;

    synchronized (avl_lock) {
      avl_insert(tdata);
      avl_lock.notify();
    }
  }

  public void after(long delay, int type, jTPCCTData tdata) {
    at(System.currentTimeMillis() + delay, type, tdata);
  }

  /*
   * avl_insert()
   */
  private void avl_insert(jTPCCTData tdata) {
    tdata.sched_fuzz = randomInt(0, 999999999);
    while (avl_find(tdata) != null)
      tdata.sched_fuzz = randomInt(0, 999999999);

    avl_root = avl_insert_node(avl_root, tdata);

    avl_num_nodes++;
    if (avl_max_nodes < avl_num_nodes)
      avl_max_nodes = avl_num_nodes;
    if (avl_max_height < avl_root.tree_height)
      avl_max_height = avl_root.tree_height;
  }

  private jTPCCTData avl_insert_node(jTPCCTData into, jTPCCTData node) {
    long side;

    if (into == null)
      return node;

    side = avl_compare(node, into);
    if (side < 0)
      into.term_left = avl_insert_node(into.term_left, node);
    else if (side > 0)
      into.term_right = avl_insert_node(into.term_right, node);
    else
      log.error("Scheduler, duplicate avl node {}", node.dumpHdr());
    return avl_balance(into);
  }

  /*
   * avl_remove()
   */
  private void avl_remove(jTPCCTData tdata) {
    avl_root = avl_remove_node(avl_root, tdata);
    avl_num_nodes--;
  }

  private jTPCCTData avl_remove_node(jTPCCTData stack, jTPCCTData needle) {
    jTPCCTData result;
    long side;

    if (stack == null) {
      log.error("Scheduler, entry not found in avl_remove_node: {}", needle.dumpHdr());
      return null;
    }
    side = avl_compare(needle, stack);
    if (side == 0) {
      result = avl_move_right(stack.term_left, stack.term_right);
      stack.term_left = null;
      stack.term_right = null;
      return result;
    }
    if (side < 0)
      stack.term_left = avl_remove_node(stack.term_left, needle);
    else
      stack.term_right = avl_remove_node(stack.term_right, needle);
    return avl_balance(stack);
  }

  private jTPCCTData avl_move_right(jTPCCTData node, jTPCCTData right) {
    if (node == null)
      return right;
    node.term_right = avl_move_right(node.term_right, right);
    return avl_balance(node);
  }


  /*
   * avl_first()
   */
  private jTPCCTData avl_first() {
    jTPCCTData node = avl_root;

    if (node == null)
      return null;

    while (node.term_left != null)
      node = node.term_left;

    return node;
  }

  /*
   * avl_find()
   */
  private jTPCCTData avl_find(jTPCCTData tdata) {
    return avl_find_node(avl_root, tdata);
  }

  private jTPCCTData avl_find_node(jTPCCTData stack, jTPCCTData needle) {
    long side;

    if (stack == null)
      return null;

    side = avl_compare(needle, stack);
    if (side == 0)
      return stack;
    if (side < 0)
      return avl_find_node(stack.term_left, needle);
    else
      return avl_find_node(stack.term_right, needle);
  }

  /*
   * avl_compare()
   */
  private long avl_compare(jTPCCTData node1, jTPCCTData node2) {
    long result;

    result = node1.trans_due - node2.trans_due;
    if (result != 0)
      return result;
    return node1.sched_fuzz - node2.sched_fuzz;
  }

  /*
   * avl_delta()
   */
  private int avl_delta(jTPCCTData node) {
    return ((node.term_left == null) ? 0 : node.term_left.tree_height)
        - ((node.term_right == null) ? 0 : node.term_right.tree_height);
  }

  /*
   * avl_balance()
   */
  private jTPCCTData avl_balance(jTPCCTData node) {
    int delta = avl_delta(node);
    if (delta < -1) {
      if (avl_delta(node.term_right) > 0)
        node.term_right = avl_rotate_right(node.term_right);
      return avl_rotate_left(node);
    } else if (delta > 1) {
      if (avl_delta(node.term_left) < 0)
        node.term_left = avl_rotate_left(node.term_left);
      return avl_rotate_right(node);
    }
    node.tree_height = 0;
    if (node.term_left != null && node.term_left.tree_height > node.tree_height)
      node.tree_height = node.term_left.tree_height;
    if (node.term_right != null && node.term_right.tree_height > node.tree_height)
      node.tree_height = node.term_right.tree_height;
    node.tree_height++;
    return node;
  }

  /*
   * avl_rotate_left()
   */
  private jTPCCTData avl_rotate_left(jTPCCTData node) {
    jTPCCTData r = node.term_right;
    node.term_right = r.term_left;
    r.term_left = avl_balance(node);
    return avl_balance(r);
  }

  /*
   * avl_rotate_right()
   */
  private jTPCCTData avl_rotate_right(jTPCCTData node) {
    jTPCCTData l = node.term_left;
    node.term_left = l.term_right;
    l.term_right = avl_balance(node);
    return avl_balance(l);
  }

  private long randomInt(long min, long max) {
    return (long) (this.random.nextDouble() * (max - min + 1) + min);
  }

}
