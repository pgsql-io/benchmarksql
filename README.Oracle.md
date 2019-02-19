Creating a benchmark database on Oracle
=======================================

The following assumes a default installation of oracle-xe-11.2.0-1.0.

Creating the benchmarksql user run the following commands in sqlplus
under the sysdba account:

<<_EOF_

CREATE USER benchmarksql
	IDENTIFIED BY "bmsql1"
	DEFAULT TABLESPACE users
	TEMPORARY TABLESPACE temp;

GRANT CONNECT TO benchmarksql;
GRANT CREATE PROCEDURE TO benchmarksql;
GRANT CREATE SEQUENCE TO benchmarksql;
GRANT CREATE SESSION TO benchmarksql;
GRANT CREATE TABLE TO benchmarksql;
GRANT CREATE TRIGGER TO benchmarksql;
GRANT CREATE TYPE TO benchmarksql;
GRANT UNLIMITED TABLESPACE TO benchmarksql;

_EOF_

Compiling with Oracle Suport
============================

To use the OracleStoredProc Application driver BenchmarkSQL must be
compiled with special Oracle support. This is because the Oracle JDBC
driver does not support the JDBC 4.0 feature createArrayOf(). The
proprietary features needed instead can only be compiled if the Oracle
JDBC .jar file is present, which we cannot redistribute for licensing
reasons. 

After copying the ojdbc?.jar file into ./lib/oracle execute

```
ant -DOracleSupport=true
```

