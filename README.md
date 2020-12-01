BenchmarkSQL
============

BenchmarkSQL is a [GPLv2](./doc/LICENSE.gpl-2.0.txt)
fair-use TPC-C like testing tool.

TPC-C
-----

The TPC-C is an OLTP benchmark defined by the
[Transaction Processing Counil](http://tpc.org). It consists of 9 tables
that are connected with 10 Foreign Key Relationships. Except for the **Item**
table, everything is scaled in cardinality by the number of warehouses (**W**),
that are generated during the initial load of the database.

<img src="./doc/TPC-C_ERD.svg" width="100%"/>

This schema is used by 5 different transactions that produce a variety of
different access patterns on the tables. 

* **Item** is read only.
* **Warehouse**, **District**, **Customer** and **Stock** are read/write.
* **New-Order** is insert, read and delete, like a queue that at any given
  time has approximately W * 9000 rows in it.
* **Order** and **Order-Line** receive inserts and every row inserted will
  have a time delayed update to it, after which the row becomes stale and
  may be read infrequently in the future.
* **History** is insert only.

This is an impressive complexity and set of different access patterns for
such a small schema and number of transaction profiles. It is one of the
reasons why **TPC-C** is still one of the most important database benchmarks
today.

Overview
--------

BenchmarkSQL is implemented in JAVA, using
JDBC to stress test SQL databases. The overall architecture is a
series of data structures, queues and thread groups that handle the
simulated terminals, users and application threads. 

Its architecture allows BenchmarkSQL to drive TPC-C configurations
up to many thousands of warehouses (known as the scaling factor) without
overwhelming the job scheduler of the test driver itself. Yet it is
capable of doing so without sacrificing one of the most important measurements
in a TPC-C, the end-user experienced response time at the terminal.

<img src="./doc/TimedDriver-1.svg" width="100%"/>

Please read the [Full Architecture Description](./doc/TimedDriver.md)
for a detailed explanation of the above diagram.

Building
--------

BenchmarkSQL V6 is meant to be built into a [Docker](https://www.docker.com/)
container and controlled via its Flask based WEB UI and/or API. This allows
for easy deployment of the benchmark driver on servers and cloud systems
while controlling it through a browser or scripted.

See the [build instructions](./doc/BUILDING.md) for details.

Launching the Service Container
-------------------------------

Once the Docker image is built a container can be started with the
```service-start.sh``` script.
```
#!/bin/sh

mkdir -p ./service_data

docker run --rm -it --name benchmarksql-service \
	--publish 5000:5000 \
	--volume "`pwd`/service_data:/service_data" \
	--user `id -u`:`id -g` \
	benchmarksql-v6.0
```
* It creates a local directory to preserve configuration and result data.
  This directory is mounted into the container.
* It runs the docker image **benchmarksql-v6.0** as a container
  with a tag **benchmarksql-service**. This container is running the
  service under the current user (not root) and it forwards port 5000/tcp
  into the container for the Flask UI and API.

This container will run in the foreground and show the Flask
log for debugging purposes. To run it in the background simply replace the
flags _-it_ with _-d_.

At this point the BenchmarkSQL service is running and you can connect to
it with you browser on http://localhost:5000

If you created this service on a remote machine, don't simply open port
5000/tcp in the firewall. **Keep in mind that the configuration file,
controlling the benchmark run settings, contains all the connection
credentials for your database in clear text!**
The plan is to substantially enhance the
Flask GUI and API with user and configuration management. Then provide
instructions on how to secure the container behind an
[nginx](https://www.nginx.com/) reverse proxy for encryption. In the meantime
please use ssh to tunnel port 5000/tcp securely to the benchmark
driver machine. Since that tunnel is only for the WEB UI and API traffic,
it won't affect the benchmark results at all.

Configuring and Running a Benchmark
-----------------------------------

BenchmarkSQL is configured with files in the JAVA properties format.
A detailed description of all parameters in that file can be found
[here](./doc/PROPERTIES.md).

TODO: Tutorial of how to use the WEB UI

Automation
----------

TODO: Point to a tutorial walking through using the (yet to be written)
command line interface.
