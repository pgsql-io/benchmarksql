# Note: This should be run, once Maven has build correctly the target directory.

FROM centos:8

RUN dnf -y update

RUN dnf -y install epel-release
RUN dnf -y install java-11-openjdk-headless
RUN dnf -y install dnf-plugins-core
RUN dnf config-manager --set-enabled powertools
RUN dnf -y install python3
RUN dnf -y install python3-pip \
				   python3-numpy \
				   python3-matplotlib

ENV JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF8
ENV FLASK_ENV=development

RUN pip3 install pip --upgrade
RUN pip3 install Flask paho-mqtt jproperties

COPY ./target/ /benchmarksql

RUN mkdir -p /service_data && \
    rm -f /benchmarksql/run/.jTPCC_run_seq.dat && \
    rm -f /benchmarksql/run/benchmark.log && \
    rm -f /benchmarksql/run/terminalio.log && \
    ln -s /service_data/run_seq.dat /benchmarksql/run/.jTPCC_run_seq.dat && \
    ln -s /service_data/benchmark.log /benchmarksql/run/benchmark.log && \
    ln -s /service_data/terminalio.log /benchmarksql/run/terminalio.log

CMD ["python3", "/benchmarksql/run/FlaskService/main.py"]

