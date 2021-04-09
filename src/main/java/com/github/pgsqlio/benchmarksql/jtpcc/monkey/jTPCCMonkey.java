package com.github.pgsqlio.benchmarksql.jtpcc.monkey;
import java.util.Formatter;
import java.util.Locale;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.pgsqlio.benchmarksql.jtpcc.jTPCC;
import com.github.pgsqlio.benchmarksql.jtpcc.jTPCCRandom;
import com.github.pgsqlio.benchmarksql.jtpcc.jTPCCResult;
import com.github.pgsqlio.benchmarksql.jtpcc.jTPCCScheduler;
import com.github.pgsqlio.benchmarksql.jtpcc.jTPCCTData;
import com.github.pgsqlio.benchmarksql.jtpcc.jTPCCTDataList;

/**
 * jTPCCMonkey - The terminal input data generator and output consumer.
 */
public class jTPCCMonkey
{
    private static Logger log = LogManager.getLogger(jTPCCMonkey.class);
    private static Logger logResult = LogManager.getLogger(jTPCC.class);

    private jTPCC		gdata;
    private int			numMonkeys;
    private Monkey		monkeys[];
    private Thread		monkeyThreads[];

    private jTPCCTDataList	queue;
    private Object		queue_lock;

    private Object		trace_terminal_lock;

    private jTPCCRandom		rnd;

    public jTPCCMonkey(jTPCC gdata)
    {
	this.gdata	    = gdata;
	this.numMonkeys	    = jTPCC.numMonkeys;
	this.monkeys	    = new Monkey[jTPCC.numMonkeys];
	this.monkeyThreads  = new Thread[jTPCC.numMonkeys];

	this.queue	    = new jTPCCTDataList();
	this.queue_lock	    = new Object();

	this.trace_terminal_lock = new Object();

	this.rnd = gdata.rnd.newRandom();

	for (int m = 0; m < jTPCC.numMonkeys; m++)
	{
	    monkeys[m] = new Monkey(m, this);
	    monkeyThreads[m] = new Thread(monkeys[m]);
	    monkeyThreads[m].start();
	}
    }

    public void reportStatistics()
    {
	jTPCCResult    sumStats = new jTPCCResult();
	StringBuilder	    sb = new StringBuilder();
	Formatter	    fmt = new Formatter(sb, Locale.US);
	double		    total_count = 0.0;

	for (int m = 0; m < jTPCC.numMonkeys; m++)
	{
	    monkeys[m].result.aggregate(sumStats);
	}
	gdata.systemUnderTest.deliveryBg.result.aggregate(sumStats);

	for (int tt = jTPCCTData.TT_NEW_ORDER;
	     tt <= jTPCCTData.TT_DELIVERY; tt++)
	{
	    total_count += (double)(sumStats.counters[tt].numTrans);
	}

	logResult.info("result,                                           _____ latency (seconds) _____");
	logResult.info("result,   TransType              count |   mix % |    mean       max     90th% |    rbk%          errors");
	logResult.info("result, +--------------+---------------+---------+---------+---------+---------+---------+---------------+");
	for (int tt = jTPCCTData.TT_NEW_ORDER; tt <= jTPCCTData.TT_DELIVERY_BG; tt++)
	{
	    double		count;
	    double		percent;
	    double		mean;
	    double		max;
	    double		nth_pct;
	    long		nth_needed;
	    long		nth_have = 0;
	    double		rbk;
	    double		errors;
	    int			b;

	    count = (double)(sumStats.counters[tt].numTrans);
	    percent = count / total_count * 100.0;
	    if (tt == jTPCCTData.TT_DELIVERY_BG)
	    	percent = 0.0;
	    mean = (double)(sumStats.counters[tt].sumMS) / 1000.0 / count;
	    max = (double)(sumStats.counters[tt].maxMS) / 1000.0;
	    rbk = (double)(sumStats.counters[tt].numRbk) / count * 100.0;
	    errors = (double)(sumStats.counters[tt].numError);

	    nth_needed = (long)((double)(sumStats.counters[tt].numTrans) * 0.9);
	    for (b = 0; b < jTPCCResult.NUM_BUCKETS && nth_have < nth_needed; b++)
	    {
		nth_have += sumStats.counters[tt].bucket[b];
	    }
	    nth_pct = Math.exp((double)b * sumStats.statsDivider) / 1000.0;
	    if (sumStats.counters[tt].numTrans == 0)
	    {
		count = max = nth_pct = rbk = errors = 0.0 / 0.0;
	    }


	    fmt.format("| %-12.12s | %,13d | %7.3f | %7.3f | %7.3f | %7.3f | %7.3f | %,13.0f |",
		       jTPCCTData.trans_type_names[tt],
		       sumStats.counters[tt].numTrans, percent, mean,
		       max, nth_pct, rbk, errors);
	    logResult.info("result, {}", sb.toString());
	    sb.setLength(0);
	}
	logResult.info("result, +--------------+---------------+---------+---------+---------+---------+---------+---------------+");

	logResult.info("result,");

	fmt.format("Overall NOPM: %,12.0f (%.2f%% of the theoretical maximum)",
	    (double)(sumStats.counters[jTPCCTData.TT_NEW_ORDER].numTrans) /
	    (double)(jTPCC.runMins),
	    (double)(sumStats.counters[jTPCCTData.TT_NEW_ORDER].numTrans) /
	    ((double)jTPCC.numWarehouses * 0.1286 * (double)(jTPCC.runMins)));
	logResult.info("result, {}", sb.toString());
	sb.setLength(0);

	fmt.format("Overall TPM:  %,12.0f", total_count / (double)(jTPCC.runMins));
	logResult.info("result, {}", sb.toString());
	sb.setLength(0);
    }

