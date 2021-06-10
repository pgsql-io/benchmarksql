package com.github.pgsqlio.benchmarksql.loader;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Formatter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.pgsqlio.benchmarksql.jtpcc.jTPCC;
import com.github.pgsqlio.benchmarksql.jtpcc.jTPCCRandom;

/**
 * LoadData - Load Sample Data directly into database tables or into CSV files using multiple
 * parallel workers.
 *
 * Copyright (C) 2016, Denis Lussier Copyright (C) 2016, Jan Wieck
 */
public class LoadData {
  private static Logger log = LogManager.getLogger(LoadData.class);
  private static StringBuffer sb = new StringBuffer();
  private static Formatter fmt = new Formatter(sb);

  private static Properties ini = new Properties();
  private static String db;
  private static Properties dbProps;
  private static jTPCCRandom rnd;
  private static String fileLocation = null;
  private static String csvNullValue = null;

  private static int numWarehouses;
  private static int numWorkers;

  private static boolean loadingItemDone = false;
  private static boolean loadingWarehouseDone = false;
  private static int nextWIDX = 0;
  private static int nextDIDX = 0;
  private static int nextOIDX = 0;
  private static int nextCID[][][];
  private static Object nextJobLock = new Object();

  private static LoadDataWorker[] workers;
  private static Thread[] workerThreads;

  private static String[] argv;

  private static boolean writeCSV = false;
  private static BufferedWriter configCSV = null;
  private static BufferedWriter itemCSV = null;
  private static BufferedWriter warehouseCSV = null;
  private static BufferedWriter districtCSV = null;
  private static BufferedWriter stockCSV = null;
  private static BufferedWriter customerCSV = null;
  private static BufferedWriter historyCSV = null;
  private static BufferedWriter orderCSV = null;
  private static BufferedWriter orderLineCSV = null;
  private static BufferedWriter newOrderCSV = null;

