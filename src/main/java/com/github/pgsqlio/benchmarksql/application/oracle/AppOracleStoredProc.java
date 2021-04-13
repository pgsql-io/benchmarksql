package com.github.pgsqlio.benchmarksql.application.oracle;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.pgsqlio.benchmarksql.jtpcc.jTPCC;
import com.github.pgsqlio.benchmarksql.jtpcc.jTPCCApplication;
import com.github.pgsqlio.benchmarksql.jtpcc.jTPCCTData;

import oracle.jdbc.OracleConnection;
import oracle.jdbc.OracleTypes;

/**
 * AppOracleStoredProc - TPC-C Transaction Implementation for using Stored Procedures on Oracle
 */
public class AppOracleStoredProc extends jTPCCApplication {
  private static Logger log = LogManager.getLogger(AppOracleStoredProc.class);
  private jTPCC gdata;
  private int sut_id;

  private Connection dbConn;

  public String stmtNewOrderStoredProc;
  public String stmtPaymentStoredProc;
  public String stmtOrderStatusStoredProc;
  public String stmtStockLevelStoredProc;
  public String stmtDeliveryBGStoredProc;


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

    // Statement for NEW_ORDER
    stmtNewOrderStoredProc = "{call tpccc_oracle.oracle_proc_new_order(?, ?, ?, ?, ?, ?, ?, ?, ?, ?"
        + ",?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";

    // Statement for PAYMENT
    stmtPaymentStoredProc = "{call tpccc_oracle.oracle_proc_payment(?, ?, ?, ?, ?, ?, ?, ?, ?, ?,"
        + " ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?," + " ?, ?, ?, ?, ?)}";

    // Statement for ORDER_STATUS
    stmtOrderStatusStoredProc = "{call tpccc_oracle.oracle_proc_order_status(?, ?, ?, "
        + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";

    // Statement for STOCK_LEVEL
    stmtStockLevelStoredProc = "{call tpccc_oracle.oracle_proc_stock_level(?, ?, ?, ?)}";

    // Statement for DELIVERY_BG
    stmtDeliveryBGStoredProc = "call tpccc_oracle.oracle_proc_delivery_bg(?, ?, ?, ?)";

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
    CallableStatement stmt;
    OracleConnection oConn = (OracleConnection) dbConn;