    public void terminate()
    {
	synchronized(queue_lock)
	{
	    queue.truncate();

	    for (int m = 0; m < numMonkeys; m++)
	    {
		jTPCCTData doneMsg = new jTPCCTData();
		doneMsg.trans_type = jTPCCTData.TT_DONE;
		queue.append(doneMsg);
	    }

	    queue_lock.notify();
	}

	for (int m = 0; m < numMonkeys; m++)
	{
	    try
	    {
		monkeyThreads[m].join();
	    }
	    catch (InterruptedException e)
	    {
	    }
	}
    }

    public void queueAppend(jTPCCTData tdata)
    {
	synchronized(queue_lock)
	{
	    queue.append(tdata);
	    queue_lock.notify();
	}
    }

    private class Monkey implements Runnable
    {

	private int		    m_id;
	private jTPCCMonkey    parent;
	private Random		    random;

	public	jTPCCResult    result;

	public Monkey(int m_id, jTPCCMonkey parent)
	{

	    this.m_id = m_id;
	    this.parent = parent;
	    this.random = new Random(System.currentTimeMillis());

	    this.result = new jTPCCResult();
	}

	public void run()
	{
	    jTPCCTData	tdata;
	    double		think_time;
	    double		key_time;
	    double		key_mean;

	    for (;;)
	    {
		synchronized(queue_lock)
		{
		    while ((tdata = queue.first()) == null)
		    {
			try
			{
			    queue_lock.wait();
			}
			catch (InterruptedException e)
			{
			    log.error("monkey-{}, InterruptedException: {}", this.m_id,
				      e.getMessage());
			    return;
			}
		    }
		    queue.remove(tdata);

		    /*
		     * If there are more result, notify another input
		     * data generator (if there is an idle one).
		     */
		    if (queue.first() != null)
			queue_lock.notify();
		}

		/*
		 * Exit the loop (and terminate this thread) when
		 * we receive the DONE signal.
		 */
		if (tdata.trans_type == jTPCCTData.TT_DONE)
		    break;

		/*
		 */
		if (tdata.trans_type != jTPCCTData.TT_NONE)
		{
		}

		/*
		 * Process the last transactions result and
		 * determine the think time based on the previous
		 * transaction type.
		 */
		switch (tdata.trans_type)
		{
		    case jTPCCTData.TT_NONE:
			think_time = 0.0;
			break;

		    case jTPCCTData.TT_NEW_ORDER:
			processNewOrderResult(tdata);
			result.collect(tdata);
			think_time = 12.0;
			break;

		    case jTPCCTData.TT_PAYMENT:
			processPaymentResult(tdata);
			result.collect(tdata);
			think_time = 12.0;
			break;

		    case jTPCCTData.TT_ORDER_STATUS:
			processOrderStatusResult(tdata);
			result.collect(tdata);
			think_time = 10.0;
			break;

		    case jTPCCTData.TT_STOCK_LEVEL:
			processStockLevelResult(tdata);
			result.collect(tdata);
			think_time = 5.0;
			break;

		    case jTPCCTData.TT_DELIVERY:
			processDeliveryResult(tdata);
			result.collect(tdata);
			think_time = 5.0;
			break;

		    default:
			think_time = 0.0;
			break;
		}

		/*
		 * Initialize trans_rbk as false. The New Order
		 * input generator may set it to true.
		 */
		tdata.trans_rbk = false;

		/*
		 * Select the next transaction type.
		 */
		tdata.trans_type = nextTransactionType();
		switch (tdata.trans_type)
		{
		    case jTPCCTData.TT_NEW_ORDER:
			generateNewOrder(tdata);
			key_mean = 18.0;
			break;

		    case jTPCCTData.TT_PAYMENT:
			generatePayment(tdata);
			key_mean = 3.0;
			break;

		    case jTPCCTData.TT_ORDER_STATUS:
			generateOrderStatus(tdata);
			key_mean = 2.0;
			break;

		    case jTPCCTData.TT_STOCK_LEVEL:
			generateStockLevel(tdata);
			key_mean = 2.0;
			break;

		    case jTPCCTData.TT_DELIVERY:
			generateDelivery(tdata);
			key_mean = 2.0;
			break;

		    default:
			key_mean = 0.0;
			break;
		}

		/*
		 * Calculate keying time according to 5.2.5.4, then
		 * apply our non-standard multiplier that allows us
		 * to drive a higher rate of transactions without
		 * scaling to more warehouses.
		 */
		double r = randomDouble();
		if (r < 0.000045)
		    key_time = key_mean * 10.0;
		else
		    key_time = -Math.log(r) * key_mean;
		think_time *= jTPCC.thinkTimeMultiplier;
		key_time *= jTPCC.keyingTimeMultiplier;

		/*
		 * Set up the terminal data header fields. The
		 * Transaction due time is based on the last transactions
		 * end time. This eliminates delays caused bu the monkeys
		 * not reading or typing at infinite speed.
		 */
		tdata.trans_due = tdata.trans_end +
				  (long)((think_time + key_time) * 1000.0);
		tdata.trans_start = 0;
		tdata.trans_end = 0;
		tdata.trans_error = false;

		gdata.scheduler.at(tdata.trans_due,
				   jTPCCScheduler.SCHED_TERMINAL_DATA,
				   tdata);
	    }
	}

