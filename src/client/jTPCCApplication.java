/*
 * jTPCCApplication - Dummy of the DB specific implementation of the
 * 		      TPC-C Transactions
 *
 */

import org.apache.log4j.*;

import java.sql.*;

public class jTPCCApplication
{
    public void init(jTPCC gdata, int sut_id, org.apache.log4j.Logger sutLog)
    	throws Exception
    {
    }

    public void finish()
    	throws Exception
    {
    }

    public void executeNewOrder(jTPCCTData.NewOrderData screen, boolean trans_rbk)
    	throws Exception
    {
    }

    public void executePayment(jTPCCTData.PaymentData screen)
    	throws Exception
    {
    }

    public void executeOrderStatus(jTPCCTData.OrderStatusData screen)
    	throws Exception
    {
    }

    public void executeStockLevel(jTPCCTData.StockLevelData screen)
    	throws Exception
    {
    }

    public void executeDeliveryBG(jTPCCTData.DeliveryBGData screen)
    	throws Exception
    {
    }
}
