# Note: This should be run, once Maven has build correctly the target directory.

FROM centos:8

RUN dnf -y update

RUN dnf -y install epel-release
RUN dnf -y install java-11-openjdk-headless
RUN dnf -y install R-core
RUN dnf -y install bc
RUN dnf -y install python3
RUN dnf -y install python3-pip

ENV JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF8
ENV FLASK_ENV=development

RUN pip3 install pip --upgrade
RUN pip3 install Flask

COPY ./target/ /benchmarksql

RUN mkdir -p /service_data
RUN ln -s /service_data/run_seq.dat /benchmarksql/run/.jTPCC_run_seq.dat
RUN ln -s /service_data/benchmarksql-error.log /benchmarksql/run/benchmarksql-error.log
RUN ln -s /service_data/benchmarksql-trace.log /benchmarksql/run/benchmarksql-trace.log

CMD ["python3", "/benchmarksql/run/FlaskService/main.py"]

