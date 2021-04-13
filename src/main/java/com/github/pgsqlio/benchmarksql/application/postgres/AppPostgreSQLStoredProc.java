package com.github.pgsqlio.benchmarksql.application.postgres;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.pgsqlio.benchmarksql.jtpcc.jTPCC;
import com.github.pgsqlio.benchmarksql.jtpcc.jTPCCApplication;
import com.github.pgsqlio.benchmarksql.jtpcc.jTPCCTData;

/**
 * AppPostgreSQLStoredProc - TPC-C Transaction Implementation for using Stored Procedures on
 * PostgreSQL
 */
public class AppPostgreSQLStoredProc extends jTPCCApplication {
  private static Logger log = LogManager.getLogger(AppPostgreSQLStoredProc.class);
  private jTPCC gdata;
  private int sut_id;

  private Connection dbConn;

  public PreparedStatement stmtNewOrderStoredProc;
  public PreparedStatement stmtPaymentStoredProc;
  public PreparedStatement stmtOrderStatusStoredProc;
  public PreparedStatement stmtStockLevelStoredProc;
  public PreparedStatement stmtDeliveryBGStoredProc;


  public void init(jTPCC gdata, int sut_id) throws Exception {
    Properties dbProps;

    this.gdata = gdata;
    this.sut_id = sut_id;

    // Connect to the database
    dbProps = new Properties();
    dbProps.setProperty("user", gdata.iUser);
    dbProps.setProperty("password", gdata.iPassword);
    dbConn = DriverManager.getConnection(gdata.iConn, dbProps);
    dbConn.setAutoCommit(false);

    // PreparedStataments for NEW_ORDER
    stmtNewOrderStoredProc =
        dbConn.prepareStatement("SELECT * FROM bmsql_proc_new_order (?, ?, ?, ?, ?, ?)");

    // PreparedStatements for PAYMENT
    stmtPaymentStoredProc =
        dbConn.prepareStatement("SELECT * FROM bmsql_proc_payment (?, ?, ?, ?, ?, ?, ?)");

    // PreparedStatements for ORDER_STATUS
    stmtOrderStatusStoredProc =
        dbConn.prepareStatement("SELECT * FROM bmsql_proc_order_status (?, ?, ?, ?)");

    // PreparedStatement for STOCK_LEVEL
    stmtStockLevelStoredProc =
        dbConn.prepareStatement("SELECT * FROM bmsql_proc_stock_level (?, ?, ?)");

    // PreparedStatements for DELIVERY_BG
    stmtDeliveryBGStoredProc =
        dbConn.prepareStatement("SELECT * FROM bmsql_proc_delivery_bg(?, ?, ?)");

    dbConn.commit();
  }

  public void finish() throws Exception {
    if (dbConn != null) {
      dbConn.close();
      dbConn = null;
    }
  }

