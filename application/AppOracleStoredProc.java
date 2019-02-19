/*
 * AppOracleStoredProc - TPC-C Transaction Implementation for using
 *			 Stored Procedures on Oracle
 *
 */
import org.apache.log4j.*;

import java.util.*;
import java.sql.*;
import java.math.*;
import oracle.jdbc.*;
import oracle.sql.*;

public class AppOracleStoredProc extends jTPCCApplication
{
    private jTPCC			gdata;
    private org.apache.log4j.Logger	log;
    private int				sut_id;

    private Connection			dbConn;

    public String			stmtNewOrderStoredProc;
    public String			stmtPaymentStoredProc;
    public String			stmtOrderStatusStoredProc;
    public String			stmtStockLevelStoredProc;
    public String			stmtDeliveryBGStoredProc;


    public void init(jTPCC gdata, int sut_id, org.apache.log4j.Logger sutLog)
    	throws Exception
    {
	Properties	dbProps;

    	this.gdata	= gdata;
	this.log	= sutLog;
	this.sut_id	= sut_id;

	// Connect to the database
	dbProps = new Properties();
	dbProps.setProperty("user", gdata.iUser);
	dbProps.setProperty("password", gdata.iPassword);
	dbConn = DriverManager.getConnection(gdata.iConn, dbProps);
	dbConn.setAutoCommit(false);

	// Statement for NEW_ORDER
	stmtNewOrderStoredProc =
		"{call tpccc_oracle.oracle_proc_new_order(?, ?, ?, ?, ?, ?, ?, ?, ?, ?"  +
		",?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";

	// Statement for PAYMENT
	stmtPaymentStoredProc =
		"{call tpccc_oracle.oracle_proc_payment(?, ?, ?, ?, ?, ?, ?, ?, ?, ?," +
		" ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?," +
		" ?, ?, ?, ?, ?)}";

	// Statement for ORDER_STATUS
	stmtOrderStatusStoredProc =
		"{call tpccc_oracle.oracle_proc_order_status(?, ?, ?, " +
		"?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";

	// Statement for STOCK_LEVEL
	stmtStockLevelStoredProc =
		"{call tpccc_oracle.oracle_proc_stock_level(?, ?, ?, ?)}";

	// Statement for DELIVERY_BG
	stmtDeliveryBGStoredProc =
		"call tpccc_oracle.oracle_proc_delivery_bg(?, ?, ?, ?)";

    	dbConn.commit();
    }

    public void finish()
    	throws Exception
    {
    	if (dbConn != null)
	{
	    dbConn.close();
	    dbConn = null;
	}
    }