	private void generateNewOrder(jTPCCTData tdata)
	{
	    jTPCCTData.NewOrderData screen = tdata.NewOrderData();
	    int ol_count;
	    int ol_idx = 0;

	    // 2.4.1.1 - w_id = terminal's w_id
	    screen.w_id = tdata.term_w_id;

	    // 2.4.1.2 - random d_id and non uniform random c_id
	    screen.d_id = rnd.nextInt(1, 10);
	    screen.c_id = rnd.getCustomerID();

	    // 2.4.1.3 - random [5..15] order lines
	    ol_count = rnd.nextInt(5, 15);
	    while (ol_idx < ol_count)
	    {
		// 2.4.1.5 1) - non uniform ol_i_id
		screen.ol_i_id[ol_idx] = rnd.getItemID();

		// 2.4.1.5 2) - In 1% of order lines the supply warehouse
		// is different from the terminal's home warehouse.
		screen.ol_supply_w_id[ol_idx] = tdata.term_w_id;
		if (rnd.nextInt(1, 100) == 1)
		{
		    do
		    {
			screen.ol_supply_w_id[ol_idx] = rnd.nextInt(1, jTPCC.numWarehouses);
		    }
		    while (screen.ol_supply_w_id[ol_idx] == tdata.term_w_id && jTPCC.numWarehouses > 1);
		}

		// 2.4.1.5 3) - random ol_quantity [1..10]
		screen.ol_quantity[ol_idx] = rnd.nextInt(1, 10);
		ol_idx++;
	    }

	    // 2.4.1.4 - 1% of orders must use an invalid ol_o_id in the last
	    // order line generated.
	    if (rnd.nextInt(1, 100) == 1)
	    {
		screen.ol_i_id[ol_idx - 1] = 999999;
		tdata.trans_rbk = true;
	    }

	    // Zero out the remaining order lines if they contain old data.
	    while (ol_idx < 15)
	    {
		screen.ol_supply_w_id[ol_idx] = 0;
		screen.ol_i_id[ol_idx] = 0;
		screen.ol_quantity[ol_idx] = 0;
		ol_idx++;
	    }

	    tdata.new_order = screen;
	}

