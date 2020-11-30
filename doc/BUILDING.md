Building BenchmarkSQL
=====================

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
