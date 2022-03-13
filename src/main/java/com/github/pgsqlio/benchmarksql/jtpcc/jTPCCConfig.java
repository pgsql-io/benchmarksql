package com.github.pgsqlio.benchmarksql.jtpcc;

import java.text.SimpleDateFormat;

/**
 * jTPCCConfig - Basic configuration parameters for jTPCC
 */
public interface jTPCCConfig {
  public final static String JTPCCVERSION = "6.0";

  public final static int DB_UNKNOWN = 0, DB_ORACLE = 1, DB_POSTGRES = 2, DB_FIREBIRD = 3,
      DB_MARIADB = 4, DB_TSQL = 5, DB_BABELFISH = 6;

  public final static int NEW_ORDER = 1, PAYMENT = 2, ORDER_STATUS = 3, DELIVERY = 4,
      STOCK_LEVEL = 5;

  public final static String[] nameTokens =
      {"BAR", "OUGHT", "ABLE", "PRI", "PRES", "ESE", "ANTI", "CALLY", "ATION", "EING"};

  public final static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  public final static int configCommitCount = 10000; // commit every n records in LoadData

  public final static int configWhseCount = 10;
  public final static int configItemCount = 100000; // tpc-c std = 100,000
  public final static int configDistPerWhse = 10; // tpc-c std = 10
  public final static int configCustPerDist = 3000; // tpc-c std = 3,000
}