	private void processNewOrderResult(jTPCCTData tdata)
	{
	    jTPCCTData.NewOrderData newOrder = tdata.new_order;

	    tdata.new_order = null;
	    if (log.isDebugEnabled()) {

	    StringBuffer    sb[] = new StringBuffer[23];
	    Formatter       fmt[] = new Formatter[23];
	    for (int i = 0; i < 23; i++)
	    {
		sb[i] = new StringBuffer();
		fmt[i] = new Formatter(sb[i]);
	    }

	    // NEW_ORDER OUTPUT screen
	    fmt[0].format("                                    New Order");
	    fmt[1].format("Warehouse: %6d  District: %2d                       Date: %19.19s",
		       newOrder.w_id, newOrder.d_id, newOrder.o_entry_d);
	    fmt[2].format("Customer:    %4d  Name: %-16.16s   Credit: %2.2s   %%Disc: %5.2f",
		       newOrder.c_id, newOrder.c_last,
		       newOrder.c_credit, newOrder.c_discount * 100.0);
	    fmt[3].format("Order Number:  %8d  Number of Lines: %2d        W_tax: %5.2f   D_tax: %5.2f",
		       newOrder.o_id, newOrder.o_ol_cnt,
		       newOrder.w_tax * 100.0, newOrder.d_tax * 100.0);

	    fmt[5].format("Supp_W   Item_Id  Item Name                  Qty  Stock  B/G  Price    Amount");

	    for (int i = 0; i < 15; i++)
	    {
		if (newOrder.ol_i_id[i] != 0)
		    fmt[6 + i].format("%6d   %6d   %-24.24s   %2d    %3d    %1.1s   $%6.2f  $%7.2f",
			       newOrder.ol_supply_w_id[i],
			       newOrder.ol_i_id[i], newOrder.i_name[i],
			       newOrder.ol_quantity[i],
			       newOrder.s_quantity[i],
			       newOrder.brand_generic[i],
			       newOrder.i_price[i],
			       newOrder.ol_amount[i]);
	    }

	    fmt[21].format("Execution Status: %-24.24s                    Total:  $%8.2f",
		       newOrder.execution_status, newOrder.total_amount);

	    synchronized(trace_terminal_lock)
	    {
		log.trace("monkey-{}, +------------------------------------------------------------------------------+", this.m_id);
		for (int i = 0; i < 22; i++)
		{
		    log.trace("monkey-{}, {}", this.m_id, sb[i].toString());
		    sb[i].setLength(0);
		}
		log.trace("monkey-{}, +------------------------------------------------------------------------------+", this.m_id);
	    }
	    }
	}

