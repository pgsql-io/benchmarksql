BenchmarkSQL
============

BenchmarkSQL is a GPLv2 fair-use TPC-C like testing tool.

Overview
--------

BenchmarkSQL is a TPC-C like testing tool implemented in JAVA, using
JDBC to stress test SQL databases. The overall architecture is a
series of data structures, queues and thread groups that handle the
simulated terminals, users and application threads. 

Its architecture allows BenchmarkSQL to drive TPC-C configurations
up to many thousands of warehouses (known as the scaling factor) without
overwhelming the job scheduler of the test driver itself. Yet it is
capable of doing so without sacrificing the most important measurement
in a TPC-C: the end-user experienced response time at the terminal.

<img src="./doc/TimedDriver-1.svg" width="100%"/>

Please read the [Full Architecture Description](./doc/TimedDriver.md)
for a detailed explanation of the above diagram.

Installation
------------

TODO: Describe building the Docker container and running it.

Running
-------

TODO: Point to a tutorial walking through the Flask GUI.

Automation
----------

TODO: Point to a tutorial walking through using the (yet to be written)
command line interface.
