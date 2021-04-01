FROM centos:8

RUN dnf -y update

RUN dnf -y install epel-release
RUN dnf -y install java-1.8.0-openjdk-headless
RUN dnf -y install R-core
RUN dnf -y install bc
RUN dnf -y install python3
RUN dnf -y install python3-pip

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

