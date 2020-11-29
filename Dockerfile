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
COPY ./FlaskService/ /benchmarksql/FlaskService/
COPY ./dist/ /benchmarksql/dist/
COPY ./lib/ /benchmarksql/lib/
COPY ./run/ /benchmarksql/run/

RUN mkdir -p /service_data
RUN ln -s /service_data/run_seq.dat /benchmarksql/run/.jTPCC_run_seq.dat
RUN ln -s /service_data/benchmarksql-error.log /benchmarksql/run/
RUN ln -s /service_data/extra_lib /benchmarksql/extra_lib

CMD ["python3", "/benchmarksql/FlaskService/main.py"]