    public void executeNewOrder(jTPCCTData.NewOrderData newOrder, boolean trans_rbk)
    	throws Exception
    {
	CallableStatement	stmt;

	try {
	    // Execute the stored procedure for NEW_ORDER
	    ArrayDescriptor oracleIntArray =
		ArrayDescriptor.createDescriptor("INT_ARRAY", dbConn);
	    ArrayDescriptor oracleDecimalArray =
		ArrayDescriptor.createDescriptor("NUM_ARRAY", dbConn);
	    ArrayDescriptor oracleCharArray =
		ArrayDescriptor.createDescriptor("CHAR_ARRAY", dbConn);
	    ArrayDescriptor oracleVarcharArray =
		ArrayDescriptor.createDescriptor("VARCHAR24_ARRAY", dbConn);

	    stmt = dbConn.prepareCall(stmtNewOrderStoredProc);

	    int[] ol_supply_w_id = new int[15];
	    int[] ol_i_id = new int[15];
	    int[] ol_quantity = new int [15];

	    for (int i=0; i < 15; i++)
	    {
		ol_supply_w_id[i] = newOrder.ol_supply_w_id[i];
		ol_i_id[i] = newOrder.ol_i_id[i];
		ol_quantity[i] = newOrder.ol_quantity[i];
	    }


	    ARRAY ora_ol_supply_w_id = new ARRAY(oracleIntArray, dbConn, ol_supply_w_id);
	    ARRAY ora_ol_i_id = new ARRAY(oracleIntArray, dbConn, ol_i_id);
	    ARRAY ora_ol_quantity = new ARRAY(oracleIntArray, dbConn, ol_quantity);

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
	    ARRAY ora_ol_amount = ((OracleCallableStatement)stmt).getARRAY(7);
	    ARRAY ora_i_name = ((OracleCallableStatement)stmt).getARRAY(8);
	    ARRAY ora_i_price = ((OracleCallableStatement)stmt).getARRAY(9);
	    ARRAY ora_s_quantity = ((OracleCallableStatement)stmt).getARRAY(10);
	    ARRAY ora_brand_generic = ((OracleCallableStatement)stmt).getARRAY(11);

	    newOrder.w_tax = stmt.getDouble(12);
	    newOrder.d_tax = stmt.getDouble(13);
	    newOrder.o_id = stmt.getInt(14);
	    newOrder.o_entry_d = stmt.getTimestamp(15).toString();
	    newOrder.o_ol_cnt = stmt.getInt(16);
	    newOrder.total_amount = stmt.getDouble(17);
	    newOrder.c_last = stmt.getString(18);
	    newOrder.c_credit = stmt.getString(19);
	    newOrder.c_discount = stmt.getDouble(20);

	    double[] ol_amount_arr = ora_ol_amount.getDoubleArray();
	    String[] i_name_arr = (String[]) ora_i_name.getArray();
	    double[] i_price_arr = ora_i_price.getDoubleArray();
	    int[] s_quantity_arr = ora_s_quantity.getIntArray();
	    String[] brand_generic_arr = (String[]) ora_brand_generic.getArray();

	    for(int i = 0; i < 15; i++)
	    { if( i < ol_amount_arr.length){
		newOrder.ol_amount[i] = ol_amount_arr[i];
		newOrder.i_name[i] = i_name_arr[i];
		newOrder.i_price[i] = i_price_arr[i];
		newOrder.s_quantity[i] = s_quantity_arr[i];
		newOrder.brand_generic[i] = brand_generic_arr[i];
		}
	    }

	    newOrder.execution_status = new String("Order placed");

	    dbConn.commit();
	    stmt.close();

	}
	catch (SQLException se)
        {
	    boolean expected = false;

            if (trans_rbk && se.getMessage().startsWith("ORA-20001: Item number is not valid"))
            {
                newOrder.execution_status = new String(
                                "Item number is not valid");
	    	expected = true;
            }
            else
            {
                log.error("Unexpected SQLException in NEW_ORDER");
                log.error("message: '" + se.getMessage() + "' trans_rbk=" + trans_rbk);
                for (SQLException x = se; x != null; x = x.getNextException())
                    log.error(x.getMessage());
                se.printStackTrace();
            }

            try
            {
                dbConn.rollback();
            }
            catch (SQLException se2)
            {
                throw new Exception("Unexpected SQLException on rollback: " +
                                se2.getMessage());
            }
	    if (!expected)
	    	throw se;
        }
        catch (Exception e)
        {
            try
            {
                dbConn.rollback();
            }
            catch (SQLException se2)
            {
                throw new Exception("Unexpected SQLException on rollback: " +
                                se2.getMessage());
            }
            throw e;
        }
    }