  public void executeNewOrder(jTPCCTData.NewOrderData newOrder, boolean trans_rbk)
      throws Exception {
    PreparedStatement stmt;
    ResultSet rs;

    Integer[] ol_supply_w_id = new Integer[15];
    Integer[] ol_i_id = new Integer[15];
    Integer[] ol_quantity = new Integer[15];

    for (int i = 0; i < 15; i++) {
      ol_supply_w_id[i] = newOrder.ol_supply_w_id[i];
      ol_i_id[i] = newOrder.ol_i_id[i];
      ol_quantity[i] = newOrder.ol_quantity[i];
    }

    try {
      // Execute the stored procedure for NEW_ORDER
      stmt = stmtNewOrderStoredProc;
      stmt.setInt(1, newOrder.w_id);
      stmt.setInt(2, newOrder.d_id);
      stmt.setInt(3, newOrder.c_id);
      stmt.setArray(4, dbConn.createArrayOf("integer", ol_supply_w_id));
      stmt.setArray(5, dbConn.createArrayOf("integer", ol_i_id));
      stmt.setArray(6, dbConn.createArrayOf("integer", ol_quantity));
      rs = stmt.executeQuery();

      // The stored proc succeeded. Extract the results.
      rs.next();

      newOrder.w_tax = rs.getDouble("out_w_tax");
      newOrder.d_tax = rs.getDouble("out_d_tax");
      newOrder.o_id = rs.getInt("out_o_id");
      newOrder.o_entry_d = rs.getTimestamp("out_o_entry_d").toString();
      newOrder.o_ol_cnt = rs.getInt("out_ol_cnt");
      newOrder.total_amount = rs.getDouble("out_total_amount");
      newOrder.c_last = rs.getString("out_c_last");
      newOrder.c_credit = rs.getString("out_c_credit");
      newOrder.c_discount = rs.getDouble("out_c_discount");

      Array arr_ol_amount = rs.getArray("out_ol_amount");
      Array arr_i_name = rs.getArray("out_i_name");
      Array arr_i_price = rs.getArray("out_i_price");
      Array arr_s_quantity = rs.getArray("out_s_quantity");
      Array arr_bg = rs.getArray("out_brand_generic");
      BigDecimal[] ol_amount = (BigDecimal[]) arr_ol_amount.getArray();
      String[] i_name = (String[]) arr_i_name.getArray();
      BigDecimal[] i_price = (BigDecimal[]) arr_i_price.getArray();
      Integer[] s_quantity = (Integer[]) arr_s_quantity.getArray();
      String[] bg = (String[]) arr_bg.getArray();

      for (int i = 0; i < ol_amount.length; i++) {
        newOrder.ol_amount[i] = ol_amount[i].doubleValue();
        newOrder.i_name[i] = i_name[i];
        newOrder.i_price[i] = i_price[i].doubleValue();
        newOrder.s_quantity[i] = s_quantity[i];
        newOrder.brand_generic[i] = bg[i];
      }

      newOrder.execution_status = new String("Order placed");

      rs.close();
      dbConn.commit();
    } catch (SQLException se) {
      boolean expected = false;

      if (trans_rbk && se.getMessage().startsWith("ERROR: Item number is not valid")) {
        newOrder.execution_status = new String("Item number is not valid");
        expected = true;
      } else {
        log.error("Unexpected SQLException in NEW_ORDER");
        log.error("message: {} trans_rbk={}", se.getMessage(), trans_rbk);
        for (SQLException x = se; x != null; x = x.getNextException())
          log.error(x.getMessage());
        log.info(se);
      }

      try {
        dbConn.rollback();
      } catch (SQLException se2) {
        throw new Exception("Unexpected SQLException on rollback: " + se2.getMessage());
      }

      if (!expected)
        throw se;
    } catch (Exception e) {
      try {
        dbConn.rollback();
      } catch (SQLException se2) {
        throw new Exception("Unexpected SQLException on rollback: " + se2.getMessage());
      }
      throw e;
    }
  }

