/*
 * jTPCCResult - Collecting of statistics and writing results.
 *
 */

import java.io.*;

public class jTPCCResult
{
    public  Counters	counters[];
    private Object	lock;
    public  double	statsDivider;

    public  static final int	NUM_BUCKETS = 1000;
    private static final double STATS_CUTOFF = 600.0;

    public jTPCCResult()
    {
	counters = new Counters[jTPCCTData.TT_DELIVERY_BG + 1];
	for (int i = 0; i < jTPCCTData.TT_DELIVERY_BG + 1; i++)
	    counters[i] = new Counters();
	lock = new Object();
	statsDivider = Math.log(STATS_CUTOFF * 1000.0) / (double)(NUM_BUCKETS);
    }

    public void collect(jTPCCTData tdata)
    {
	Counters    counter;
	long	    latency;
	int	    bucket;

	if (tdata.trans_type < 0 ||
	    tdata.trans_type > jTPCCTData.TT_DELIVERY_BG)
	    return;

	counter = counters[tdata.trans_type];

	latency = tdata.trans_end - tdata.trans_due;
	if (latency < 1)
	    bucket = 0;
	else
	    bucket = (int)Math.round(Math.log((double)latency) / statsDivider);
	if (bucket >= NUM_BUCKETS)
	    bucket = NUM_BUCKETS - 1;

	/* Only collect data within the defined measurement window */
	if (tdata.trans_end >= jTPCC.result_begin && tdata.trans_end < jTPCC.result_end)
	{
	    synchronized(lock)
	    {
		if (counter.numTrans == 0)
		{
		    counter.minMS = latency;
		    counter.maxMS = latency;
		}
		else
		{
		    if (counter.minMS > latency)
			counter.minMS = latency;
		    if (counter.maxMS < latency)
			counter.maxMS = latency;
		}
		counter.numTrans++;
		counter.sumMS += latency;
		if (tdata.trans_error)
		    counter.numError++;
		if (tdata.trans_rbk)
		    counter.numRbk++;

		counter.bucket[bucket]++;
	    }
	}

	/*
	 * Send the per transaction CSV entry to the result.csv
	 * in any case. The report generator will take care of
	 * filtering the relevant data.
	 */
	jTPCC.csv_result_write(
	    jTPCC.runID + "," +
	    jTPCCTData.trans_type_names[tdata.trans_type] + "," +
	    new java.sql.Timestamp(tdata.trans_due) + "," +
	    new java.sql.Timestamp(tdata.trans_end) + "," +
	    (tdata.trans_due - jTPCC.csv_begin) + "," +
	    (tdata.trans_end - jTPCC.csv_begin) + "," +
	    (tdata.trans_start - tdata.trans_due) + "," +
	    (tdata.trans_end - tdata.trans_due) + "," +
	    ((tdata.trans_rbk) ? "1," : "0,") +
	    ((tdata.trans_error) ? "1\n" : "0\n"));
    }

    public void aggregate(jTPCCResult into)
    {
	synchronized(lock)
	{
	    for (int tt = 0; tt <= jTPCCTData.TT_DELIVERY_BG; tt++)
	    {
		if (into.counters[tt].numTrans == 0)
		{
		    into.counters[tt].minMS = counters[tt].minMS;
		    into.counters[tt].maxMS = counters[tt].maxMS;
		}
		else
		{
		    if (into.counters[tt].minMS > counters[tt].minMS)
			into.counters[tt].minMS = counters[tt].minMS;
		    if (into.counters[tt].maxMS < counters[tt].maxMS)
			into.counters[tt].maxMS = counters[tt].maxMS;
		}
		into.counters[tt].numTrans  += counters[tt].numTrans;
		into.counters[tt].sumMS	    += counters[tt].sumMS;
		into.counters[tt].numError  += counters[tt].numError;
		into.counters[tt].numRbk    += counters[tt].numRbk;
		for (int i = 0; i < NUM_BUCKETS; i++)
		    into.counters[tt].bucket[i] += counters[tt].bucket[i];
	    }
	}
    }

    public class Counters
    {
	public long numTrans = 0;
	public long sumMS = 0;
	public long minMS = 0;
	public long maxMS = 0;
	public long numError = 0;
	public long numRbk = 0;
	public long bucket[] = new long[NUM_BUCKETS];
    }
}
