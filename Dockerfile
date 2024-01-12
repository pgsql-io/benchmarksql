# Note: This should be run, once Maven has build correctly the target directory.

FROM rockylinux:9

RUN dnf -y update

RUN dnf -y install epel-release \
				   java-17-openjdk-headless \
				   dnf-plugins-core \
				   python3
RUN dnf config-manager --set-enabled crb
RUN dnf install python3 python3-pip -y
RUN pip3 install pip --upgrade

ENV JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF8
ENV FLASK_ENV=development

COPY ./target/BenchmarkSQL.jar /benchmarksql/BenchmarkSQL.jar
COPY ./target/lib/ /benchmarksql/lib
COPY ./target/run/ /benchmarksql/run

RUN mkdir -p /service_data && \
    rm -f /benchmarksql/run/.jTPCC_run_seq.dat && \
    rm -f /benchmarksql/run/benchmark.log && \
    rm -f /benchmarksql/run/terminalio.log && \
    mkdir -p /benchmarksql/.config/matplotlib && \
    chmod 777 /benchmarksql/.config/matplotlib && \
    mkdir -p /benchmarksql/.cache/matplotlib && \
    chmod 777 /benchmarksql/.cache/matplotlib && \
    ln -s /service_data/run_seq.dat /benchmarksql/run/.jTPCC_run_seq.dat && \
    ln -s /service_data/benchmark.log /benchmarksql/run/benchmark.log && \
    ln -s /service_data/terminalio.log /benchmarksql/run/terminalio.log && \
    pip3 install -r /benchmarksql/run/requirements.txt

CMD ["python3", "/benchmarksql/run/FlaskService/main.py"]

