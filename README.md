BenchmarkSQL
============

BenchmarkSQL is a [GPLv2](./doc/LICENSE.gpl-2.0.txt)
fair-use TPC-C like testing tool.

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

Building
--------

BenchmarkSQL V6 is meant to be built into a [Docker](https://www.docker.com/)
container and controlled via its Flask based WEB UI and/or API.

Requirements:
* Docker and a user account authorized to use it. This depends
  on your OS. On RH based systems the usual way is to install Docker via
  ```sudo yum install -y docker``` and make the users, who are allowed to
  use, it members of the group **docker** by running the command
  ```sudo usermod -a -G docker <USERNAME>```.
* Java development environment (java-1.8.0-openjdk-devel or newer).
* Ant build tool for Java.

The Java development environment and Ant are required on the build machine
because the Docker container will only have the Java runtime installed. So
the BenchmarkSQL .jar files need to be built outside the container.

After installing the above requirements and cloning the BenchmarkSQL
git repository (assuming username **wieck** and cloned into ~/benchmarksql):
```
$ cd ~/benchmarksql
$ ant docker
```
This will create a lot of output:
```
Buildfile: /home/wieck/benchmarksql/build.xml

init:
    [mkdir] Created dir: /home/wieck/benchmarksql/build
    [mkdir] Created dir: /home/wieck/benchmarksql/src/appstemp

appGeneric:
     [copy] Copying 1 file to /home/wieck/benchmarksql/src/appstemp

appPGProc:
     [copy] Copying 1 file to /home/wieck/benchmarksql/src/appstemp

appOraProcReal:

appOraProcDummy:
     [copy] Copying 1 file to /home/wieck/benchmarksql/src/appstemp

appOraProc:

apps:

compile:
    [javac] Compiling 17 source files to /home/wieck/benchmarksql/build

dist:
    [mkdir] Created dir: /home/wieck/benchmarksql/dist
      [jar] Building jar: /home/wieck/benchmarksql/dist/BenchmarkSQL-6.devel.jar

docker:
     [exec] Sending build context to Docker daemon 13.49 MB
     [exec] Step 1/21 : FROM centos:8
     [exec]  ---> 0f3e07c0138f
     ...
     [exec] Step 21/21 : CMD python3 /benchmarksql/FlaskService/main.py
     [exec]  ---> Running in 8f60bf73fa3e
     [exec]  ---> d76b00e4a550
     [exec] Removing intermediate container 8f60bf73fa3e
     [exec] Successfully built d76b00e4a550

BUILD SUCCESSFUL
Total time: 3 seconds
```
Your "Total time" will most likely not be 3 seconds on the first
run. Expect it to run for a few minutes as the resulting Docker
image is about 1.7GB in size and a lot of that will be pulled in
over your internet connection.

Running
-------

Once the Docker image is built a container can be started with the
```service-start.sh```
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
  with a tag **benchmarksql-service. This container is running the
  service under the current user (not root) and it forwards port 5000/tcp
  into the container for the Flask UI and API.

This container will run in the foreground and show the Flask
log for debugging purposes. To run it in the background simply replace the
flags _-it_ with _-d_.

At this point the BenchmarkSQL service is running and you can connect to
it with you browser on 
<a href="http://localhost:5000" target="_blank">http://localhost:5000</a>.
If you created this service on a remote machine, don't forget to open port
5000/tcp in the firewall.


TODO: Point to a tutorial walking through the Flask GUI.

Automation
----------

TODO: Point to a tutorial walking through using the (yet to be written)
command line interface.