    try {
      // Execute the stored procedure for NEW_ORDER
      stmt = dbConn.prepareCall(stmtNewOrderStoredProc);

      int[] ol_supply_w_id = new int[15];
      int[] ol_i_id = new int[15];
      int[] ol_quantity = new int[15];

      for (int i = 0; i < 15; i++) {
        ol_supply_w_id[i] = newOrder.ol_supply_w_id[i];
        ol_i_id[i] = newOrder.ol_i_id[i];
        ol_quantity[i] = newOrder.ol_quantity[i];
      }

      Array ora_ol_supply_w_id = oConn.createOracleArray("INT_ARRAY", ol_supply_w_id);
      Array ora_ol_i_id = oConn.createOracleArray("INT_ARRAY", ol_i_id);
      Array ora_ol_quantity = oConn.createOracleArray("INT_ARRAY", ol_quantity);

      stmt.setInt(1, newOrder.w_id);
      stmt.setInt(2, newOrder.d_id);
      stmt.setInt(3, newOrder.c_id);
      stmt.setObject(4, ora_ol_supply_w_id);
      stmt.setObject(5, ora_ol_i_id);
      stmt.setObject(6, ora_ol_quantity);
      stmt.registerOutParameter(7, OracleTypes.ARRAY, "NUM_ARRAY");
      stmt.registerOutParameter(8, OracleTypes.ARRAY, "VARCHAR24_ARRAY");
      stmt.registerOutParameter(9, OracleTypes.ARRAY, "NUM_ARRAY");
      stmt.registerOutParameter(10, OracleTypes.ARRAY, "INT_ARRAY");
      stmt.registerOutParameter(11, OracleTypes.ARRAY, "CHAR_ARRAY");
      stmt.registerOutParameter(12, Types.DECIMAL);
      stmt.registerOutParameter(13, Types.DECIMAL);
      stmt.registerOutParameter(14, Types.INTEGER);
      stmt.registerOutParameter(15, Types.TIMESTAMP);
      stmt.registerOutParameter(16, Types.INTEGER);
      stmt.registerOutParameter(17, Types.DECIMAL);
      stmt.registerOutParameter(18, Types.VARCHAR);
      stmt.registerOutParameter(19, Types.VARCHAR);
      stmt.registerOutParameter(20, Types.DECIMAL);

      stmt.executeUpdate();

      // The stored proc succeeded. Extract the results.
      BigDecimal[] ora_ol_amount = (BigDecimal[]) (stmt.getArray(7).getArray());
      String[] ora_i_name = (String[]) (stmt.getArray(8).getArray());
      BigDecimal[] ora_i_price = (BigDecimal[]) (stmt.getArray(9).getArray());
      BigDecimal[] ora_s_quantity = (BigDecimal[]) (stmt.getArray(10).getArray());
      String[] ora_brand_generic = (String[]) (stmt.getArray(11).getArray());

      newOrder.w_tax = stmt.getDouble(12);
      newOrder.d_tax = stmt.getDouble(13);
      newOrder.o_id = stmt.getInt(14);
      newOrder.o_entry_d = stmt.getTimestamp(15).toString();
      newOrder.o_ol_cnt = stmt.getInt(16);
      newOrder.total_amount = stmt.getDouble(17);
      newOrder.c_last = stmt.getString(18);
      newOrder.c_credit = stmt.getString(19);
      newOrder.c_discount = stmt.getDouble(20);

      for (int i = 0; i < 15; i++) {
        if (ora_ol_amount[i] != null) {
          newOrder.ol_amount[i] = ora_ol_amount[i].doubleValue();
          newOrder.i_name[i] = ora_i_name[i];
          newOrder.i_price[i] = ora_i_price[i].doubleValue();
          newOrder.s_quantity[i] = ora_s_quantity[i].intValue();
          newOrder.brand_generic[i] = ora_brand_generic[i];
        }
      }

      newOrder.execution_status = new String("Order placed");

      dbConn.commit();
      stmt.close();

    } catch (SQLException se) {
      boolean expected = false;

      if (trans_rbk && se.getMessage().startsWith("ORA-20001: Item number is not valid")) {
        newOrder.execution_status = new String("Item number is not valid");
        expected = true;
      } else {
        log.error("Unexpected SQLException in NEW_ORDER");
        log.error("message: '{}' trans_rbk={}", se.getMessage(), trans_rbk);
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
    CallableStatement stmt;

    try {
      // Execute the stored procedure for STOCK_LEVEL
      stmt = dbConn.prepareCall(stmtPaymentStoredProc);

      stmt.setInt(1, payment.w_id);
      stmt.setInt(2, payment.d_id);
      stmt.setInt(3, payment.c_id);
      stmt.setInt(4, payment.c_d_id);
      stmt.setInt(5, payment.c_w_id);
      stmt.setString(6, payment.c_last);
      stmt.setBigDecimal(7, new BigDecimal(payment.h_amount));
      stmt.registerOutParameter(3, Types.INTEGER);
      stmt.registerOutParameter(6, Types.VARCHAR);
      stmt.registerOutParameter(8, Types.VARCHAR);
      stmt.registerOutParameter(9, Types.VARCHAR);
      stmt.registerOutParameter(10, Types.VARCHAR);
      stmt.registerOutParameter(11, Types.VARCHAR);
      stmt.registerOutParameter(12, Types.VARCHAR);
      stmt.registerOutParameter(13, Types.VARCHAR);
      stmt.registerOutParameter(14, Types.VARCHAR);
      stmt.registerOutParameter(15, Types.VARCHAR);
      stmt.registerOutParameter(16, Types.VARCHAR);
      stmt.registerOutParameter(17, Types.VARCHAR);
      stmt.registerOutParameter(18, Types.VARCHAR);
      stmt.registerOutParameter(19, Types.VARCHAR);
      stmt.registerOutParameter(20, Types.VARCHAR);
      stmt.registerOutParameter(21, Types.VARCHAR);
      stmt.registerOutParameter(22, Types.VARCHAR);
      stmt.registerOutParameter(23, Types.VARCHAR);
      stmt.registerOutParameter(24, Types.VARCHAR);
      stmt.registerOutParameter(25, Types.VARCHAR);
      stmt.registerOutParameter(26, Types.VARCHAR);
      stmt.registerOutParameter(27, Types.VARCHAR);
      stmt.registerOutParameter(28, Types.TIMESTAMP);
      stmt.registerOutParameter(29, Types.VARCHAR);
      stmt.registerOutParameter(30, Types.DECIMAL);
      stmt.registerOutParameter(31, Types.DECIMAL);
      stmt.registerOutParameter(32, Types.DECIMAL);
      stmt.registerOutParameter(33, Types.VARCHAR);
      stmt.registerOutParameter(34, Types.TIMESTAMP);

      stmt.executeUpdate();

      // The stored proc succeded. Extract the results.
      payment.c_id = stmt.getInt(3);
      payment.c_last = stmt.getString(6);
      payment.w_name = stmt.getString(8);
      payment.w_street_1 = stmt.getString(9);
      payment.w_street_2 = stmt.getString(10);
      payment.w_city = stmt.getString(11);
      payment.w_state = stmt.getString(12);
      payment.w_zip = stmt.getString(13);
      payment.d_name = stmt.getString(14);
      payment.d_street_1 = stmt.getString(15);
      payment.d_street_2 = stmt.getString(16);
      payment.d_city = stmt.getString(17);
      payment.d_state = stmt.getString(18);
      payment.d_zip = stmt.getString(19);
      payment.c_first = stmt.getString(20);
      payment.c_middle = stmt.getString(21);
      payment.c_street_1 = stmt.getString(22);
      payment.c_street_2 = stmt.getString(23);
      payment.c_city = stmt.getString(24);
      payment.c_state = stmt.getString(25);
      payment.c_zip = stmt.getString(26);
      payment.c_phone = stmt.getString(27);
      payment.c_since = stmt.getTimestamp(28).toString();
      payment.c_credit = stmt.getString(29);
      payment.c_credit_lim = stmt.getDouble(30);
      payment.c_discount = stmt.getDouble(31);
      payment.c_balance = stmt.getDouble(32);
      payment.c_data = stmt.getString(33);
      payment.h_date = stmt.getTimestamp(34).toString();

      dbConn.commit();
      stmt.close();

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
    CallableStatement stmt;

    try {
      // Execute the stored procedure for ORDER_STATUS
      int[] supply_w_id_arr = new int[15];
      int[] i_id_arr = new int[15];
      int[] quantity_arr = new int[15];
      double[] amount_arr = new double[15];
      String[] delivery_d = new String[15];

      stmt = dbConn.prepareCall(stmtOrderStatusStoredProc);
      stmt.setInt(1, orderStatus.w_id);
      stmt.setInt(2, orderStatus.d_id);
      stmt.setInt(3, orderStatus.c_id);
      stmt.setString(4, orderStatus.c_last);
      stmt.registerOutParameter(3, Types.INTEGER);
      stmt.registerOutParameter(4, Types.VARCHAR);
      stmt.registerOutParameter(5, Types.VARCHAR);
      stmt.registerOutParameter(6, Types.VARCHAR);
      stmt.registerOutParameter(7, Types.DECIMAL);
      stmt.registerOutParameter(8, Types.INTEGER);
      stmt.registerOutParameter(9, Types.TIMESTAMP);
      stmt.registerOutParameter(10, Types.INTEGER);
      stmt.registerOutParameter(11, OracleTypes.ARRAY, "INT_ARRAY");
      stmt.registerOutParameter(12, OracleTypes.ARRAY, "INT_ARRAY");
      stmt.registerOutParameter(13, OracleTypes.ARRAY, "INT_ARRAY");
      stmt.registerOutParameter(14, OracleTypes.ARRAY, "NUM_ARRAY");
      stmt.registerOutParameter(15, OracleTypes.ARRAY, "VARCHAR16_ARRAY");

      stmt.executeUpdate();

      // The stored proc succeeded. Extract the results.
      orderStatus.c_id = stmt.getInt(3);
      orderStatus.c_last = stmt.getString(4);
      orderStatus.c_first = stmt.getString(5);
      orderStatus.c_middle = stmt.getString(6);
      orderStatus.c_balance = stmt.getDouble(7);
      orderStatus.o_id = stmt.getInt(8);
      orderStatus.o_entry_d = stmt.getTimestamp(9).toString();
      orderStatus.o_carrier_id = stmt.getInt(10);

      BigDecimal[] ora_supply_w_id = (BigDecimal[]) (stmt.getArray(11).getArray());
      BigDecimal[] ora_i_id = (BigDecimal[]) (stmt.getArray(12).getArray());
      BigDecimal[] ora_quantity = (BigDecimal[]) (stmt.getArray(13).getArray());
      BigDecimal[] ora_amount = (BigDecimal[]) (stmt.getArray(14).getArray());
      String[] ora_delivery_d = (String[]) (stmt.getArray(15).getArray());

      for (int i = 0; i < 15 && ora_supply_w_id != null; i++) {
        orderStatus.ol_supply_w_id[i] = ora_supply_w_id[i].intValue();
        orderStatus.ol_i_id[i] = ora_i_id[i].intValue();
        orderStatus.ol_quantity[i] = ora_quantity[i].intValue();
        orderStatus.ol_amount[i] = ora_amount[i].doubleValue();
        if (ora_delivery_d[i] == null)
          orderStatus.ol_delivery_d[i] = " ";
        else
          orderStatus.ol_delivery_d[i] = ora_delivery_d[i];
      }

      dbConn.commit();
      stmt.close();

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
    CallableStatement stmt;

    try {

      // Execute the stored procedure for STOCK_LEVEL
      stmt = dbConn.prepareCall(stmtStockLevelStoredProc);
      stmt.setInt(1, stockLevel.w_id);
      stmt.setInt(2, stockLevel.d_id);
      stmt.setInt(3, stockLevel.threshold);
      stmt.registerOutParameter(4, Types.INTEGER);

      stmt.executeUpdate();

      // The stored proc succeeded. Extract the results.
      stockLevel.low_stock = stmt.getInt(4);

      dbConn.commit();
      stmt.close();

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
    CallableStatement stmt;

    try {
      // Execute the stored procedure for DELIVERY_BG
      int[] delivery_array = new int[10];

      stmt = dbConn.prepareCall(stmtDeliveryBGStoredProc);
      stmt.setInt(1, deliveryBG.w_id);
      stmt.setInt(2, deliveryBG.o_carrier_id);
      stmt.setTimestamp(3, Timestamp.valueOf(deliveryBG.ol_delivery_d));
      stmt.registerOutParameter(4, OracleTypes.ARRAY, "INT_ARRAY");

      stmt.executeUpdate();

      // The stored proc succeeded. Extract the results.
      BigDecimal[] ora_array = (BigDecimal[]) (stmt.getArray(4).getArray());

      deliveryBG.delivered_o_id = new int[delivery_array.length];
      for (int i = 0; i < 10; i++) {
        deliveryBG.delivered_o_id[i] = ora_array[i].intValue();
      }

      dbConn.commit();
      stmt.close();
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
