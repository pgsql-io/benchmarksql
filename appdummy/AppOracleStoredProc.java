/*
 * AppOracleStoredProc - TPC-C Transaction Implementation for using
 *			 Stored Procedures on Oracle
 *
 */
import org.apache.log4j.*;

import java.util.*;
import java.sql.*;
import java.math.*;

public class AppOracleStoredProc extends jTPCCApplication
{
    private jTPCC			gdata;
    private org.apache.log4j.Logger	log;
    private int				sut_id;

    public void init(jTPCC gdata, int sut_id, org.apache.log4j.Logger sutLog)
    	throws Exception
    {
    	throw new Exception("Oracle support was not compiled in - please rebuild BenchmarkSQL with -DOracleSupport=true");
    }

    public void finish()
    	throws Exception
    {
    }

    public void executeNewOrder(jTPCCTData.NewOrderData newOrder, boolean trans_rbk)
    	throws Exception
    {
    }

    public void executePayment(jTPCCTData.PaymentData payment)
    	throws Exception
    {
    }

    public void executeOrderStatus(jTPCCTData.OrderStatusData orderStatus)
    	throws Exception
    {
    }

    public void executeStockLevel(jTPCCTData.StockLevelData stockLevel)
    	throws Exception
    {
    }

    public void executeDeliveryBG(jTPCCTData.DeliveryBGData deliveryBG)
    	throws Exception
    {
    }
}