  public void executePayment(jTPCCTData.PaymentData payment) throws Exception {
    PreparedStatement stmt;
    ResultSet rs;

    try {
      stmt = stmtPaymentStoredProc;
      stmt.setInt(1, payment.w_id);
      stmt.setInt(2, payment.d_id);
      stmt.setInt(3, payment.c_id);
      stmt.setInt(4, payment.c_d_id);
      stmt.setInt(5, payment.c_w_id);
      stmt.setString(6, payment.c_last);
      stmt.setBigDecimal(7, new BigDecimal(payment.h_amount));
      rs = stmt.executeQuery();

      // The stored proc succeeded. Extract the results.
      rs.next();

      payment.w_name = rs.getString("out_w_name");
      payment.w_street_1 = rs.getString("out_w_street_1");
      payment.w_street_2 = rs.getString("out_w_street_2");
      payment.w_city = rs.getString("out_w_city");
      payment.w_state = rs.getString("out_w_state");
      payment.w_zip = rs.getString("out_w_zip");
      payment.d_name = rs.getString("out_d_name");
      payment.d_street_1 = rs.getString("out_d_street_1");
      payment.d_street_2 = rs.getString("out_d_street_2");
      payment.d_city = rs.getString("out_d_city");
      payment.d_state = rs.getString("out_d_state");
      payment.d_zip = rs.getString("out_d_zip");
      payment.c_id = rs.getInt("in_c_id");
      payment.c_first = rs.getString("out_c_first");
      payment.c_middle = rs.getString("out_c_middle");
      payment.c_street_1 = rs.getString("out_c_street_1");
      payment.c_street_2 = rs.getString("out_c_street_2");
      payment.c_city = rs.getString("out_c_city");
      payment.c_state = rs.getString("out_c_state");
      payment.c_zip = rs.getString("out_c_zip");
      payment.c_phone = rs.getString("out_c_phone");
      payment.c_since = rs.getTimestamp("out_c_since").toString();
      payment.c_credit = rs.getString("out_c_credit");
      payment.c_credit_lim = rs.getDouble("out_c_credit_lim");
      payment.c_discount = rs.getDouble("out_c_discount");
      payment.c_balance = rs.getDouble("out_c_balance");
      payment.c_data = rs.getString("out_c_data");
      payment.h_date = rs.getTimestamp("out_h_date").toString();

      rs.close();
      dbConn.commit();
    } catch (SQLException se) {
      log.error("Unexpected SQLException in PAYMENT");
      log.error("message: {}", se.getMessage());
      for (SQLException x = se; x != null; x = x.getNextException())
        log.error(x.getMessage());
      log.info(se);
      try {
        dbConn.rollback();
      } catch (SQLException se2) {
        throw new Exception("Unexpected SQLException on rollback: " + se2.getMessage());
      }

      throw se;
    } catch (Exception e) {
      try {
        dbConn.rollback();
      } catch (SQLException se2) {
        throw new Exception("Unexpected SQLException on rollback: " + se2.getMessage());
      }
      throw e;
    }
  }

  public void executeOrderStatus(jTPCCTData.OrderStatusData orderStatus) throws Exception {
    PreparedStatement stmt;
    ResultSet rs;

    try {
      // Execute the stored procedure for ORDER_STATUS
      stmt = stmtOrderStatusStoredProc;
      stmt.setInt(1, orderStatus.w_id);
      stmt.setInt(2, orderStatus.d_id);
      stmt.setInt(3, orderStatus.c_id);
      stmt.setString(4, orderStatus.c_last);
      rs = stmt.executeQuery();

      // The stored proc succeeded Extract the results.
      rs.next();

      orderStatus.c_first = rs.getString("out_c_first");
      orderStatus.c_middle = rs.getString("out_c_middle");
      orderStatus.c_balance = rs.getDouble("out_c_balance");
      orderStatus.o_id = rs.getInt("out_o_id");
      orderStatus.o_entry_d = rs.getTimestamp("out_o_entry_d").toString();
      orderStatus.o_carrier_id = rs.getInt("out_o_carrier_id");
      orderStatus.c_id = rs.getInt("in_c_id");
      Array arr_ol_supply_w_id = rs.getArray("out_ol_supply_w_id");
      Array arr_ol_i_id = rs.getArray("out_ol_i_id");
      Array arr_ol_quantity = rs.getArray("out_ol_quantity");
      Array arr_ol_amount = rs.getArray("out_ol_amount");
      Array arr_ol_delivery_d = rs.getArray("out_ol_delivery_d");
      Integer[] ol_i_id = (Integer[]) arr_ol_i_id.getArray();
      Integer[] ol_supply_w_id = (Integer[]) arr_ol_supply_w_id.getArray();
      Integer[] ol_quantity = (Integer[]) arr_ol_quantity.getArray();
      BigDecimal[] ol_amount = (BigDecimal[]) arr_ol_amount.getArray();
      Timestamp[] ol_delivery_x = (Timestamp[]) arr_ol_delivery_d.getArray();

      for (int i = 0; i < ol_amount.length; i++) {
        orderStatus.ol_supply_w_id[i] = ol_supply_w_id[i];
        orderStatus.ol_i_id[i] = ol_i_id[i];
        orderStatus.ol_quantity[i] = ol_quantity[i];
        orderStatus.ol_amount[i] = ol_amount[i].doubleValue();
        if (ol_delivery_x[i] == null)
          orderStatus.ol_delivery_d[i] = " ";
        else
          orderStatus.ol_delivery_d[i] = ol_delivery_x[i].toString();
      }

      rs.close();
      dbConn.commit();

    } catch (SQLException se) {
      log.error("Unexpected SQLException in ORDER_STATUS");
      log.error("message: {}", se.getMessage());
      for (SQLException x = se; x != null; x = x.getNextException())
        log.error(x.getMessage());
      log.info(se);

      try {
        dbConn.rollback();
      } catch (SQLException se2) {
        throw new Exception("Unexpected SQLException on rollback: " + se2.getMessage());
      }

      throw se;
    } catch (Exception e) {
      try {
        dbConn.rollback();
      } catch (SQLException se2) {
        throw new Exception("Unexpected SQLException on rollback: " + se2.getMessage());
      }
      throw e;
    }
  }

