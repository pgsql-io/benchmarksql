# BenchmarkSQL on AWS EC2 running Amazon Linux 2

## Contents
* [Placement and Sizing](#placement-and-sizing)
* [Installing Prerequisites](#installing-prereqesites)
  * [Installing EPEL](#installing-epel)
  * [Installing Java 11](#installing-java-11)
  * [Installing git](#installing-git)
  * [Installing Python packages](#installing-python-packages)
  * [Installing Maven](#installing-maven)
* [Cloning and Building BenchmarkSQL](#cloning-and-building-benchmarksql)
* [Launching the Flask UI and connecting to it](#launching-the-flask-ui-and-connecting-to-it)
  * [First test launching the UI](#first-test-launching-the-ui)
  * [Creating the secure shell port forward](#creating-the-secure-shell-port-forward)
  * [Connecting to the UI](#connecting-to-the-ui)
  * [Relaunching the UI disconnect-safe](#relaunching-the-ui-disconnect-safe)


## Placement and Sizing

In order to perform benchmarking against any cloud
database the BenchmarkSQL application itself needs to be running in
the cloud as well. In the case of AWS it is preferable to run in
the same availability zone. This minimizes network latency, which
would otherwise negatively impact the performance or even make the
results unpredictable.

Properly performing a benchmark also requires that the driver,
the EC2 instance running the BenchmarkSQL application, has enough
CPU power and network bandwidth to generate client requests fast
enough. It would be a completely false performance result if the
number of transactions per minute fell short because the CPU of the
driver was overloaded. It is recommended that the driver EC2 instance
has at least half the number of vCPUs that the largest database instance
under test will have. Oversizing the driver instance will have no
negative influence on the test at all, but it runs up the cost for
no benefit. 

This tutorial will not teach you how to actually create a new EC2
instance. It is assumed that you have one and that you can log into
it as the ec2-user via ssh(1) with the downloaded key pair.

At this point log into the EC2 instance:
```
ssh -i ~/.ssh/AWSKEY.pem ec2-user@EC2IPADDR
```

## Installing Prerequisites

The following components are needed to build and run BenchmarkSQL:

* Extra Packages for Enterprise Linux (EPEL)
* Java 11 Development
* Git
* Python3
* Python numpy
* Python matplotlib
* Python jinja2
* Python Flask
* Python jproperties
* Apache Maven 3.1 or higher

### Installing EPEL

The Extra Packages for Enterprise Linux are available via
the amazon-linux-extras repository:
```
sudo amazon-linux-extras install -y epel
```
Example:
```
[ec2-user@ip-172-31-70-6 ~]$ sudo amazon-linux-extras install -y epel
Installing epel-release
Loaded plugins: extras_suggestions, langpacks, priorities, update-motd
Cleaning repos: amzn2-core amzn2extra-docker amzn2extra-epel epel
23 metadata files removed
8 sqlite files removed
0 metadata files removed
Loaded plugins: extras_suggestions, langpacks, priorities, update-motd
amzn2-core                                               | 3.7 kB     00:00
amzn2extra-docker                                        | 3.0 kB     00:00
amzn2extra-epel                                          | 3.0 kB     00:00
epel/x86_64/metalink                                     |  12 kB     00:00
epel                                                     | 4.7 kB     00:00
(1/10): amzn2-core/2/x86_64/updateinfo                     | 405 kB   00:00
(2/10): amzn2-core/2/x86_64/group_gz                       | 2.5 kB   00:00

[more output trimmed]
```

### Installing Java 11

Java OpenJDK 11 is also available via the amazon-linux-extras
repository:
```
sudo amazon-linux-extras install -y java-openjdk11
```
Example:
```
[ec2-user@ip-172-31-70-6 ~]$ sudo amazon-linux-extras install -y java-openjdk11
Installing java-11-openjdk
Loaded plugins: extras_suggestions, langpacks, priorities, update-motd
Cleaning repos: amzn2-core amzn2extra-docker amzn2extra-epel
              : amzn2extra-java-openjdk11 epel
18 metadata files removed
8 sqlite files removed
0 metadata files removed
Loaded plugins: extras_suggestions, langpacks, priorities, update-motd
amzn2-core                                               | 3.7 kB     00:00
amzn2extra-docker                                        | 3.0 kB     00:00
amzn2extra-epel                                          | 3.0 kB     00:00
amzn2extra-java-openjdk11                                | 3.0 kB     00:00
epel/x86_64/metalink                                     |  12 kB     00:00
epel                                                     | 4.7 kB     00:00
(1/12): amzn2-core/2/x86_64/group_gz                       | 2.5 kB   00:00
(2/12): amzn2-core/2/x86_64/updateinfo                     | 405 kB   00:00

[more output trimmed]
```

### Installing git

The Git version control system is available from the Amazon core
repository:
```
sudo yum install -y git
```
Example:
```
[ec2-user@ip-172-31-70-6 ~]$ sudo yum install -y git
Loaded plugins: extras_suggestions, langpacks, priorities, update-motd
217 packages excluded due to repository priority protections
Resolving Dependencies
--> Running transaction check
---> Package git.x86_64 0:2.32.0-1.amzn2.0.1 will be installed

[more output trimmed]
```

### Installing Python packages

The required Python packages should normally NOT be installed by
the root user. It can break dependencies down the road. The correct
way would be to create a Python virtual environment for the ec2-user
and install them there. But since this is a throw-away benchmark
driver system, we are taking a shortcut and do it as root anyway:
```
sudo pip3 install numpy matplotlib jinja2 flask jproperties
```
Example:
```
[ec2-user@ip-172-31-70-6 ~]$ sudo pip3 install numpy matplotlib jinja2 flask jproperties
WARNING: Running pip install with root privileges is generally not a good idea. Try `pip3 install --user` instead.
Collecting numpy
  Downloading numpy-1.21.2-cp37-cp37m-manylinux_2_12_x86_64.manylinux2010_x86_64.whl (15.7 MB)
     |████████████████████████████████| 15.7 MB 16.7 MB/s

[more output trimmed]

Successfully installed MarkupSafe-2.0.1 Werkzeug-2.0.1 click-8.0.1 cycler-0.10.0 flask-2.0.1 importlib-metadata-4.6.4 itsdangerous-2.0.1 jinja2-3.0.1 jproperties-2.1.1 kiwisolver-1.3.1 matplotlib-3.4.3 numpy-1.21.2 pillow-8.3.1 pyparsing-2.4.7 python-dateutil-2.8.2 six-1.16.0 typing-extensions-3.10.0.0 zipp-3.5.0
```

### Installing Maven

As of this writing the version of Apache Maven in the Amazon core
repository is only 3.0.5. BenchmarkSQL requires at least 3.1 because
of a bug in the resources plugin that was only fixed as of that
version. This means that we need to download and install a newer
version manually. The latest stable version of Maven as of this writing
is 3.8.2. You may want to check all of this and adjust if needed.
```
cd /tmp
sudo wget https://mirrors.sonic.net/apache/maven/maven-3/3.8.2/binaries/apache-maven-3.8.2-bin.tar.gz
sudo tar xf /tmp/apache-maven-3.8.2-bin.tar.gz -C /opt
sudo rm /tmp/apache-maven-3.8.2-bin.tar.gz
sudo ln -s /opt/apache-maven-3.8.2 /opt/maven
```
We also need to create a file in ```/etc/profile.d``` that sets
a few environment variables so that Maven and the correct Java
version are found:
```
sudo tee /etc/profile.d/maven.sh <<_EOF_
export JAVA_HOME=/usr/lib/jvm/jre-11-openjdk
export M2_HOME=/opt/maven
export MAVEN_HOME=/opt/maven
export PATH=\${M2_HOME}/bin:\${PATH}
_EOF_
sudo chmod 755 /etc/profile.d/maven.sh
# Source this now
. /etc/profile.d/maven.sh
cd
```
With all that done check the Maven and Java versions:
```
mvn -version
```
**Make sure that in the following output the Maven version is 3.8.2
and the Java version is 11.**
```[ec2-user@ip-172-31-70-6 tmp]$ mvn -version
Apache Maven 3.8.2 (ea98e05a04480131370aa0c110b8c54cf726c06f)
Maven home: /opt/maven
Java version: 11.0.11, vendor: Red Hat, Inc., runtime: /usr/lib/jvm/java-11-openjdk-11.0.11.0.9-1.amzn2.0.1.x86_64
Default locale: en_US, platform encoding: UTF-8
OS name: "linux", version: "4.14.243-185.433.amzn2.x86_64", arch: "amd64", family: "unix"
```

## Cloning and Building BenchmarkSQL

Back in the ec2-user's $HOME directory clone the BenchmarkSQL
github repository and checkout the latest stable release tag:
```
git clone https://github.com/pgsql-io/benchmarksql.git
cd benchmarksql
git checkout REL6_0
```
Example:
```
[ec2-user@ip-172-31-70-6 ~]$ git clone https://github.com/pgsql-io/benchmarksql.git
Cloning into 'benchmarksql'...
remote: Enumerating objects: 2371, done.
remote: Counting objects: 100% (2371/2371), done.
remote: Compressing objects: 100% (971/971), done.
remote: Total 2371 (delta 1374), reused 2254 (delta 1260), pack-reused 0
Receiving objects: 100% (2371/2371), 5.55 MiB | 26.69 MiB/s, done.
Resolving deltas: 100% (1374/1374), done.
[ec2-user@ip-172-31-70-6 benchmarksql]$ cd benchmarksql
[ec2-user@ip-172-31-70-6 benchmarksql]$ git checkout REL6_0
HEAD is now at 5dc7881 Add change log for version 6.0
```

Build the project:
```
mvn
```
On the first ever run Maven will download all the dependencies like
JDBC drivers and other Java packages. So there will be a lot of output
scrolling by. The important bit at the end should look like:
```
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  14.346 s
[INFO] Finished at: 2021-08-20T13:26:56Z
[INFO] ------------------------------------------------------------------------
```

## Launching the Flask UI and connecting to it

BenchmarkSQL comes with a Flask based WEB UI. The UI at this point
is rudimentary but fully functional. For security reasons (there are
clear-text passwords in the traffic) we will NOT open up the TCP port
of the UI and connect directly. Instead we will start the UI and
create a secure shell tunnel with port forwarding so that our browser
can connect through the tunnel.

### First test launching the UI

For the first test we launch the UI in the foreground.
```
cd target/run
./FlaskService/main.py
```

### Creating the secure shell port forward

In a separate terminal window we use the following ssh(1) command
to cause the local port 5000/tcp to be forwarded to our EC2 driver
instance and from there connect to localhost:5000/tcp
```
ssh -N -L 5000:localhost:5000 ec2-user@$EC2IPADDR
```

### Connecting to the UI

If everything went well so far, we can now point our web browser at
[http://localhost:5000] and it should look like this:

![BenchmarkSQL-UI](./screenshots/tut1-bmsql-ui-1.png)

### Relaunching the UI disconnect-safe

The BenchmarkSQL Flask UI is meant to be run as a service. This has
the advantage that losing the connection to the EC2 driver instance
will not affect a running benchmark at all. One can simply restart
the ssh(1) tunnel and reconnect to the UI.

To do that we can simply stop the UI that is running in the foreground
with CTRL-C and then launch it via nohup(1) again in the background:
```
nohup ./FlaskService/main.py &
```
Example:
```
[ec2-user@ip-172-31-70-6 run]$ nohup ./FlaskService/main.py &
[1] 32492
[ec2-user@ip-172-31-70-6 run]$ nohup: ignoring input and appending output to ‘nohup.out’
```

