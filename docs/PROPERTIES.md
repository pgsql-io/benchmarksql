# BenchmarkSQL Properties Files


BenchmarkSQL is configured with files in the Java properties format.
The parameters in these files control which JDBC driver to load, what the
database type is, the connection credentials, scaling and so on.

## Driver and Connection Parameters

| Parameter     | Description                     | Example                                |
| ------------- | ------------------------------- | -------------------------------------- |
| `db`          | Database type<br/>This is a string of *firebird*, *mariadb*, *oracle*, *postgres*, *transact-sql* or *babelfish*. There are some differences in SQL dialects that the **Generic** application will handle based on this parameter.<br/>An example of this is the handling of **FOR UPDATE**. MSSQL only allows the SQL Standard syntax for that in cursors, but supports a hint **WITH(UPDLOCK)** in a regular **SELECT**. | postgres |
| `driver`      | JDBC driver class name to load. | org.postgresql.Driver                  |
| `application` | The client side application type<br/>BenchmarkSQL supports all database types in the *Generic* application type, which is using JDBC.PreparedStatement() and business logic implemented in the client only. The two alternatives are *PostgreSQLStoredProc* and *OracleStoredProc*, which implement the business logic in stored procedures written in PL/pgSQL or PL/SQL respectively. The BenchmarkSQL client is still generating all the screen input, transmits it to the database and receives all the screen output back. The main benefit of these implementations is that they greatly reduce the number of network round trips between application and database. | Generic |
| `conn`        | JDBC Connection URI             | jdbc:postgresql://dbhost/benchmarksql1 |
| `user`        | Database User Name              | benchmarksql                           |
| `password`    | Database User Password          | ChangeOn1nstall                        |

## Scaling Parameters

| Parameter                   | Description  | Example  |
| --------------------------- | ------------ | -------- |
| `warehouses`                | Overall Database Size Scaling Parameter<br/>This not only affects the size of the initial database but how many Terminals BenchmarkSQL will simulate. | 2000 |
| `loadWorkers`               | The number of parallel threads used to create the initial database content. Should based on the CPU and IO capacity of the database server. | 8 |
| `monkeys`                   | The number of monkey threads used to process Terminal input and output. Please refer to the [architecture documentation](./TimedDriver.md) for details. | 8 |
| `sutThreads`                | The number of application thread to launch. BenchmarkSQL will create this many parallel database connections to process the incoming requests. | 80 |
| `maxDeliveryBGThreads`      | The maximum number of sutThreads that at any time are allowed to process the background part of a Delivery transaction. | 40 |
| `maxDeliveryBGPerWarehouse` | The maximum number of sutThreads that are allowed to process background part of a Delivery transaction "for the same warehouse". | 1 |

Notes on Delivery Background transactions: The TPC-C has this "bulk"
transaction, called Delivery.
It picks the oldest, not yet delivered `Order` of each `District` of one
`Warehouse` and "delivers" it.
This involves selecting 10 rows from the `New-Order` table `FOR UPDATE`, updating
the corresponding 10 Order rows as well as the on average 100 `Order-Line` rows
and more.

This background transaction has a very relaxed 80 seconds to complete.
Obviously the selection of those `New-Order` rows, all belonging to the same
`Warehouse`, already create a locking conflict, so limiting the concurrent
number of Delivery transactions per Warehouse to 1 is a natural choice.
The `maxDeliveryBGThreads` parameter is meant as a control mechanism to prevent
all SUT threads from being busy with this transaction type, while transactions
with tighter response time constraints are waiting in the SUT FiFo queue.

## Timing and Test Duration Parameters

| Parameter                     | Description  | Example  |
| ----------------------------- | ------------ | -------- |
| `rampupMins`                  | Delay in minutes before the actual Benchmark measurement begins. Any transactions executed before this delay are ignored in the statistics and reports. This should be greater that the rampupTerminalMins parameter to give the database some time to level out at the full load. | 30 |
| `rampupSUTMins`               | Duration in minutes over which the SUT threads (database connections) are launched. This should be lower or equal to the rampupTerminalMins. | 15 |
| `rampupTerminalMins`          | Duration in minutes over which the simulated Terminals are launched. The Terminals are doing all the keying and thinking delays, so spreading the launch of terminals over some time will cause the transaction load to gradually increase, instead of coming as a 0-100 in zero seconds onslaught. This will give the database time to keep up with the load while warming up caches, instead of building up a large backlog of requests in the client application. | 20 |
| `runMins`                     | Duration of the actual benchmark measurement in minutes. This should be long enough to cover at least one checkpoint. To get a real picture of how the database behaves several hours or even days are recommended. | 300 |
| `reportIntervalSecs`          | Interval in seconds at which the test driver is reporting the current average number to transactions per minute | 60 |
| `restartSUTThreadProbability` | Probability that the SUT threads will schedule the launch of a replacement and terminate after finishing a request. | 0.001 |

## Throughput Tuning Parameters

**Warning: Changing these parameters from their 1.0 default values
will cause the benchmark result to violate the TPC-C timing requirements.**

That said, the TPC-C benchmark specification was created at a time, when block
terminals and mainframe systems were state of the art.
The ratio between CPU and memory requirements compared to storage size are
outdated.
Todays databases see a lot more transactions per GB than databases back then.

The following parameters are an attempt to allow users control over how far
they want to scale up the transaction frequency per warehouse.
The examples show speeding up the attempted rate of transactions 10 times.

| Parameter              | Description  | Example |
| ---------------------- | ------------ | ------- |
| `keyingTimeMultiplier` | Keying is the simulated time, the user needs to fill the input screen. This is a fixed number of seconds dependent on the transaction type. | 0.1 |
| `thinkTimeMultiplier`  | Similar to the Keying the Trink time is the simulated time, the user needs to process the transaction outcome by reading the output screen. This is a random time with a defined mean per transaction time. | 0.1 |

## Transaction Mix Parameters

The TPC-C specification requires a minimum percentage for transaction types:

* 43.0% Payment.
* 4.0% Order-Status.
* 4.0% Stock-Level.
* 4.0% Delivery.

What is missing from this is the percentage of `New-Order` transactions.
The specification only requires a MINIMUM of 43.0% Payment.
So the benchmark result is invalid should it only have 42.98% of them.

BenchmarkSQL uses a random number generator to pick the next transaction
per terminal.
In order to avoid the above problem it is recommended to specify the required
percentages a little bit higher.

| Parameter           | Description                                          | Example |
| ------------------- | ---------------------------------------------------- | ------- |
| `paymentWeight`     | Probability of Payment transactions in percent.      | 43.1    |
| `orderStatusWeight` | Probability of Order-Status transactions in percent. | 4.1     |
| `stockLevelWeight`  | Probability of Stock-Level transactions in percent.  | 4.1     |
| `deliveryWeight`    | Probability of Delivery transactions in percent.     | 4.1     |