  public void executeStockLevel(jTPCCTData.StockLevelData stockLevel) throws Exception {
    PreparedStatement stmt;
    ResultSet rs;

    try {
      // Execute the stored procedure for STOCK_LEVEL
      stmt = stmtStockLevelStoredProc;
      stmt.setInt(1, stockLevel.w_id);
      stmt.setInt(2, stockLevel.d_id);
      stmt.setInt(3, stockLevel.threshold);
      rs = stmt.executeQuery();

      // The stored proc succeeded. Extract the results.
      rs.next();

      stockLevel.low_stock = rs.getInt("out_low_stock");

      rs.close();
      dbConn.commit();
    } catch (SQLException se) {

      log.error("Unexpected SQLException in NEW_ORDER");
      log.error("message: {}", se.getMessage());
      for (SQLException x = se; x != null; x = x.getNextException())
        log.error(x.getMessage());
      log.info(se);

      try {
        dbConn.rollback();
      } catch (SQLException se2) {
        throw new Exception("Unexpected SQLException on rollback: " + se2.getMessage());
      }

      throw se;
    } catch (Exception e) {
      try {
        dbConn.rollback();
      } catch (SQLException se2) {
        throw new Exception("Unexpected SQLException on rollback: " + se2.getMessage());
      }
      throw e;
    }
  }

  public void executeDeliveryBG(jTPCCTData.DeliveryBGData deliveryBG) throws Exception {
    PreparedStatement stmt;
    ResultSet rs;

    try {
      // Execute the stored procedure for DELIVERY_BG
      stmt = stmtDeliveryBGStoredProc;
      stmt.setInt(1, deliveryBG.w_id);
      stmt.setInt(2, deliveryBG.o_carrier_id);
      stmt.setTimestamp(3, Timestamp.valueOf(deliveryBG.ol_delivery_d));
      rs = stmt.executeQuery();

      // The stored proc succeeded. Extract the results.
      rs.next();

      Array arr_delivered_o_id = rs.getArray("out_delivered_o_id");

      if (arr_delivered_o_id != null) {
        Integer[] delivered_o_id = (Integer[]) arr_delivered_o_id.getArray();
        deliveryBG.delivered_o_id = new int[delivered_o_id.length];

        for (int i = 0; i < delivered_o_id.length; i++)
          deliveryBG.delivered_o_id[i] = delivered_o_id[i];
      }

      rs.close();
      dbConn.commit();
    } catch (SQLException se) {
      log.error("Unexpected SQLException in DELIVERY_BG");
      log.error("message: {}", se.getMessage());
      for (SQLException x = se; x != null; x = x.getNextException())
        log.error(x.getMessage());
      log.info(se);
      try {
        dbConn.rollback();
      } catch (SQLException se2) {
        throw new Exception("Unexpected SQLException on rollback: " + se2.getMessage());
      }

      throw se;
    } catch (Exception e) {
      try {
        dbConn.rollback();
      } catch (SQLException se2) {
        throw new Exception("Unexpected SQLException on rollback: " + se2.getMessage());
      }
      throw e;
    }
  }
}
