package com.github.pgsqlio.benchmarksql.loader;

public class LoadJob {
  public static final int LOAD_ITEM = 1;
  public static final int LOAD_WAREHOUSE = 2;
  public static final int LOAD_ORDER = 3;

  public int type;

  public int w_id;
  public int d_id;
  public int c_id;
  public int o_id;
};