  public static void main(String[] args) {
    int i;

    log.info("Starting BenchmarkSQL LoadData");
    log.info("");

    /*
     * Load the Benchmark properties file.
     */
    try {
      ini.load(new FileInputStream(System.getProperty("prop")));
    } catch (IOException e) {
      log.error("ERROR: {}", e.getMessage());
      System.exit(1);
    }
    argv = args;

    /*
     * Initialize the global Random generator that picks the C values for the load.
     */
    rnd = new jTPCCRandom();

    /*
     * Load the JDBC driver and prepare the db and dbProps.
     */
    try {
      Class.forName(iniGetString("driver"));
    } catch (Exception e) {
      log.error("ERROR: cannot load JDBC driver - {}", e.getMessage());
      System.exit(1);
    }
    db = iniGetString("conn");
    dbProps = new Properties();
    dbProps.setProperty("user", iniGetString("user"));
    dbProps.setProperty("password", iniGetString("password"));

    /*
     * Parse other vital information from the props file.
     */
    numWarehouses = iniGetInt("warehouses");
    numWorkers = iniGetInt("loadWorkers", 4);
    fileLocation = iniGetString("fileLocation");
    csvNullValue = iniGetString("csvNullValue", "NULL");

    /*
     * If CSV files are requested, open them all.
     */
    if (fileLocation != null) {
      writeCSV = true;

      try {
        configCSV = new BufferedWriter(new FileWriter(fileLocation + "config.csv"));
        itemCSV = new BufferedWriter(new FileWriter(fileLocation + "item.csv"));
        warehouseCSV = new BufferedWriter(new FileWriter(fileLocation + "warehouse.csv"));
        districtCSV = new BufferedWriter(new FileWriter(fileLocation + "district.csv"));
        stockCSV = new BufferedWriter(new FileWriter(fileLocation + "stock.csv"));
        customerCSV = new BufferedWriter(new FileWriter(fileLocation + "customer.csv"));
        historyCSV = new BufferedWriter(new FileWriter(fileLocation + "cust-hist.csv"));
        orderCSV = new BufferedWriter(new FileWriter(fileLocation + "order.csv"));
        orderLineCSV = new BufferedWriter(new FileWriter(fileLocation + "order-line.csv"));
        newOrderCSV = new BufferedWriter(new FileWriter(fileLocation + "new-order.csv"));
      } catch (IOException ie) {
        log.error(ie.getMessage());
        System.exit(3);
      }
    }

    log.info("");

    /*
     * Initialize the random nextCID arrays (one per District) used in getNextJob()
     *
     * For the ORDER rows the TPC-C specification demands that they are generated using a random
     * permutation of all 3,000 customers. To do that we set up an array per district with all
     * C_IDs and randomly shuffle each.
     */
    nextCID = new int[numWarehouses][10][3000];
    for (int w_idx = 0; w_idx < numWarehouses; w_idx++) {
      for (int d_idx = 0; d_idx < 10; d_idx++) {
        for (int c_idx = 0; c_idx < 3000; c_idx++) {
          nextCID[w_idx][d_idx][c_idx] = c_idx + 1;
        }
        for (i = 0; i < 3000; i++) {
          int x = rnd.nextInt(0, 2999);
          int y = rnd.nextInt(0, 2999);
          int tmp = nextCID[w_idx][d_idx][x];
          nextCID[w_idx][d_idx][x] = nextCID[w_idx][d_idx][y];
          nextCID[w_idx][d_idx][y] = tmp;
        }
      }
    }

    /*
     * Create the number of requested workers and start them.
     */
    workers = new LoadDataWorker[numWorkers];
    workerThreads = new Thread[numWorkers];
    for (i = 0; i < numWorkers; i++) {
      Connection dbConn;

      try {
        dbConn = DriverManager.getConnection(db, dbProps);
        dbConn.setAutoCommit(false);
        if (writeCSV)
          workers[i] = new LoadDataWorker(i, csvNullValue, rnd.newRandom());
        else
          workers[i] = new LoadDataWorker(i, dbConn, rnd.newRandom());
        workerThreads[i] = new Thread(workers[i]);
        workerThreads[i].start();
      } catch (SQLException se) {
        log.error("ERROR: {}", se.getMessage());
        System.exit(3);
        return;
      }
    }

    for (i = 0; i < numWorkers; i++) {
      try {
        workerThreads[i].join();
      } catch (InterruptedException ie) {
        log.error("ERROR: worker {} - {}", i, ie.getMessage());
        System.exit(4);
      }
    }

    /*
     * Close the CSV files if we are writing them.
     */
    if (writeCSV) {
      try {
        configCSV.close();
        itemCSV.close();
        warehouseCSV.close();
        districtCSV.close();
        stockCSV.close();
        customerCSV.close();
        historyCSV.close();
        orderCSV.close();
        orderLineCSV.close();
        newOrderCSV.close();
      } catch (IOException ie) {
        log.error(ie.getMessage());
        System.exit(3);
      }
    }
  } // End of main()

  public static void configAppend(StringBuffer buf) throws IOException {
    synchronized (configCSV) {
      configCSV.write(buf.toString());
    }
    buf.setLength(0);
  }

  public static void itemAppend(StringBuffer buf) throws IOException {
    synchronized (itemCSV) {
      itemCSV.write(buf.toString());
    }
    buf.setLength(0);
  }

  public static void warehouseAppend(StringBuffer buf) throws IOException {
    synchronized (warehouseCSV) {
      warehouseCSV.write(buf.toString());
    }
    buf.setLength(0);
  }

  public static void districtAppend(StringBuffer buf) throws IOException {
    synchronized (districtCSV) {
      districtCSV.write(buf.toString());
    }
    buf.setLength(0);
  }

  public static void stockAppend(StringBuffer buf) throws IOException {
    synchronized (stockCSV) {
      stockCSV.write(buf.toString());
    }
    buf.setLength(0);
  }

  public static void customerAppend(StringBuffer buf) throws IOException {
    synchronized (customerCSV) {
      customerCSV.write(buf.toString());
    }
    buf.setLength(0);
  }

  public static void historyAppend(StringBuffer buf) throws IOException {
    synchronized (historyCSV) {
      historyCSV.write(buf.toString());
    }
    buf.setLength(0);
  }

  public static void orderAppend(StringBuffer buf) throws IOException {
    synchronized (orderCSV) {
      orderCSV.write(buf.toString());
    }
    buf.setLength(0);
  }