	private void generatePayment(jTPCCTData tdata)
	{
	    jTPCCTData.PaymentData screen = tdata.PaymentData();

	    // 2.5.1.1 - w_id = terminal's w_id
	    screen.w_id = tdata.term_w_id;

	    // 2.5.1.2 - d_id = random [1..10]
	    screen.d_id = rnd.nextInt(1, 10);

	    // 2.5.1.2 - in 85% of cases (c_d_id, c_w_id) = (d_id, w_id)
	    //		 in 15% of cases they are randomly chosen.
	    if (rnd.nextInt(1, 100) <= 85)
	    {
		screen.c_d_id = screen.d_id;
		screen.c_w_id = screen.w_id;
	    }
	    else
	    {
		screen.c_d_id = rnd.nextInt(1, 10);
		do
		{
		    screen.c_w_id = rnd.nextInt(1, jTPCC.numWarehouses);
		}
		while (screen.c_w_id == tdata.term_w_id && jTPCC.numWarehouses > 1);
	    }

	    // 2.5.1.2 - in 60% of cases customer is selected by last name,
	    //		 in 40% of cases by customer ID.
	    if (rnd.nextInt(1, 100) <= 60)
	    {
		screen.c_id = 0;
		screen.c_last = rnd.getCLast();
	    }
	    else
	    {
		screen.c_id = rnd.getCustomerID();
		screen.c_last = null;
	    }

	    // 2.5.1.3 - h_amount = random [1.00 .. 5,000.00]
	    screen.h_amount = ((double)rnd.nextLong(100, 500000)) / 100.0;

	    tdata.payment = screen;
	}

	private void processPaymentResult(jTPCCTData tdata)
	{
	    jTPCCTData.PaymentData payment = tdata.payment;

	    tdata.payment = null;
	    if (log.isDebugEnabled()) {

	    StringBuffer    sb[] = new StringBuffer[23];
	    Formatter       fmt[] = new Formatter[23];
	    for (int i = 0; i < 23; i++)
	    {
		sb[i] = new StringBuffer();
		fmt[i] = new Formatter(sb[i]);
	    }

	    // PAYMENT OUTPUT screen
	    fmt[0].format("                                   Payment");
	    fmt[1].format("Date: %-19.19s", payment.h_date);
	    fmt[3].format("Warehouse: %6d                         District: %2d",
			  payment.w_id, payment.d_id);
	    fmt[4].format("%-20.20s                      %-20.20s",
			  payment.w_street_1, payment.d_street_1);
	    fmt[5].format("%-20.20s                      %-20.20s",
			  payment.w_street_2, payment.d_street_2);
	    fmt[6].format("%-20.20s %2.2s %5.5s-%4.4s        %-20.20s %2.2s %5.5s-%4.4s",
			  payment.w_city, payment.w_state,
			  payment.w_zip.substring(0, 5), payment.w_zip.substring(5, 9),
			  payment.d_city, payment.d_state,
			  payment.d_zip.substring(0, 5), payment.d_zip.substring(5, 9));

	    fmt[8].format("Customer: %4d  Cust-Warehouse: %6d  Cust-District: %2d",
			  payment.c_id, payment.c_w_id, payment.c_d_id);
	    fmt[9].format("Name:   %-16.16s %2.2s %-16.16s       Since:  %-10.10s",
			  payment.c_first, payment.c_middle, payment.c_last,
			  payment.c_since);
	    fmt[10].format("        %-20.20s                       Credit: %2s",
			   payment.c_street_1, payment.c_credit);
	    fmt[11].format("        %-20.20s                       %%Disc:  %5.2f",
			   payment.c_street_2, payment.c_discount * 100.0);
	    fmt[12].format("        %-20.20s %2.2s %5.5s-%4.4s         Phone:  %6.6s-%3.3s-%3.3s-%4.4s",
			   payment.c_city, payment.c_state,
			   payment.c_zip.substring(0, 5), payment.c_zip.substring(5, 9),
			   payment.c_phone.substring(0, 6), payment.c_phone.substring(6, 9),
			   payment.c_phone.substring(9, 12), payment.c_phone.substring(12, 16));

	    fmt[14].format("Amount Paid:          $%7.2f        New Cust-Balance: $%14.2f",
			   payment.h_amount, payment.c_balance);
	    fmt[15].format("Credit Limit:   $%13.2f", payment.c_credit_lim);
	    if (payment.c_data.length() >= 200)
	    {
		fmt[17].format("Cust-Data: %-50.50s", payment.c_data.substring(0, 50));
		fmt[18].format("           %-50.50s", payment.c_data.substring(50, 100));
		fmt[19].format("           %-50.50s", payment.c_data.substring(100, 150));
		fmt[20].format("           %-50.50s", payment.c_data.substring(150, 200));
	    }
	    else
	    {
		fmt[17].format("Cust-Data:");
	    }

	    synchronized(trace_terminal_lock)
	    {
		log.trace("monkey-{}, +------------------------------------------------------------------------------+", this.m_id);
		for (int i = 0; i < 21; i++)
		{
		    log.trace("monkey-{}, {}", this.m_id, sb[i].toString());
		    sb[i].setLength(0);
		}
		log.trace("monkey-{}, +------------------------------------------------------------------------------+", this.m_id);
	    }
	    }
	}

