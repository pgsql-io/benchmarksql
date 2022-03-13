package com.github.pgsqlio.benchmarksql.application;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Formatter;
import java.util.Properties;
import java.util.Vector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.pgsqlio.benchmarksql.jtpcc.jTPCC;
import com.github.pgsqlio.benchmarksql.jtpcc.jTPCCApplication;
import com.github.pgsqlio.benchmarksql.jtpcc.jTPCCConfig;
import com.github.pgsqlio.benchmarksql.jtpcc.jTPCCMonkey;
import com.github.pgsqlio.benchmarksql.jtpcc.jTPCCTData;

/**
 * AppGeneric - TPC-C Transaction Implementation for using Generic with plain JDBC
 * PreparedStatements.
 */
public class AppGeneric extends jTPCCApplication {
  private static Logger log = LogManager.getLogger(AppGeneric.class);;
  private jTPCC gdata;
  private int sut_id;

  private Connection dbConn;

  public PreparedStatement stmtNewOrderSelectWhseCust;
  public PreparedStatement stmtNewOrderSelectDist;
  public PreparedStatement stmtNewOrderUpdateDist;
  public PreparedStatement stmtNewOrderInsertOrder;
  public PreparedStatement stmtNewOrderInsertNewOrder;
  public PreparedStatement stmtNewOrderSelectStock;
  public PreparedStatement stmtNewOrderSelectItem;
  public PreparedStatement stmtNewOrderUpdateStock;
  public PreparedStatement stmtNewOrderInsertOrderLine;

  public PreparedStatement stmtPaymentSelectWarehouse;
  public PreparedStatement stmtPaymentSelectDistrict;
  public PreparedStatement stmtPaymentSelectCustomerListByLast;
  public PreparedStatement stmtPaymentSelectCustomer;
  public PreparedStatement stmtPaymentSelectCustomerData;
  public PreparedStatement stmtPaymentUpdateWarehouse;
  public PreparedStatement stmtPaymentUpdateDistrict;
  public PreparedStatement stmtPaymentUpdateCustomer;
  public PreparedStatement stmtPaymentUpdateCustomerWithData;
  public PreparedStatement stmtPaymentInsertHistory;

  public PreparedStatement stmtOrderStatusSelectCustomerListByLast;
  public PreparedStatement stmtOrderStatusSelectCustomer;
  public PreparedStatement stmtOrderStatusSelectLastOrder;
  public PreparedStatement stmtOrderStatusSelectOrderLine;

  public PreparedStatement stmtStockLevelSelectLow;

  public PreparedStatement stmtDeliveryBGSelectOldestNewOrder;
  public PreparedStatement stmtDeliveryBGDeleteOldestNewOrder;
  public PreparedStatement stmtDeliveryBGSelectOrder;
  public PreparedStatement stmtDeliveryBGUpdateOrder;
  public PreparedStatement stmtDeliveryBGSelectSumOLAmount;
  public PreparedStatement stmtDeliveryBGUpdateOrderLine;
  public PreparedStatement stmtDeliveryBGUpdateCustomer;