  public static void orderLineAppend(StringBuffer buf) throws IOException {
    synchronized (orderLineCSV) {
      orderLineCSV.write(buf.toString());
    }
    buf.setLength(0);
  }

  public static void newOrderAppend(StringBuffer buf) throws IOException {
    synchronized (newOrderCSV) {
      newOrderCSV.write(buf.toString());
    }
    buf.setLength(0);
  }

  public static LoadJob getNextJob() {
    synchronized (nextJobLock) {
      /*
       * The first job we kick off is loading the ITEM table.
       */
      if (!loadingItemDone) {
        LoadJob job = new LoadJob();
        job.type = LoadJob.LOAD_ITEM;

        loadingItemDone = true;

        return job;
      }

      /*
       * Load everything per warehouse except for the OORDER, ORDER_LINE and NEW_ORDER tables.
       */
      if (!loadingWarehouseDone) {
        if (nextWIDX <= numWarehouses) {
          LoadJob job = new LoadJob();
          job.type = LoadJob.LOAD_WAREHOUSE;
          job.w_id = nextWIDX + 1;
          nextWIDX += 1;

          if (nextWIDX >= numWarehouses) {
            nextWIDX = 0;
            loadingWarehouseDone = true;
          }

          return job;
        }
      }

      /*
       * Load the OORDER, ORDER_LINE and NEW_ORDER rows.
       *
       * This is a state machine that will return jobs for creating orders in advancing O_ID
       * numbers. Within them it will loop trough W_IDs and D_IDs. The C_IDs will be the ones
       * preloaded into the nextCID arrays when this loader was created.
       */
      if (nextOIDX < 3000) {
        LoadJob job = new LoadJob();

        if (nextOIDX % 100 == 0 && nextDIDX == 0 && nextWIDX == 0) {
          fmt.format("Loading Orders with O_ID %4d and higher", nextOIDX + 1);
          log.info(sb.toString());
          sb.setLength(0);
        }

        job.w_id = nextWIDX + 1;
        job.d_id = nextDIDX + 1;
        job.c_id = nextCID[nextWIDX][nextDIDX][nextOIDX];
        job.o_id = nextOIDX + 1;

        if (++nextDIDX >= 10) {
          nextDIDX = 0;
          if (++nextWIDX >= numWarehouses) {
            nextWIDX = 0;
            ++nextOIDX;

            if (nextOIDX >= 3000) {
              log.info("Loading Orders done");
            }
          }
        }

        fmt.format("Order %d,%d,%d,%d", job.o_id, job.w_id, job.d_id, job.c_id);
        // log.info(sb.toString());
        sb.setLength(0);
        return job;
      }

      /*
       * Nothing more to be done. Returning null signals to the worker to exit.
       */
      return null;
    }
  }

  public static int getNumWarehouses() {
    return numWarehouses;
  }

  private static String iniGetString(String name) {
    String strVal = null;

    for (int i = 0; i < argv.length - 1; i += 2) {
      if (name.toLowerCase().equals(argv[i].toLowerCase())) {
        strVal = argv[i + 1];
        break;
      }
    }

    if (strVal == null)
      strVal = ini.getProperty(name);

    if (strVal == null)
      log.warn("{} (not defined)", name);
    else if (name.equals("password"))
      log.info("{}=***********", name);
    else
      log.info("{}={}", name, strVal);
    return strVal;
  }

  private static String iniGetString(String name, String defVal) {
    String strVal = null;

    for (int i = 0; i < argv.length - 1; i += 2) {
      if (name.toLowerCase().equals(argv[i].toLowerCase())) {
        strVal = argv[i + 1];
        break;
      }
    }

    if (strVal == null)
      strVal = ini.getProperty(name);

    if (strVal == null) {
      log.warn("{} (not defined - using default '{}')", name, defVal);
      return defVal;
    } else if (name.equals("password"))
      log.info("{}=***********", name);
    else
      log.info("{}={}", name, strVal);
    return strVal;
  }

  private static int iniGetInt(String name) {
    String strVal = iniGetString(name);

    if (strVal == null)
      return 0;
    return Integer.parseInt(strVal);
  }

  private static int iniGetInt(String name, int defVal) {
    String strVal = iniGetString(name);

    if (strVal == null)
      return defVal;
    return Integer.parseInt(strVal);
  }
}
