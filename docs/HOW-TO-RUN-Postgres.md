# Instructions for running BenchmarkSQL on PostgreSQL

Please follow the general instruction for any RDBMS in the
[How to run section](HOW-TO-RUN.md).

## Create a user and a database

As Unix user `postgres` use the `psql` shell to connect to the `postgres`
database and issue the `CREATE USER` and `CREATE DATABASE` commands.

```
$ psql postgres
psql (9.5.2)
Type "help" for help.

postgres=# CREATE USER benchmarksql WITH ENCRYPTED PASSWORD 'changeme';
postgres=# CREATE DATABASE benchmarksql OWNER benchmarksql;
postgres=# \q
```

