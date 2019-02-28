# ----
# Dockerfile for BenchmarkSQL
# ----

# Our base is CentOS-7
FROM centos:7

RUN sed -i -e 's/enabled=.*/enabled=0/' /etc/yum/pluginconf.d/fastestmirror.conf

RUN yum -y update

# We need EPEL for R-core
RUN yum -y install wget
RUN wget http://dl.fedoraproject.org/pub/epel/epel-release-latest-7.noarch.rpm
RUN rpm -ivh epel-release-latest-7.noarch.rpm

# Install Python Flask
RUN yum -y install python-flask

# Install Java-8 Runtime
RUN yum -y install java-1.8.0-openjdk

# Install R-core (this is big because it pulls a lot from X11)
RUN yum -y install R-core

COPY dist	/benchmarksql/dist
COPY lib	/benchmarksql/lib
COPY FlaskGUI	/benchmarksql/FlaskGUI
COPY run	/benchmarksql/run

CMD ["python", "/benchmarksql/FlaskGUI/main.py"]
