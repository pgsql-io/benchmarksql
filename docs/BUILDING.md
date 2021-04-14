# Building BenchmarkSQL

BenchmarkSQL V6 is meant to be built into a [Docker](https://www.docker.com/)
container and controlled via its Flask based WEB UI and/or API.
This allows for easy deployment of the benchmark driver on servers and cloud
systems while controlling it through a browser or scripted.

## Requirements

The requirements to run BenchmarkSQL are:

[comment]: # (TODO update java version, and package to install.)

 * Java development environment (java-1.8.0-openjdk-devel or newer).
 * Maven build tool for Java.
 * Docker and a user account authorized to use it. This depends on your OS.
On RedHat based systems the usual way is to install Docker via
`sudo yum install -y docker` and make the users, who are allowed to use, its
members of the group **docker** by running the command
`sudo usermod -a -G docker <USERNAME>`.

## Building process

The Java development environment and Maven are required on the build machine
because the Docker container will only have the Java runtime installed. So
the `BenchmarkSQL.jar` file needs to be built outside the container.

After installing the above requirements and cloning the BenchmarkSQL
git repository (assuming username **wieck** and cloned into ~/benchmarksql):

```
$ cd ~/benchmarksql
$ mvn
```

This will create a lot of output:

```
[INFO] Scanning for projects...
[INFO] 
[INFO] ------------------< com.github.pgsql-io:benchmarksql >------------------
[INFO] Building A TPC-C like test tool 6.0.0-SNAPSHOT
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- maven-clean-plugin:2.5:clean (default-clean) @ benchmarksql ---
[INFO] Deleting /Users/wieck/git/benchmarksql-6/target
[INFO] 
[INFO] --- maven-resources-plugin:3.2.0:resources (default-resources) @ benchmarksql ---
[INFO] Using 'UTF-8' encoding to copy filtered resources.
[INFO] Using 'UTF-8' encoding to copy filtered properties files.
[INFO] Copying 49 resources
[INFO] 
[INFO] --- maven-compiler-plugin:3.8.0:compile (default-compile) @ benchmarksql ---
[INFO] Changes detected - recompiling the module!
[INFO] Compiling 18 source files to /Users/wieck/git/benchmarksql-6/target/classes
[INFO] 
[INFO] --- maven-resources-plugin:3.2.0:testResources (default-testResources) @ benchmarksql ---
[INFO] Using 'UTF-8' encoding to copy filtered resources.
[INFO] Using 'UTF-8' encoding to copy filtered properties files.
[INFO] skip non existing resourceDirectory /Users/wieck/git/benchmarksql-6/src/test/resources
[INFO] 
[INFO] --- maven-compiler-plugin:3.8.0:testCompile (default-testCompile) @ benchmarksql ---
[INFO] No sources to compile
[INFO] 
[INFO] --- maven-surefire-plugin:2.12.4:test (default-test) @ benchmarksql ---
[INFO] No tests to run.
[INFO] 
[INFO] --- maven-jar-plugin:2.4:jar (default-jar) @ benchmarksql ---
[INFO] Building jar: /Users/wieck/git/benchmarksql-6/target/BenchmarkSQL.jar
[INFO] 
[INFO] --- maven-dependency-plugin:3.0.0:copy-dependencies (copy-dependencies) @ benchmarksql ---
[INFO] Copying postgresql-42.2.19.jar to /Users/wieck/git/benchmarksql-6/target/lib/postgresql-42.2.19.jar
[INFO] Copying jcc-11.5.5.0.jar to /Users/wieck/git/benchmarksql-6/target/lib/jcc-11.5.5.0.jar
[INFO] Copying mysql-connector-java-8.0.23.jar to /Users/wieck/git/benchmarksql-6/target/lib/mysql-connector-java-8.0.23.jar
[INFO] Copying protobuf-java-3.11.4.jar to /Users/wieck/git/benchmarksql-6/target/lib/protobuf-java-3.11.4.jar
[INFO] Copying jaybird-4.0.3.java11.jar to /Users/wieck/git/benchmarksql-6/target/lib/jaybird-4.0.3.java11.jar
[INFO] Copying mssql-jdbc-9.2.1.jre8.jar to /Users/wieck/git/benchmarksql-6/target/lib/mssql-jdbc-9.2.1.jre8.jar
[INFO] Copying antlr4-runtime-4.7.2.jar to /Users/wieck/git/benchmarksql-6/target/lib/antlr4-runtime-4.7.2.jar
[INFO] Copying log4j-api-2.14.1.jar to /Users/wieck/git/benchmarksql-6/target/lib/log4j-api-2.14.1.jar
[INFO] Copying ojdbc8-21.1.0.0.jar to /Users/wieck/git/benchmarksql-6/target/lib/ojdbc8-21.1.0.0.jar
[INFO] Copying connector-api-1.5.jar to /Users/wieck/git/benchmarksql-6/target/lib/connector-api-1.5.jar
[INFO] Copying checker-qual-3.5.0.jar to /Users/wieck/git/benchmarksql-6/target/lib/checker-qual-3.5.0.jar
[INFO] Copying log4j-core-2.14.1.jar to /Users/wieck/git/benchmarksql-6/target/lib/log4j-core-2.14.1.jar
[INFO] Copying mariadb-java-client-2.7.2.jar to /Users/wieck/git/benchmarksql-6/target/lib/mariadb-java-client-2.7.2.jar
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  5.360 s
[INFO] Finished at: 2021-04-13T17:56:05-05:00
[INFO] ------------------------------------------------------------------------
```

Your "Total time" will most likely not be 3 seconds on the first
run.

[comment]: # (TODO To include the docker creation. docker-maven-plugin can be used.) 

Expect it to run for a few minutes as the resulting Docker
image is about 1.7GB in size and a lot of that will be pulled in
over your Internet connection.