	/*
	 * ORDER_STATUS
	 */
	private void generateOrderStatus(jTPCCTData tdata)
	{
	    jTPCCTData.OrderStatusData screen = tdata.OrderStatusData();

	    // 2.6.1.1 - w_id = terminal's w_id
	    screen.w_id = tdata.term_w_id;

	    // 2.6.1.2 - d_id is random [1..10]
	    screen.d_id = rnd.nextInt(1, 10);

	    // 2.6.1.2 - in 60% of cases customer is selected by last name,
	    //		 in 40% of cases by customer ID.
	    if (rnd.nextInt(1, 100) <= 60)
	    {
		screen.c_id = 0;
		screen.c_last = rnd.getCLast();
	    }
	    else
	    {
		screen.c_id = rnd.getCustomerID();
		screen.c_last = null;
	    }

	    tdata.order_status = screen;
	}

	private void processOrderStatusResult(jTPCCTData tdata)
	{
	    jTPCCTData.OrderStatusData orderStatus = tdata.order_status;

	    tdata.order_status = null;
	    if (log.isDebugEnabled()) {

	    StringBuffer    sb[] = new StringBuffer[23];
	    Formatter       fmt[] = new Formatter[23];
	    for (int i = 0; i < 23; i++)
	    {
		sb[i] = new StringBuffer();
		fmt[i] = new Formatter(sb[i]);
	    }

	    // ORDER_STATUS OUTPUT screen
	    fmt[0].format("                                  Order Status");
	    fmt[1].format("Warehouse: %6d   District: %2d",
			  orderStatus.w_id, orderStatus.d_id);
	    fmt[2].format("Customer: %4d   Name: %-16.16s %2.2s %-16.16s",
			  orderStatus.c_id, orderStatus.c_first,
			  orderStatus.c_middle, orderStatus.c_last);
	    fmt[3].format("Cust-Balance: $%13.2f", orderStatus.c_balance);

	    if (orderStatus.o_carrier_id >= 0)
		fmt[5].format("Order-Number: %8d   Entry-Date: %-19.19s   Carrier-Number: %2d",
			      orderStatus.o_id, orderStatus.o_entry_d, orderStatus.o_carrier_id);
	    else
		fmt[5].format("Order-Number: %8d   Entry-Date: %-19.19s   Carrier-Number:",
			      orderStatus.o_id, orderStatus.o_entry_d);
	    fmt[6].format("Suppy-W      Item-Id     Qty    Amount        Delivery-Date");
	    for (int i = 0; i < 15 && orderStatus.ol_i_id[i] > 0; i++)
	    {
		fmt[7 + i].format(" %6d      %6d     %3d     $%8.2f     %-10.10s",
				  orderStatus.ol_supply_w_id[i],
				  orderStatus.ol_i_id[i],
				  orderStatus.ol_quantity[i],
				  orderStatus.ol_amount[i],
				  (orderStatus.ol_delivery_d[i] == null) ? "" :
				      orderStatus.ol_delivery_d[i]);
	    }

	    synchronized(trace_terminal_lock)
	    {
		log.trace("monkey-{}, +------------------------------------------------------------------------------+", this.m_id);
		for (int i = 0; i < 22; i++)
		{
		    log.trace("monkey-{}, {}", this.m_id ,sb[i].toString());
		    sb[i].setLength(0);
		}
		log.trace("monkey-{}, +------------------------------------------------------------------------------+", this.m_id);
	    }
	    }
	}

