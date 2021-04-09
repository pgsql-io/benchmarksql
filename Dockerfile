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

RUN mkdir -p /service_data && \
    rm -f /benchmarksql/run/.jTPCC_run_seq.dat && \
    rm -f /benchmarksql/run/benchmark.log && \
    rm -f /benchmarksql/run/terminalio.log && \
    ln -s /service_data/run_seq.dat /benchmarksql/run/.jTPCC_run_seq.dat && \
    ln -s /service_data/benchmark.log /benchmarksql/run/benchmark.log && \
    ln -s /service_data/terminalio.log /benchmarksql/run/terminalio.log

CMD ["python3", "/benchmarksql/run/FlaskService/main.py"]