  public void init(jTPCC gdata, int sut_id) throws Exception {
    Properties dbProps;

    this.gdata = gdata;
    this.sut_id = sut_id;

    // Connect to the database
    dbProps = new Properties();
    dbProps.setProperty("user", gdata.iUser);
    dbProps.setProperty("password", gdata.iPassword);

    /*
     * Fine tuning of database conneciton parameters if needed. TODO: See if this could be moved
     * into the URI.
     */
    switch (jTPCC.dbType) {
      case jTPCCConfig.DB_FIREBIRD:
        /*
         * Firebird needs no_rec_version for our load to work. Even with that some "deadlocks"
         * occur. Note that the message "deadlock" in Firebird can mean something completely
         * different, namely that there was a conflicting write to a row that could not be resolved.
         */
        dbProps.setProperty(
              "TRANSACTION_READ_COMMITTED", "isc_tpb_read_committed,"
            + "isc_tpb_no_rec_version,"
            + "isc_tpb_write,"
            + "isc_tpb_wait");
        break;

      default:
        break;
    }

    dbConn = DriverManager.getConnection(gdata.iConn, dbProps);
    dbConn.setAutoCommit(false);

    // PreparedStataments for NEW_ORDER
    stmtNewOrderSelectWhseCust = dbConn.prepareStatement(
          "SELECT c_discount, c_last, c_credit, w_tax "
        + "    FROM bmsql_customer "
        + "    JOIN bmsql_warehouse ON (w_id = c_w_id) "
        + "    WHERE c_w_id = ? AND c_d_id = ? AND c_id = ?");
    if (jTPCC.dbType == jTPCCConfig.DB_TSQL) {
      stmtNewOrderSelectDist = dbConn.prepareStatement(
            "SELECT d_tax, d_next_o_id "
          + "    FROM bmsql_district WITH (UPDLOCK) "
          + "    WHERE d_w_id = ? AND d_id = ? ");
    } else if (jTPCC.dbType == jTPCCConfig.DB_BABELFISH) {
      stmtNewOrderSelectDist = dbConn.prepareStatement(
            "UPDATE bmsql_district SET d_w_id = d_w_id "
          + "    WHERE d_w_id = ? AND d_id = ?; "
          + "SELECT d_tax, d_next_o_id "
          + "    FROM bmsql_district WITH (UPDLOCK) "
          + "    WHERE d_w_id = ? AND d_id = ? ");
    } else {
      stmtNewOrderSelectDist = dbConn.prepareStatement(
            "SELECT d_tax, d_next_o_id "
          + "    FROM bmsql_district "
          + "    WHERE d_w_id = ? AND d_id = ? "
          + "    FOR UPDATE");
      }
    stmtNewOrderUpdateDist = dbConn.prepareStatement(
          "UPDATE bmsql_district "
        + "    SET d_next_o_id = d_next_o_id + 1 "
        + "    WHERE d_w_id = ? AND d_id = ?");
    stmtNewOrderInsertOrder = dbConn.prepareStatement(
          "INSERT INTO bmsql_oorder ("
        + "    o_id, o_d_id, o_w_id, o_c_id, o_entry_d, "
        + "    o_ol_cnt, o_all_local) "
        + "VALUES (?, ?, ?, ?, ?, ?, ?)");
    stmtNewOrderInsertNewOrder = dbConn.prepareStatement(
          "INSERT INTO bmsql_new_order ("
        + "    no_o_id, no_d_id, no_w_id) "
        + "VALUES (?, ?, ?)");
    if (jTPCC.dbType == jTPCCConfig.DB_TSQL) {
      stmtNewOrderSelectStock = dbConn.prepareStatement(
            "SELECT s_quantity, s_data, "
          + "       s_dist_01, s_dist_02, s_dist_03, s_dist_04, "
          + "       s_dist_05, s_dist_06, s_dist_07, s_dist_08, "
          + "       s_dist_09, s_dist_10 "
          + "    FROM bmsql_stock WITH (UPDLOCK) "
          + "    WHERE s_w_id = ? AND s_i_id = ? ");
    } else if (jTPCC.dbType == jTPCCConfig.DB_BABELFISH) {
      stmtNewOrderSelectStock = dbConn.prepareStatement(
            "UPDATE bmsql_stock SET s_w_id = s_w_id "
          + "    WHERE s_w_id = ? AND s_i_id = ?; "
          + "SELECT s_quantity, s_data, "
          + "       s_dist_01, s_dist_02, s_dist_03, s_dist_04, "
          + "       s_dist_05, s_dist_06, s_dist_07, s_dist_08, "
          + "       s_dist_09, s_dist_10 "
          + "    FROM bmsql_stock WITH (UPDLOCK) "
          + "    WHERE s_w_id = ? AND s_i_id = ? ");
    } else {
      stmtNewOrderSelectStock = dbConn.prepareStatement(
            "SELECT s_quantity, s_data, "
          + "       s_dist_01, s_dist_02, s_dist_03, s_dist_04, "
          + "       s_dist_05, s_dist_06, s_dist_07, s_dist_08, "
          + "       s_dist_09, s_dist_10 "
          + "    FROM bmsql_stock "
          + "    WHERE s_w_id = ? AND s_i_id = ? "
          + "    FOR UPDATE");
    }
    stmtNewOrderSelectItem = dbConn.prepareStatement(
          "SELECT i_price, i_name, i_data "
        + "    FROM bmsql_item "
        + "    WHERE i_id = ?");
    stmtNewOrderUpdateStock = dbConn.prepareStatement(
          "UPDATE bmsql_stock "
        + "    SET s_quantity = ?, s_ytd = s_ytd + ?, "
        + "        s_order_cnt = s_order_cnt + 1, "
        + "        s_remote_cnt = s_remote_cnt + ? "
        + "    WHERE s_w_id = ? AND s_i_id = ?");
    stmtNewOrderInsertOrderLine = dbConn.prepareStatement(
          "INSERT INTO bmsql_order_line ("
        + "    ol_o_id, ol_d_id, ol_w_id, ol_number, "
        + "    ol_i_id, ol_supply_w_id, ol_quantity, "
        + "    ol_amount, ol_dist_info) "
        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");

    // PreparedStatements for PAYMENT
    stmtPaymentSelectWarehouse = dbConn.prepareStatement(
          "SELECT w_name, w_street_1, w_street_2, w_city, "
        + "       w_state, w_zip "
        + "    FROM bmsql_warehouse "
        + "    WHERE w_id = ? ");
    stmtPaymentSelectDistrict = dbConn.prepareStatement(
          "SELECT d_name, d_street_1, d_street_2, d_city, "
        + "       d_state, d_zip "
        + "    FROM bmsql_district "
        + "    WHERE d_w_id = ? AND d_id = ?");
    stmtPaymentSelectCustomerListByLast = dbConn.prepareStatement(
          "SELECT c_id "
        + "    FROM bmsql_customer "
        + "    WHERE c_w_id = ? AND c_d_id = ? AND c_last = ? "
        + "    ORDER BY c_first");
    if (jTPCC.dbType == jTPCCConfig.DB_TSQL) {
      stmtPaymentSelectCustomer = dbConn.prepareStatement(
            "SELECT c_first, c_middle, c_last, c_street_1, c_street_2, "
          + "       c_city, c_state, c_zip, c_phone, c_since, c_credit, "
          + "       c_credit_lim, c_discount, c_balance "
          + "    FROM bmsql_customer WITH (UPDLOCK) "
          + "    WHERE c_w_id = ? AND c_d_id = ? AND c_id = ? ");
    } else if (jTPCC.dbType == jTPCCConfig.DB_BABELFISH) {
      stmtPaymentSelectCustomer = dbConn.prepareStatement(
            "UPDATE bmsql_customer SET c_w_id = c_w_id "
          + "WHERE c_w_id = ? AND c_d_id = ? AND c_id = ?; "
          + "SELECT c_first, c_middle, c_last, c_street_1, c_street_2, "
          + "       c_city, c_state, c_zip, c_phone, c_since, c_credit, "
          + "       c_credit_lim, c_discount, c_balance "
          + "    FROM bmsql_customer WITH (UPDLOCK) "
          + "    WHERE c_w_id = ? AND c_d_id = ? AND c_id = ? ");
    } else {
      stmtPaymentSelectCustomer = dbConn.prepareStatement(
            "SELECT c_first, c_middle, c_last, c_street_1, c_street_2, "
          + "       c_city, c_state, c_zip, c_phone, c_since, c_credit, "
          + "       c_credit_lim, c_discount, c_balance "
          + "    FROM bmsql_customer "
          + "    WHERE c_w_id = ? AND c_d_id = ? AND c_id = ? "
          + "    FOR UPDATE");
    }
    stmtPaymentSelectCustomerData = dbConn.prepareStatement(
          "SELECT c_data "
        + "    FROM bmsql_customer "
        + "    WHERE c_w_id = ? AND c_d_id = ? AND c_id = ?");
    stmtPaymentUpdateWarehouse = dbConn.prepareStatement(
          "UPDATE bmsql_warehouse "
        + "    SET w_ytd = w_ytd + ? "
        + "    WHERE w_id = ?");
    stmtPaymentUpdateDistrict = dbConn.prepareStatement(
          "UPDATE bmsql_district "
        + "    SET d_ytd = d_ytd + ? "
        + "    WHERE d_w_id = ? AND d_id = ?");
    stmtPaymentUpdateCustomer = dbConn.prepareStatement(
          "UPDATE bmsql_customer "
        + "    SET c_balance = c_balance - ?, "
        + "        c_ytd_payment = c_ytd_payment + ?, "
        + "        c_payment_cnt = c_payment_cnt + 1 "
        + "    WHERE c_w_id = ? AND c_d_id = ? AND c_id = ?");
    stmtPaymentUpdateCustomerWithData = dbConn.prepareStatement(
          "UPDATE bmsql_customer "
        + "    SET c_balance = c_balance - ?, "
        + "        c_ytd_payment = c_ytd_payment + ?, "
        + "        c_payment_cnt = c_payment_cnt + 1, "
        + "        c_data = ? "
        + "    WHERE c_w_id = ? AND c_d_id = ? AND c_id = ?");
    stmtPaymentInsertHistory = dbConn.prepareStatement(
          "INSERT INTO bmsql_history ("
        + "    h_c_id, h_c_d_id, h_c_w_id, h_d_id, h_w_id, "
        + "    h_date, h_amount, h_data) "
        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)");

    // PreparedStatements for ORDER_STATUS
    stmtOrderStatusSelectCustomerListByLast = dbConn.prepareStatement(
          "SELECT c_id "
        + "    FROM bmsql_customer "
        + "    WHERE c_w_id = ? AND c_d_id = ? AND c_last = ? "
        + "    ORDER BY c_first");
    stmtOrderStatusSelectCustomer = dbConn.prepareStatement(
          "SELECT c_first, c_middle, c_last, c_balance "
        + "    FROM bmsql_customer "
        + "    WHERE c_w_id = ? AND c_d_id = ? AND c_id = ?");
    stmtOrderStatusSelectLastOrder = dbConn.prepareStatement(
          "SELECT o_id, o_entry_d, o_carrier_id "
        + "    FROM bmsql_oorder "
        + "    WHERE o_w_id = ? AND o_d_id = ? AND o_c_id = ? "
        + "      AND o_id = ("
        + "          SELECT max(o_id) "
        + "              FROM bmsql_oorder "
        + "              WHERE o_w_id = ? AND o_d_id = ? AND o_c_id = ?"
        + "          )");
    stmtOrderStatusSelectOrderLine = dbConn.prepareStatement(
          "SELECT ol_i_id, ol_supply_w_id, ol_quantity, "
        + "       ol_amount, ol_delivery_d "
        + "    FROM bmsql_order_line "
        + "    WHERE ol_w_id = ? AND ol_d_id = ? AND ol_o_id = ? "
        + "    ORDER BY ol_w_id, ol_d_id, ol_o_id, ol_number");

    // PreparedStatement for STOCK_LEVEL
    switch (jTPCC.dbType) {
      case jTPCCConfig.DB_POSTGRES:
      case jTPCCConfig.DB_MARIADB:
      case jTPCCConfig.DB_TSQL:
      case jTPCCConfig.DB_BABELFISH:
        stmtStockLevelSelectLow = dbConn.prepareStatement(
              "SELECT count(*) AS low_stock FROM ("
            + "    SELECT s_w_id, s_i_id, s_quantity "
            + "        FROM bmsql_stock "
            + "        WHERE s_w_id = ? AND s_quantity < ? AND s_i_id IN ("
            + "            SELECT ol_i_id "
            + "                FROM bmsql_district "
            + "                JOIN bmsql_order_line ON ol_w_id = d_w_id "
            + "                 AND ol_d_id = d_id "
            + "                 AND ol_o_id >= d_next_o_id - 20 "
            + "                 AND ol_o_id < d_next_o_id "
            + "                WHERE d_w_id = ? AND d_id = ? "
            + "        ) "
            + "    ) AS L");
        break;

      default:
        stmtStockLevelSelectLow = dbConn.prepareStatement(
              "SELECT count(*) AS low_stock FROM ("
            + "    SELECT s_w_id, s_i_id, s_quantity "
            + "        FROM bmsql_stock "
            + "        WHERE s_w_id = ? AND s_quantity < ? AND s_i_id IN ("
            + "            SELECT ol_i_id "
            + "                FROM bmsql_district "
            + "                JOIN bmsql_order_line ON ol_w_id = d_w_id "
            + "                 AND ol_d_id = d_id "
            + "                 AND ol_o_id >= d_next_o_id - 20 "
            + "                 AND ol_o_id < d_next_o_id "
            + "                WHERE d_w_id = ? AND d_id = ? "
            + "        ) "
            + "    )");
        break;
    }

    // PreparedStatements for DELIVERY_BG
    stmtDeliveryBGSelectOldestNewOrder = dbConn.prepareStatement(
          "SELECT no_o_id "
        + "    FROM bmsql_new_order "
        + "    WHERE no_w_id = ? AND no_d_id = ? "
        + "    ORDER BY no_o_id ASC");
    stmtDeliveryBGDeleteOldestNewOrder = dbConn.prepareStatement(
          "DELETE FROM bmsql_new_order "
        + "    WHERE no_w_id = ? AND no_d_id = ? AND no_o_id = ?");
    stmtDeliveryBGSelectOrder = dbConn.prepareStatement(
          "SELECT o_c_id "
        + "    FROM bmsql_oorder "
        + "    WHERE o_w_id = ? AND o_d_id = ? AND o_id = ?");
    stmtDeliveryBGUpdateOrder = dbConn.prepareStatement(
          "UPDATE bmsql_oorder "
        + "    SET o_carrier_id = ? "
        + "    WHERE o_w_id = ? AND o_d_id = ? AND o_id = ?");
    stmtDeliveryBGSelectSumOLAmount = dbConn.prepareStatement(
          "SELECT sum(ol_amount) AS sum_ol_amount "
        + "    FROM bmsql_order_line "
        + "    WHERE ol_w_id = ? AND ol_d_id = ? AND ol_o_id = ?");
    stmtDeliveryBGUpdateOrderLine = dbConn.prepareStatement(
          "UPDATE bmsql_order_line "
        + "    SET ol_delivery_d = ? "
        + "    WHERE ol_w_id = ? AND ol_d_id = ? AND ol_o_id = ?");
    stmtDeliveryBGUpdateCustomer = dbConn.prepareStatement(
          "UPDATE bmsql_customer "
        + "    SET c_balance = c_balance + ?, "
        + "        c_delivery_cnt = c_delivery_cnt + 1 "
        + "    WHERE c_w_id = ? AND c_d_id = ? AND c_id = ?");
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
    PreparedStatement insertOrderLineBatch;
    PreparedStatement updateStockBatch;
    ResultSet rs;

    int o_id;
    int o_all_local = 1;
    long o_entry_d;
    int ol_cnt;
    double total_amount = 0.0;

    int ol_seq[] = new int[15];

    // The o_entry_d is now.
    o_entry_d = System.currentTimeMillis();
    newOrder.o_entry_d = new java.sql.Timestamp(o_entry_d).toString();

    /*
     * When processing the order lines we must select the STOCK rows FOR UPDATE. This is because we
     * must perform business logic (the juggling with the S_QUANTITY) here in the application and
     * cannot do that in an atomic UPDATE statement while getting the original value back at the
     * same time (UPDATE ... RETURNING may not be vendor neutral). This can lead to possible
     * deadlocks if two transactions try to lock the same two stock rows in opposite order. To avoid
     * that we process the order lines in the order of the order of ol_supply_w_id, ol_i_id.
     */
    for (ol_cnt = 0; ol_cnt < 15 && newOrder.ol_i_id[ol_cnt] != 0; ol_cnt++) {
      ol_seq[ol_cnt] = ol_cnt;

      // While looping we also determine o_all_local.
      if (newOrder.ol_supply_w_id[ol_cnt] != newOrder.w_id)
        o_all_local = 0;
    }

    for (int x = 0; x < ol_cnt - 1; x++) {
      for (int y = x + 1; y < ol_cnt; y++) {
        if (newOrder.ol_supply_w_id[ol_seq[y]] < newOrder.ol_supply_w_id[ol_seq[x]]) {
          int tmp = ol_seq[x];
          ol_seq[x] = ol_seq[y];
          ol_seq[y] = tmp;
        } else if (newOrder.ol_supply_w_id[ol_seq[y]] == newOrder.ol_supply_w_id[ol_seq[x]]
            && newOrder.ol_i_id[ol_seq[y]] < newOrder.ol_i_id[ol_seq[x]]) {
          int tmp = ol_seq[x];
          ol_seq[x] = ol_seq[y];
          ol_seq[y] = tmp;
        }
      }
    }

    // The above also provided the output value for o_ol_cnt;
    newOrder.o_ol_cnt = ol_cnt;

    String last_stmt = "unknown";

    try {
      // Retrieve the required data from DISTRICT
      last_stmt = "stmtNewOrderSelectDist";
      stmt = stmtNewOrderSelectDist;
      if (jTPCC.dbType == jTPCCConfig.DB_BABELFISH) {
        stmt.setInt(1, newOrder.w_id);
        stmt.setInt(2, newOrder.d_id);
        stmt.setInt(3, newOrder.w_id);
        stmt.setInt(4, newOrder.d_id);
      } else {
        stmt.setInt(1, newOrder.w_id);
        stmt.setInt(2, newOrder.d_id);
      }
      rs = stmt.executeQuery();
      if (!rs.next()) {
        rs.close();
        throw new SQLException(
            "District for" + " W_ID=" + newOrder.w_id + " D_ID=" + newOrder.d_id + " not found");
      }
      newOrder.d_tax = rs.getDouble("d_tax");
      newOrder.o_id = rs.getInt("d_next_o_id");
      o_id = newOrder.o_id;
      rs.close();

      // Retrieve the required data from CUSTOMER and WAREHOUSE
      last_stmt = "stmtNewOrderSelectWhseCust";
      stmt = stmtNewOrderSelectWhseCust;
      stmt.setInt(1, newOrder.w_id);
      stmt.setInt(2, newOrder.d_id);
      stmt.setInt(3, newOrder.c_id);
      rs = stmt.executeQuery();
      if (!rs.next()) {
        rs.close();
        throw new SQLException("Warehouse or Customer for" + " W_ID=" + newOrder.w_id + " D_ID="
            + newOrder.d_id + " C_ID=" + newOrder.c_id + " not found");
      }
      newOrder.w_tax = rs.getDouble("w_tax");
      newOrder.c_last = rs.getString("c_last");
      newOrder.c_credit = rs.getString("c_credit");
      newOrder.c_discount = rs.getDouble("c_discount");
      rs.close();

      // Update the DISTRICT bumping the D_NEXT_O_ID
      last_stmt = "stmtNewOrderUpdateDist";
      stmt = stmtNewOrderUpdateDist;
      stmt.setInt(1, newOrder.w_id);
      stmt.setInt(2, newOrder.d_id);
      stmt.executeUpdate();

      // Insert the ORDER row
      last_stmt = "stmtNewOrderInsertOrder";
      stmt = stmtNewOrderInsertOrder;
      stmt.setInt(1, o_id);
      stmt.setInt(2, newOrder.d_id);
      stmt.setInt(3, newOrder.w_id);
      stmt.setInt(4, newOrder.c_id);
      stmt.setTimestamp(5, new java.sql.Timestamp(System.currentTimeMillis()));
      stmt.setInt(6, ol_cnt);
      stmt.setInt(7, o_all_local);
      stmt.executeUpdate();

      // Insert the NEW_ORDER row
      last_stmt = "stmtNewOrderInsertNewOrder";
      stmt = stmtNewOrderInsertNewOrder;
      stmt.setInt(1, o_id);
      stmt.setInt(2, newOrder.d_id);
      stmt.setInt(3, newOrder.w_id);
      stmt.executeUpdate();

      // Per ORDER_LINE
      insertOrderLineBatch = stmtNewOrderInsertOrderLine;
      updateStockBatch = stmtNewOrderUpdateStock;
      for (int i = 0; i < ol_cnt; i++) {
        int ol_number = i + 1;
        int seq = ol_seq[i];
        String i_data;

        last_stmt = "stmtNewOrderSelectItem";
        stmt = stmtNewOrderSelectItem;
        stmt.setInt(1, newOrder.ol_i_id[seq]);
        rs = stmt.executeQuery();
        if (!rs.next()) {
          rs.close();

          /*
           * 1% of NEW_ORDER transactions use an unused item in the last line to simulate user entry
           * errors. Make sure this is precisely that case.
           */
          if (trans_rbk && (newOrder.ol_i_id[seq] < 1 || newOrder.ol_i_id[seq] > 100000)) {
            /*
             * Clause 2.4.2.3 mandates that the entire transaction profile up to here must be
             * executed before we can roll back, except for retrieving the missing STOCK row and
             * inserting this ORDER_LINE row. Note that we haven't updated STOCK rows or inserted
             * any ORDER_LINE rows so far, we only batched them up. So we must do that now in order
             * to satisfy 2.4.2.3.
             */
            last_stmt = "insertOrderLineBatch.executeBatch";
            insertOrderLineBatch.executeBatch();
            insertOrderLineBatch.clearBatch();
            last_stmt = "updateStockBatch.executeBatch";
            updateStockBatch.executeBatch();
            updateStockBatch.clearBatch();

            dbConn.rollback();

            newOrder.total_amount = total_amount;
            newOrder.execution_status = new String("Item number is not valid");
            return;
          }

          // This ITEM should have been there.
          throw new Exception("ITEM " + newOrder.ol_i_id[seq] + " not found");
        }
        // Found ITEM
        newOrder.i_name[seq] = rs.getString("i_name");
        newOrder.i_price[seq] = rs.getDouble("i_price");
        i_data = rs.getString("i_data");
        rs.close();

        // Select STOCK for update.
        last_stmt = "stmtNewOrderSelectStock";
        stmt = stmtNewOrderSelectStock;
        if (jTPCC.dbType == jTPCCConfig.DB_BABELFISH) {
          stmt.setInt(1, newOrder.ol_supply_w_id[seq]);
          stmt.setInt(2, newOrder.ol_i_id[seq]);
          stmt.setInt(3, newOrder.ol_supply_w_id[seq]);
          stmt.setInt(4, newOrder.ol_i_id[seq]);
        } else {
          stmt.setInt(1, newOrder.ol_supply_w_id[seq]);
          stmt.setInt(2, newOrder.ol_i_id[seq]);
        }
        rs = stmt.executeQuery();
        if (!rs.next()) {
          throw new Exception("STOCK with" + " S_W_ID=" + newOrder.ol_supply_w_id[seq] + " S_I_ID="
              + newOrder.ol_i_id[seq] + " not fount");
        }
        newOrder.s_quantity[seq] = rs.getInt("s_quantity");
        // Leave the ResultSet open ... we need it for the s_dist_NN.

        newOrder.ol_amount[seq] = newOrder.i_price[seq] * newOrder.ol_quantity[seq];
        if (i_data.contains("ORIGINAL") && rs.getString("s_data").contains("ORIGINAL"))
          newOrder.brand_generic[seq] = new String("B");
        else
          newOrder.brand_generic[seq] = new String("G");

        total_amount += newOrder.ol_amount[seq] * (1.0 - newOrder.c_discount)
            * (1.0 + newOrder.w_tax + newOrder.d_tax);

        // Update the STOCK row.
        if (newOrder.s_quantity[seq] >= newOrder.ol_quantity[seq] + 10)
          updateStockBatch.setInt(1, newOrder.s_quantity[seq] - newOrder.ol_quantity[seq]);
        else
          updateStockBatch.setInt(1, newOrder.s_quantity[seq] + 91);
        updateStockBatch.setInt(2, newOrder.ol_quantity[seq]);
        if (newOrder.ol_supply_w_id[seq] == newOrder.w_id)
          updateStockBatch.setInt(3, 0);
        else
          updateStockBatch.setInt(3, 1);
        updateStockBatch.setInt(4, newOrder.ol_supply_w_id[seq]);
        updateStockBatch.setInt(5, newOrder.ol_i_id[seq]);
        updateStockBatch.addBatch();

        // Insert the ORDER_LINE row.
        insertOrderLineBatch.setInt(1, o_id);
        insertOrderLineBatch.setInt(2, newOrder.d_id);
        insertOrderLineBatch.setInt(3, newOrder.w_id);
        insertOrderLineBatch.setInt(4, seq + 1);
        insertOrderLineBatch.setInt(5, newOrder.ol_i_id[seq]);
        insertOrderLineBatch.setInt(6, newOrder.ol_supply_w_id[seq]);
        insertOrderLineBatch.setInt(7, newOrder.ol_quantity[seq]);
        insertOrderLineBatch.setDouble(8, newOrder.ol_amount[seq]);
        switch (newOrder.d_id) {
          case 1:
            insertOrderLineBatch.setString(9, rs.getString("s_dist_01"));
            break;
          case 2:
            insertOrderLineBatch.setString(9, rs.getString("s_dist_02"));
            break;
          case 3:
            insertOrderLineBatch.setString(9, rs.getString("s_dist_03"));
            break;
          case 4:
            insertOrderLineBatch.setString(9, rs.getString("s_dist_04"));
            break;
          case 5:
            insertOrderLineBatch.setString(9, rs.getString("s_dist_05"));
            break;
          case 6:
            insertOrderLineBatch.setString(9, rs.getString("s_dist_06"));
            break;
          case 7:
            insertOrderLineBatch.setString(9, rs.getString("s_dist_07"));
            break;
          case 8:
            insertOrderLineBatch.setString(9, rs.getString("s_dist_08"));
            break;
          case 9:
            insertOrderLineBatch.setString(9, rs.getString("s_dist_09"));
            break;
          case 10:
            insertOrderLineBatch.setString(9, rs.getString("s_dist_10"));
            break;
        }
        insertOrderLineBatch.addBatch();
      }
      rs.close();

      // All done ... execute the batches.
      last_stmt = "updateStockBatch.executeBatch";
      updateStockBatch.executeBatch();
      updateStockBatch.clearBatch();
      last_stmt = "insertOrderLineBatch.executeBatch";
      insertOrderLineBatch.executeBatch();
      insertOrderLineBatch.clearBatch();

      newOrder.execution_status = new String("Order placed");
      newOrder.total_amount = total_amount;

      dbConn.commit();

    } catch (SQLException se) {
      log.error("Unexpected SQLException in NEW_ORDER - stmt = '" +
                last_stmt + "'");
      for (SQLException x = se; x != null; x = x.getNextException())
        log.error(x.getMessage());
      log.info(se);

      try {
        stmtNewOrderUpdateStock.clearBatch();
        stmtNewOrderInsertOrderLine.clearBatch();
        dbConn.rollback();
      } catch (SQLException se2) {
        throw new Exception("Unexpected SQLException on rollback: " + se2.getMessage());
      }
      throw se;
    } catch (Exception e) {
      try {
        stmtNewOrderUpdateStock.clearBatch();
        stmtNewOrderInsertOrderLine.clearBatch();
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
    Vector<Integer> c_id_list = new Vector<Integer>();

    long h_date = System.currentTimeMillis();

    String last_stmt = "unknown";

    try {
      // Update the DISTRICT.
      last_stmt = "stmtPaymentUpdateDistrict";
      stmt = stmtPaymentUpdateDistrict;
      stmt.setDouble(1, payment.h_amount);
      stmt.setInt(2, payment.w_id);
      stmt.setInt(3, payment.d_id);
      stmt.executeUpdate();

      // Select the DISTRICT.
      last_stmt = "stmtPaymentSelectDistrict";
      stmt = stmtPaymentSelectDistrict;
      stmt.setInt(1, payment.w_id);
      stmt.setInt(2, payment.d_id);
      rs = stmt.executeQuery();
      if (!rs.next()) {
        rs.close();
        throw new Exception(
            "District for" + " W_ID=" + payment.w_id + " D_ID=" + payment.d_id + " not found");
      }
      payment.d_name = rs.getString("d_name");
      payment.d_street_1 = rs.getString("d_street_1");
      payment.d_street_2 = rs.getString("d_street_2");
      payment.d_city = rs.getString("d_city");
      payment.d_state = rs.getString("d_state");
      payment.d_zip = rs.getString("d_zip");
      rs.close();

      // Update the WAREHOUSE.
      last_stmt = "stmtPaymentUpdateWarehouse";
      stmt = stmtPaymentUpdateWarehouse;
      stmt.setDouble(1, payment.h_amount);
      stmt.setInt(2, payment.w_id);
      stmt.executeUpdate();

      // Select the WAREHOUSE.
      last_stmt = "stmtPaymentSelectWarehouse";
      stmt = stmtPaymentSelectWarehouse;
      stmt.setInt(1, payment.w_id);
      rs = stmt.executeQuery();
      if (!rs.next()) {
        rs.close();
        throw new Exception("Warehouse for" + " W_ID=" + payment.w_id + " not found");
      }
      payment.w_name = rs.getString("w_name");
      payment.w_street_1 = rs.getString("w_street_1");
      payment.w_street_2 = rs.getString("w_street_2");
      payment.w_city = rs.getString("w_city");
      payment.w_state = rs.getString("w_state");
      payment.w_zip = rs.getString("w_zip");
      rs.close();

      // If C_LAST is given instead of C_ID (60%), determine the C_ID.
      if (payment.c_last != null) {
        last_stmt = "stmtPaymentSelectCustomerListByLast";
        stmt = stmtPaymentSelectCustomerListByLast;
        stmt.setInt(1, payment.c_w_id);
        stmt.setInt(2, payment.c_d_id);
        stmt.setString(3, payment.c_last);
        rs = stmt.executeQuery();
        while (rs.next())
          c_id_list.add(rs.getInt("c_id"));
        rs.close();

        if (c_id_list.size() == 0) {
          throw new Exception("Customer(s) for" + " C_W_ID=" + payment.c_w_id + " C_D_ID="
              + payment.c_d_id + " C_LAST=" + payment.c_last + " not found");
        }

        payment.c_id = c_id_list.get((c_id_list.size() + 1) / 2 - 1);
      }

      // Select the CUSTOMER.
      last_stmt = "stmtPaymentSelectCustomer";
      stmt = stmtPaymentSelectCustomer;
      if (jTPCC.dbType == jTPCCConfig.DB_BABELFISH) {
        stmt.setInt(1, payment.c_w_id);
        stmt.setInt(2, payment.c_d_id);
        stmt.setInt(3, payment.c_id);
        stmt.setInt(4, payment.c_w_id);
        stmt.setInt(5, payment.c_d_id);
        stmt.setInt(6, payment.c_id);
      } else {
        stmt.setInt(1, payment.c_w_id);
        stmt.setInt(2, payment.c_d_id);
        stmt.setInt(3, payment.c_id);
      }
      rs = stmt.executeQuery();
      if (!rs.next()) {
        throw new Exception("Customer for" + " C_W_ID=" + payment.c_w_id + " C_D_ID="
            + payment.c_d_id + " C_ID=" + payment.c_id + " not found");
      }
      payment.c_first = rs.getString("c_first");
      payment.c_middle = rs.getString("c_middle");
      if (payment.c_last == null)
        payment.c_last = rs.getString("c_last");
      payment.c_street_1 = rs.getString("c_street_1");
      payment.c_street_2 = rs.getString("c_street_2");
      payment.c_city = rs.getString("c_city");
      payment.c_state = rs.getString("c_state");
      payment.c_zip = rs.getString("c_zip");
      payment.c_phone = rs.getString("c_phone");
      payment.c_since = rs.getTimestamp("c_since").toString();
      payment.c_credit = rs.getString("c_credit");
      payment.c_credit_lim = rs.getDouble("c_credit_lim");
      payment.c_discount = rs.getDouble("c_discount");
      payment.c_balance = rs.getDouble("c_balance");
      payment.c_data = new String("");
      rs.close();

      // Update the CUSTOMER.
      payment.c_balance -= payment.h_amount;
      if (payment.c_credit.equals("GC")) {
        // Customer with good credit, don't update C_DATA.
        last_stmt = "stmtPaymentUpdateCustomer";
        stmt = stmtPaymentUpdateCustomer;
        stmt.setDouble(1, payment.h_amount);
        stmt.setDouble(2, payment.h_amount);
        stmt.setInt(3, payment.c_w_id);
        stmt.setInt(4, payment.c_d_id);
        stmt.setInt(5, payment.c_id);
        stmt.executeUpdate();
      } else {
        // Customer with bad credit, need to do the C_DATA work.
        last_stmt = "stmtPaymentSelectCustomerData";
        stmt = stmtPaymentSelectCustomerData;
        stmt.setInt(1, payment.c_w_id);
        stmt.setInt(2, payment.c_d_id);
        stmt.setInt(3, payment.c_id);
        rs = stmt.executeQuery();
        if (!rs.next()) {
          throw new Exception("Customer.c_data for" + " C_W_ID=" + payment.c_w_id + " C_D_ID="
              + payment.c_d_id + " C_ID=" + payment.c_id + " not found");
        }
        payment.c_data = rs.getString("c_data");
        rs.close();

        last_stmt = "stmtPaymentUpdateCustomerWithData";
        stmt = stmtPaymentUpdateCustomerWithData;
        stmt.setDouble(1, payment.h_amount);
        stmt.setDouble(2, payment.h_amount);

        StringBuffer sbData = new StringBuffer();
        Formatter fmtData = new Formatter(sbData);
        fmtData.format("C_ID=%d C_D_ID=%d C_W_ID=%d " + "D_ID=%d W_ID=%d H_AMOUNT=%.2f   ",
            payment.c_id, payment.c_d_id, payment.c_w_id, payment.d_id, payment.w_id,
            payment.h_amount);
        sbData.append(payment.c_data);
        if (sbData.length() > 500)
          sbData.setLength(500);
        payment.c_data = sbData.toString();
        stmt.setString(3, payment.c_data);

        stmt.setInt(4, payment.c_w_id);
        stmt.setInt(5, payment.c_d_id);
        stmt.setInt(6, payment.c_id);
        stmt.executeUpdate();
      }

      // Insert the HISORY row.
      last_stmt = "stmtPaymentInsertHistory";
      stmt = stmtPaymentInsertHistory;
      stmt.setInt(1, payment.c_id);
      stmt.setInt(2, payment.c_d_id);
      stmt.setInt(3, payment.c_w_id);
      stmt.setInt(4, payment.d_id);
      stmt.setInt(5, payment.w_id);
      stmt.setTimestamp(6, new java.sql.Timestamp(h_date));
      stmt.setDouble(7, payment.h_amount);
      stmt.setString(8, payment.w_name + "    " + payment.d_name);
      stmt.executeUpdate();

      payment.h_date = new java.sql.Timestamp(h_date).toString();

      dbConn.commit();
    } catch (SQLException se) {
      log.error("Unexpected SQLException in PAYMENT - stmt = '" +
                last_stmt + "'");
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
    Vector<Integer> c_id_list = new Vector<Integer>();
    int ol_idx = 0;

    String last_stmt = "unknown";

    try {
      // If C_LAST is given instead of C_ID (60%), determine the C_ID.
      if (orderStatus.c_last != null) {
        last_stmt = "stmtOrderStatusSelectCustomerListByLast";
        stmt = stmtOrderStatusSelectCustomerListByLast;
        stmt.setInt(1, orderStatus.w_id);
        stmt.setInt(2, orderStatus.d_id);
        stmt.setString(3, orderStatus.c_last);
        rs = stmt.executeQuery();
        while (rs.next())
          c_id_list.add(rs.getInt("c_id"));
        rs.close();

        if (c_id_list.size() == 0) {
          throw new Exception("Customer(s) for" + " C_W_ID=" + orderStatus.w_id + " C_D_ID="
              + orderStatus.d_id + " C_LAST=" + orderStatus.c_last + " not found");
        }

        orderStatus.c_id = c_id_list.get((c_id_list.size() + 1) / 2 - 1);
      }

      // Select the CUSTOMER.
      last_stmt = "stmtOrderStatusSelectCustomer";
      stmt = stmtOrderStatusSelectCustomer;
      stmt.setInt(1, orderStatus.w_id);
      stmt.setInt(2, orderStatus.d_id);
      stmt.setInt(3, orderStatus.c_id);
      rs = stmt.executeQuery();
      if (!rs.next()) {
        throw new Exception("Customer for" + " C_W_ID=" + orderStatus.w_id + " C_D_ID="
            + orderStatus.d_id + " C_ID=" + orderStatus.c_id + " not found");
      }
      orderStatus.c_first = rs.getString("c_first");
      orderStatus.c_middle = rs.getString("c_middle");
      if (orderStatus.c_last == null)
        orderStatus.c_last = rs.getString("c_last");
      orderStatus.c_balance = rs.getDouble("c_balance");
      rs.close();

      // Select the last ORDER for this customer.
      last_stmt = "stmtOrderStatusSelectLastOrder";
      stmt = stmtOrderStatusSelectLastOrder;
      stmt.setInt(1, orderStatus.w_id);
      stmt.setInt(2, orderStatus.d_id);
      stmt.setInt(3, orderStatus.c_id);
      stmt.setInt(4, orderStatus.w_id);
      stmt.setInt(5, orderStatus.d_id);
      stmt.setInt(6, orderStatus.c_id);
      rs = stmt.executeQuery();
      if (!rs.next()) {
        throw new Exception("Last Order for" + " W_ID=" + orderStatus.w_id + " D_ID="
            + orderStatus.d_id + " C_ID=" + orderStatus.c_id + " not found");
      }
      orderStatus.o_id = rs.getInt("o_id");
      orderStatus.o_entry_d = rs.getTimestamp("o_entry_d").toString();
      orderStatus.o_carrier_id = rs.getInt("o_carrier_id");
      if (rs.wasNull())
        orderStatus.o_carrier_id = -1;
      rs.close();

      last_stmt = "stmtOrderStatusSelectOrderLine";
      stmt = stmtOrderStatusSelectOrderLine;
      stmt.setInt(1, orderStatus.w_id);
      stmt.setInt(2, orderStatus.d_id);
      stmt.setInt(3, orderStatus.o_id);
      rs = stmt.executeQuery();
      while (rs.next()) {
        Timestamp ol_delivery_d;

        orderStatus.ol_i_id[ol_idx] = rs.getInt("ol_i_id");
        orderStatus.ol_supply_w_id[ol_idx] = rs.getInt("ol_supply_w_id");
        orderStatus.ol_quantity[ol_idx] = rs.getInt("ol_quantity");
        orderStatus.ol_amount[ol_idx] = rs.getDouble("ol_amount");
        ol_delivery_d = rs.getTimestamp("ol_delivery_d");
        if (ol_delivery_d != null)
          orderStatus.ol_delivery_d[ol_idx] = ol_delivery_d.toString();
        else
          orderStatus.ol_delivery_d[ol_idx] = null;
        ol_idx++;
      }
      rs.close();

      while (ol_idx < 15) {
        orderStatus.ol_i_id[ol_idx] = 0;
        orderStatus.ol_supply_w_id[ol_idx] = 0;
        orderStatus.ol_quantity[ol_idx] = 0;
        orderStatus.ol_amount[ol_idx] = 0.0;
        orderStatus.ol_delivery_d[ol_idx] = null;
        ol_idx++;
      }

      dbConn.commit();
    } catch (SQLException se) {
      log.error("Unexpected SQLException in ORDER_STATUS - stmt = '" +
                last_stmt + "'");
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

    String last_stmt = "unknown";

    try {
      last_stmt = "stmtStockLevelSelectLow";
      stmt = stmtStockLevelSelectLow;
      stmt.setInt(1, stockLevel.w_id);
      stmt.setInt(2, stockLevel.threshold);
      stmt.setInt(3, stockLevel.w_id);
      stmt.setInt(4, stockLevel.d_id);
      rs = stmt.executeQuery();
      if (!rs.next()) {
        throw new Exception("Failed to get low-stock for" + " W_ID=" + stockLevel.w_id + " D_ID="
            + stockLevel.d_id);
      }
      stockLevel.low_stock = rs.getInt("low_stock");
      rs.close();

      dbConn.commit();
    } catch (SQLException se) {
      log.error("Unexpected SQLException in STOCK_LEVEL - stmt = '" +
                last_stmt + "'");
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
    PreparedStatement stmt1;
    PreparedStatement stmt2;
    ResultSet rs;
    int rc;
    int d_id;
    int o_id;
    int c_id;
    double sum_ol_amount;
    long now = System.currentTimeMillis();

    deliveryBG.delivered_o_id = new int[15];

    String last_stmt = "unknown";

    try {
      for (d_id = 1; d_id <= 10; d_id++) {
        o_id = -1;

        stmt1 = stmtDeliveryBGSelectOldestNewOrder;
        stmt2 = stmtDeliveryBGDeleteOldestNewOrder;

        /*
         * Try to find the oldest undelivered order for this DISTRICT. There may not be one, which
         * is a case that needs to be reportd.
         */
        while (o_id < 0) {
          last_stmt = "stmtDeliveryBGSelectOldestNewOrder";
          stmt1.setInt(1, deliveryBG.w_id);
          stmt1.setInt(2, d_id);
          rs = stmt1.executeQuery();
          if (!rs.next()) {
            rs.close();
            break;
          }
          o_id = rs.getInt("no_o_id");
          rs.close();

          last_stmt = "stmtDeliveryBGDeleteOldestNewOrder";
          stmt2.setInt(1, deliveryBG.w_id);
          stmt2.setInt(2, d_id);
          stmt2.setInt(3, o_id);
          rc = stmt2.executeUpdate();
          if (rc == 0) {
            /*
             * Failed to delete the NEW_ORDER row. This is not an error since for concurrency
             * reasons we did not select FOR UPDATE above. It is possible that another, concurrent
             * DELIVERY_BG transaction just deleted this row and is working on it now. We simply got
             * back and try to get the next one. This logic only works in READ_COMMITTED isolation
             * level and will cause SQLExceptions in anything higher than that.
             */
            o_id = -1;
          }
        }

        if (o_id < 0) {
          // No undelivered NEW_ORDER found for this DISTRICT.
          continue;
        }

        /*
         * We found out oldest undelivered order for this DISTRICT and the NEW_ORDER line has been
         * deleted. Process the rest of the DELIVERY_BG.
         */

        // Update the ORDER setting the o_carrier_id.
        last_stmt = "stmtDeliveryBGUpdateOrder";
        stmt1 = stmtDeliveryBGUpdateOrder;
        stmt1.setInt(1, deliveryBG.o_carrier_id);
        stmt1.setInt(2, deliveryBG.w_id);
        stmt1.setInt(3, d_id);
        stmt1.setInt(4, o_id);
        stmt1.executeUpdate();

        // Get the o_c_id from the ORDER.
        last_stmt = "stmtDeliveryBGSelectOrder";
        stmt1 = stmtDeliveryBGSelectOrder;
        stmt1.setInt(1, deliveryBG.w_id);
        stmt1.setInt(2, d_id);
        stmt1.setInt(3, o_id);
        rs = stmt1.executeQuery();
        if (!rs.next()) {
          rs.close();
          throw new Exception("ORDER in DELIVERY_BG for" + " O_W_ID=" + deliveryBG.w_id + " O_D_ID="
              + d_id + " O_ID=" + o_id + " not found");
        }
        c_id = rs.getInt("o_c_id");
        rs.close();

        // Update ORDER_LINE setting the ol_delivery_d.
        last_stmt = "stmtDeliveryBGUpdateOrderLine";
        stmt1 = stmtDeliveryBGUpdateOrderLine;
        stmt1.setTimestamp(1, new java.sql.Timestamp(now));
        stmt1.setInt(2, deliveryBG.w_id);
        stmt1.setInt(3, d_id);
        stmt1.setInt(4, o_id);
        stmt1.executeUpdate();

        // Select the sum(ol_amount) from ORDER_LINE.
        last_stmt = "stmtDeliveryBGSelectSumOLAmount";
        stmt1 = stmtDeliveryBGSelectSumOLAmount;
        stmt1.setInt(1, deliveryBG.w_id);
        stmt1.setInt(2, d_id);
        stmt1.setInt(3, o_id);
        rs = stmt1.executeQuery();
        if (!rs.next()) {
          rs.close();
          throw new Exception("sum(OL_AMOUNT) for ORDER_LINEs with " + " OL_W_ID=" + deliveryBG.w_id
              + " OL_D_ID=" + d_id + " OL_O_ID=" + o_id + " not found");
        }
        sum_ol_amount = rs.getDouble("sum_ol_amount");
        rs.close();

        // Update the CUSTOMER.
        last_stmt = "stmtDeliveryBGUpdateCustomer";
        stmt1 = stmtDeliveryBGUpdateCustomer;
        stmt1.setDouble(1, sum_ol_amount);
        stmt1.setInt(2, deliveryBG.w_id);
        stmt1.setInt(3, d_id);
        stmt1.setInt(4, c_id);
        stmt1.executeUpdate();

        // Recored the delivered O_ID in the DELIVERY_BG
        deliveryBG.delivered_o_id[d_id - 1] = o_id;
      }

      dbConn.commit();
    } catch (SQLException se) {
      log.error("Unexpected SQLException in DELIVERY_BG - stmt = '" +
                last_stmt + "'");
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