    public void executePayment(jTPCCTData.PaymentData payment)
    	throws Exception
    {
	CallableStatement		stmt;

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

	}
	catch (SQLException se)
	{
	    log.error("Unexpected SQLException in PAYMENT");
	    log.error("message: '" + se.getMessage());
	    for (SQLException x = se; x != null; x = x.getNextException())
		log.error(x.getMessage());
	    se.printStackTrace();

	    try
	    {
		dbConn.rollback();
	    }
	    catch (SQLException se2)
	    {
		throw new Exception("Unexpected SQLException on rollback: " +
		    se2.getMessage());
	    }

	    throw se;
	}
	catch (Exception e)
	{
	    try
	    {
		dbConn.rollback();
	    }
	    catch (SQLException se2)
	    {
		throw new Exception("Unexpected SQLException on rollback: " +
		    se2.getMessage());
	    }
	    throw e;
	}
    }

    public void executeOrderStatus(jTPCCTData.OrderStatusData orderStatus)
    	throws Exception
    {
	CallableStatement	    stmt;

	try {
	    // Execute the stored procedure for ORDER_STATUS
	    ArrayDescriptor oracleIntArray =
		ArrayDescriptor.createDescriptor("INT_ARRAY", dbConn);
	    ArrayDescriptor oracleDecimalArray =
		ArrayDescriptor.createDescriptor("NUM_ARRAY", dbConn);
	    ArrayDescriptor oracleTimestampArray =
		ArrayDescriptor.createDescriptor("TIMESTAMP_ARRAY", dbConn);

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

	    ARRAY ora_supply_w_id_arr = ((OracleCallableStatement)stmt).getARRAY(11);
	    ARRAY ora_i_id_arr = ((OracleCallableStatement)stmt).getARRAY(12);
	    ARRAY ora_quantity_arr = ((OracleCallableStatement)stmt).getARRAY(13);
	    ARRAY ora_amount_arr = ((OracleCallableStatement)stmt).getARRAY(14);
	    ARRAY ora_delivery_d_arr = ((OracleCallableStatement)stmt).getARRAY(15);

	    supply_w_id_arr = ora_supply_w_id_arr.getIntArray();
	    i_id_arr = ora_i_id_arr.getIntArray();
	    quantity_arr = ora_quantity_arr.getIntArray();
	    amount_arr = ora_amount_arr.getDoubleArray();
	    delivery_d = (String[]) ora_delivery_d_arr.getArray();

	    for (int i = 0; i < amount_arr.length; i++)
            {
                orderStatus.ol_supply_w_id[i] = supply_w_id_arr[i];
                orderStatus.ol_i_id[i] = i_id_arr[i];
                orderStatus.ol_quantity[i] = quantity_arr[i];
                orderStatus.ol_amount[i] = amount_arr[i];
                if (delivery_d[i] == null)
                    orderStatus.ol_delivery_d[i] = " ";
                else
                    orderStatus.ol_delivery_d[i] = delivery_d[i];
            }

	    dbConn.commit();
	    stmt.close();

	}
	catch (SQLException se)
        {
            log.error("Unexpected SQLException in ORDER_STATUS");
            log.error("message: '" + se.getMessage());
            for (SQLException x = se; x != null; x = x.getNextException())
                log.error(x.getMessage());
            se.printStackTrace();

            try
            {
                dbConn.rollback();
            }
                catch (SQLException se2)
            {
                throw new Exception("Unexpected SQLException on rollback: " +
                se2.getMessage());
            }

	    throw se;
        }
        catch (Exception e)
        {
            try
            {
                dbConn.rollback();
            }
            catch (SQLException se2)
            {
                throw new Exception("Unexpected SQLException on rollback: " +
                se2.getMessage());
            }
            throw e;
        }
    }

    public void executeStockLevel(jTPCCTData.StockLevelData stockLevel)
    	throws Exception
    {
	CallableStatement		    stmt;

	try {

	    // Execute the stored procedure for STOCK_LEVEL
	    stmt =  dbConn.prepareCall(stmtStockLevelStoredProc);
	    stmt.setInt(1,stockLevel.w_id);
	    stmt.setInt(2,stockLevel.d_id);
	    stmt.setInt(3,stockLevel.threshold);
	    stmt.registerOutParameter(4, Types.INTEGER);

	    stmt.executeUpdate();

	    // The stored proc succeeded. Extract the results.
	    stockLevel.low_stock=stmt.getInt(4);

	    dbConn.commit();
	    stmt.close();

	}
	catch (SQLException se)
	{
	    log.error("Unexpected SQLException in NEW_ORDER");
	    log.error("message: '" + se.getMessage());
	    for (SQLException x = se; x != null; x = x.getNextException())
		log.error(x.getMessage());
	    se.printStackTrace();

	    try
	    {
		dbConn.rollback();
	    }
	    catch (SQLException se2)
	    {
		throw new Exception("Unexpected SQLException on rollback: " +
		    se2.getMessage());
	    }
	    throw se;
	}
	catch (Exception e)
	{
	    try
	    {
		dbConn.rollback();
	    }
	    catch (SQLException se2)
	    {
		throw new Exception("Unexpected SQLException on rollback: " +
		    se2.getMessage());
	    }
	    throw e;
	}
    }

    public void executeDeliveryBG(jTPCCTData.DeliveryBGData deliveryBG)
    	throws Exception
    {
	CallableStatement	    stmt;

	try {
	    // Execute the stored procedure for DELIVERY_BG
	    ArrayDescriptor oracleIntArray =
		ArrayDescriptor.createDescriptor("INT_ARRAY", dbConn);

	    int[] delivery_array = new int[10];


	    stmt = dbConn.prepareCall(stmtDeliveryBGStoredProc);
	    stmt.setInt(1, deliveryBG.w_id);
	    stmt.setInt(2, deliveryBG.o_carrier_id);
	    stmt.setTimestamp(3, Timestamp.valueOf(deliveryBG.ol_delivery_d));
	    stmt.registerOutParameter(4, OracleTypes.ARRAY, "INT_ARRAY");

	    stmt.executeUpdate();

	    // The stored proc succeeded. Extract the results.
	    ARRAY ora_array = ((OracleCallableStatement)stmt).getARRAY(4);
	    delivery_array = ora_array.getIntArray();
	    deliveryBG.delivered_o_id = new int[delivery_array.length];

	    for(int i = 0; i < 10; i++)
	    {
		deliveryBG.delivered_o_id[i] = delivery_array[i];
	    }
	    dbConn.commit();
	    stmt.close();
	}
	catch (SQLException se)
        {
            log.error("Unexpected SQLException in DELIVERY_BG");
            log.error("message: '" + se.getMessage());
            for (SQLException x = se; x != null; x = x.getNextException())
		log.error(x.getMessage());
		se.printStackTrace();

            try
            {
                dbConn.rollback();
            }
            catch (SQLException se2)
            {
                throw new Exception("Unexpected SQLException on rollback: " +
                    se2.getMessage());
            }
	    throw se;
        }
        catch (Exception e)
        {
            try
            {
                dbConn.rollback();
            }
            catch (SQLException se2)
            {
                throw new Exception("Unexpected SQLException on rollback: " +
                    se2.getMessage());
            }
            throw e;
        }
    }
}