	/*
	 * DELIVERY
	 */
	private void generateDelivery(jTPCCTData tdata)
	{
	    jTPCCTData.DeliveryData screen = tdata.DeliveryData();

	    // 2.7.1.1 - w_id = terminal's w_id
	    screen.w_id = tdata.term_w_id;

	    // 2.7.1.2 - o_carrier_id = random [1..10]
	    screen.o_carrier_id = rnd.nextInt(1, 10);

	    tdata.delivery = screen;
	}

	private void processDeliveryResult(jTPCCTData tdata)
	{
	    jTPCCTData.DeliveryData screen = tdata.delivery;

	    tdata.delivery = null;
	    if (log.isDebugEnabled()) {

	    synchronized(trace_terminal_lock)
	    {
		log.trace("monkey-{}, +------------------------------------------------------------------------------+");
		log.trace("monkey-{},                                   Delivery", this.m_id);
		log.trace("monkey-{}, Warehouse:	      {}", this.m_id, screen.w_id);
		log.trace("monkey-{}, Carrier Number:   {}", this.m_id, screen.o_carrier_id);
		log.trace("monkey-{}, ", this.m_id);
		log.trace("monkey-{}, Execution Status: {}", this.m_id, screen.execution_status);
		log.trace("monkey-{}, +------------------------------------------------------------------------------+", this.m_id);
	    }
	    }
	}

	/*
	 * STOCK_LEVEL
	 */
	private void generateStockLevel(jTPCCTData tdata)
	{
	    jTPCCTData.StockLevelData screen = tdata.StockLevelData();

	    screen.w_id = tdata.term_w_id;
	    screen.d_id = tdata.term_d_id;
	    screen.threshold = rnd.nextInt(10, 20);

	    tdata.stock_level = screen;
	}

	private void processStockLevelResult(jTPCCTData tdata)
	{
	    jTPCCTData.StockLevelData screen = tdata.stock_level;

	    tdata.stock_level = null;
	    if (log.isDebugEnabled()) {

	    synchronized(trace_terminal_lock)
	    {
		log.trace("monkey-{}, +------------------------------------------------------------------------------+", this.m_id);
		log.trace("monkey-{},                                  Stock Level", this.m_id);
		log.trace("monkey-{}, Warehouse: {}", this.m_id, screen.w_id);
		log.trace("monkey-{}, District:  {}", this.m_id, screen.d_id);
		log.trace("monkey-{}, ", this.m_id);
		log.trace("monkey-{}, Stock Level Threshold: {}", this.m_id, screen.threshold);
		log.trace("monkey-{}, Low Stock Count:	   {}", this.m_id, screen.low_stock);
		log.trace("monkey-{}, +------------------------------------------------------------------------------+");
	    }
	    }
	}

	private int nextTransactionType()
	{
	    double chance = randomDouble() * 100.0;

	    if (chance <= jTPCC.paymentWeight)
		return jTPCCTData.TT_PAYMENT;
	    chance -= jTPCC.paymentWeight;

	    if (chance <= jTPCC.orderStatusWeight)
		return jTPCCTData.TT_ORDER_STATUS;
	    chance -= jTPCC.orderStatusWeight;

	    if (chance <= jTPCC.stockLevelWeight)
		return jTPCCTData.TT_STOCK_LEVEL;
	    chance -= jTPCC.stockLevelWeight;

	    if (chance <= jTPCC.deliveryWeight)
		return jTPCCTData.TT_DELIVERY;

	    return jTPCCTData.TT_NEW_ORDER;
	}

	private long randomInt(long min, long max)
	{
	    return (long)(this.random.nextDouble() * (max - min + 1) + min);
	}

	private double randomDouble()
	{
	    return this.random.nextDouble();
	}

    }
}
