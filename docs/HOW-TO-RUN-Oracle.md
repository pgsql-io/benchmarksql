# Instructions for running BenchmarkSQL on Oracle

Please follow the general instruction for any RDBMS in the
[How to run section](HOW-TO-RUN.md).

## Create a user and a database

The following assumes a default installation of Oracle.

Creating the `benchmarksql` user run the following commands in `sqlplus`
under the `sysdba` account:

```
<<_EOF_

CREATE USER benchmarksql
	IDENTIFIED BY "password"
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
```

