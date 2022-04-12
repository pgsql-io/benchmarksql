package com.github.pgsqlio.benchmarksql.jtpcc;

/*
 * jTPCCResult - Collecting of statistics and writing results.
 */
public class jTPCCResult {
  public HistCounter histCounter[];
  public ResCounter resCounter[];
  private Object lock;
  public double statsDivider;
  private long resultStartMS;
  private long resultNextDue;

  public static final int NUM_BUCKETS = 1000;
  public static final double STATS_CUTOFF = 600.0;

  public jTPCCResult() {
    histCounter = new HistCounter[jTPCCTData.TT_DELIVERY_BG + 1];
    for (int i = 0; i < jTPCCTData.TT_DELIVERY_BG + 1; i++)
      histCounter[i] = new HistCounter();
    resCounter = new ResCounter[jTPCCTData.TT_DELIVERY_BG + 1];
    for (int i = 0; i < jTPCCTData.TT_DELIVERY_BG + 1; i++)
      resCounter[i] = new ResCounter();
    lock = new Object();
    statsDivider = Math.log(STATS_CUTOFF * 1000.0) / (double) (NUM_BUCKETS);

    resultStartMS = jTPCC.csv_begin;
    resultNextDue = resultStartMS + (jTPCC.resultIntervalSecs * 1000);
  }

  public void collect(jTPCCTData tdata) {
    HistCounter hCounter;
    ResCounter rCounter;
    long latency;
    long delay;
    int bucket;

    if (tdata.trans_type < 0 || tdata.trans_type > jTPCCTData.TT_DELIVERY_BG)
      return;

    hCounter = histCounter[tdata.trans_type];
    rCounter = resCounter[tdata.trans_type];

    latency = tdata.trans_end - tdata.trans_due;
    delay = tdata.trans_start - tdata.trans_due;
    if (latency < 1)
      bucket = 0;
    else
      bucket = (int) Math.round(Math.log((double) latency) / statsDivider);
    if (bucket >= NUM_BUCKETS)
      bucket = NUM_BUCKETS - 1;

    /* Only collect data within the defined measurement window */
    if (tdata.trans_end >= jTPCC.result_begin && tdata.trans_end < jTPCC.result_end) {
      synchronized (lock) {
        if (hCounter.numTrans == 0) {
          hCounter.minMS = latency;
          hCounter.maxMS = latency;
        } else {
          if (hCounter.minMS > latency)
            hCounter.minMS = latency;
          if (hCounter.maxMS < latency)
            hCounter.maxMS = latency;
        }
        hCounter.numTrans++;
        hCounter.sumMS += latency;
        if (tdata.trans_error)
          hCounter.numError++;
        if (tdata.trans_rbk)
          hCounter.numRbk++;

        hCounter.bucket[bucket]++;
      }
    }

    rCounter.numTrans++;
    rCounter.sumLatencyMS += latency;
    rCounter.sumDelayMS += delay;
    if (rCounter.numTrans == 1) {
      rCounter.minLatencyMS = latency;
      rCounter.maxLatencyMS = latency;
      rCounter.minDelayMS = delay;
      rCounter.maxDelayMS = delay;
    } else {
      if (latency < rCounter.minLatencyMS)
        rCounter.minLatencyMS = latency;
      if (latency > rCounter.maxLatencyMS)
        rCounter.maxLatencyMS = latency;
      if (delay < rCounter.minDelayMS)
        rCounter.minDelayMS = delay;
      if (delay > rCounter.maxDelayMS)
        rCounter.maxDelayMS = delay;
    }

    long now = System.currentTimeMillis();
    if (now >= resultNextDue) {
      this.emit(now);
    }
  }

  public void emit(long now) {
    long second = (resultNextDue - resultStartMS) / 1000;

    for (int tt = 0; tt <= jTPCCTData.TT_DELIVERY_BG; tt++) {
      jTPCC.csv_result_write(
          jTPCCTData.trans_type_names[tt] + "," + second + "," + resCounter[tt].numTrans + ","
              + resCounter[tt].sumLatencyMS + "," + resCounter[tt].minLatencyMS + ","
              + resCounter[tt].maxLatencyMS + "," + resCounter[tt].sumDelayMS + ","
              + resCounter[tt].minDelayMS + "," + resCounter[tt].maxDelayMS + "\n");

      resCounter[tt].numTrans = 0;
      resCounter[tt].sumLatencyMS = 0;
      resCounter[tt].minLatencyMS = 0;
      resCounter[tt].maxLatencyMS = 0;
      resCounter[tt].sumDelayMS = 0;
      resCounter[tt].minDelayMS = 0;
      resCounter[tt].maxDelayMS = 0;
    }

    while (resultNextDue <= now)
      resultNextDue += (jTPCC.resultIntervalSecs * 1000);
  }

  public void aggregate(jTPCCResult into) {
    synchronized (lock) {
      for (int tt = 0; tt <= jTPCCTData.TT_DELIVERY_BG; tt++) {
        if (into.histCounter[tt].numTrans == 0) {
          into.histCounter[tt].minMS = histCounter[tt].minMS;
          into.histCounter[tt].maxMS = histCounter[tt].maxMS;
        } else {
          if (into.histCounter[tt].minMS > histCounter[tt].minMS)
            into.histCounter[tt].minMS = histCounter[tt].minMS;
          if (into.histCounter[tt].maxMS < histCounter[tt].maxMS)
            into.histCounter[tt].maxMS = histCounter[tt].maxMS;
        }
        into.histCounter[tt].numTrans += histCounter[tt].numTrans;
        into.histCounter[tt].sumMS += histCounter[tt].sumMS;
        into.histCounter[tt].numError += histCounter[tt].numError;
        into.histCounter[tt].numRbk += histCounter[tt].numRbk;
        for (int i = 0; i < NUM_BUCKETS; i++)
          into.histCounter[tt].bucket[i] += histCounter[tt].bucket[i];
      }
    }
  }

  public class HistCounter {
    public long numTrans = 0;
    public long sumMS = 0;
    public long minMS = 0;
    public long maxMS = 0;
    public long numError = 0;
    public long numRbk = 0;
    public long bucket[] = new long[NUM_BUCKETS];
  }

  public class ResCounter {
    public long numTrans = 0;
    public long sumLatencyMS = 0;
    public long minLatencyMS = 0;
    public long maxLatencyMS = 0;
    public long sumDelayMS = 0;
    public long minDelayMS = 0;
    public long maxDelayMS = 0;
  }
}
