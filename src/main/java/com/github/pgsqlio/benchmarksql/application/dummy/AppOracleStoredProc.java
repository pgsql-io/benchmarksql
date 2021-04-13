package com.github.pgsqlio.benchmarksql.application.dummy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.pgsqlio.benchmarksql.jtpcc.jTPCC;
import com.github.pgsqlio.benchmarksql.jtpcc.jTPCCApplication;
import com.github.pgsqlio.benchmarksql.jtpcc.jTPCCTData;

/**
 * AppOracleStoredProc - TPC-C Transaction Implementation for using Stored Procedures on Oracle
 */
public class AppOracleStoredProc extends jTPCCApplication {
  private static Logger log = LogManager.getLogger(AppOracleStoredProc.class);
  private jTPCC gdata;
  private int sut_id;

  public void init(jTPCC gdata, int sut_id) throws Exception {
    throw new Exception(
        "Oracle support was not compiled in - please rebuild BenchmarkSQL with -DOracleSupport=true");
  }

  public void finish() throws Exception {}

  public void executeNewOrder(jTPCCTData.NewOrderData newOrder, boolean trans_rbk)
      throws Exception {}

  public void executePayment(jTPCCTData.PaymentData payment) throws Exception {}

  public void executeOrderStatus(jTPCCTData.OrderStatusData orderStatus) throws Exception {}

  public void executeStockLevel(jTPCCTData.StockLevelData stockLevel) throws Exception {}

  public void executeDeliveryBG(jTPCCTData.DeliveryBGData deliveryBG) throws Exception {}
}
