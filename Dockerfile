# Note: This should be run, once Maven has build correctly the target directory.

FROM centos:8

RUN yum -y update

RUN yum -y install epel-release
RUN yum -y install java-1.8.0-openjdk-headless
RUN yum -y install R-core bc
RUN yum -y install python3
RUN yum -y install python3-pip

ENV JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF8
ENV FLASK_ENV=development

RUN pip3 install pip --upgrade
RUN pip3 install Flask

RUN mkdir -p /benchmarksql
COPY ./src/main/FlaskService/ /benchmarksql/FlaskService/
COPY ./target/lib/ /benchmarksql/lib/
COPY ./target/ /benchmarksql/run/

RUN mkdir -p /service_data
RUN ln -s /service_data/run_seq.dat /benchmarksql/run/.jTPCC_run_seq.dat
RUN ln -s /service_data/benchmarksql-error.log /benchmarksql/run/

CMD ["python3", "/benchmarksql/FlaskService/main.py"]

