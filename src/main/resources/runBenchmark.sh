#!/usr/bin/env bash

if [ $# -ne 1 ] ; then
    echo "usage: $(basename $0) PROPS_FILE" >&2
    exit 2
fi

SEQ_FILE="./.jTPCC_run_seq.dat"
if [ ! -f "${SEQ_FILE}" ] ; then
    echo "0" > "${SEQ_FILE}"
fi
SEQ=$(expr $(cat "${SEQ_FILE}") + 1) || exit 1
echo "${SEQ}" > "${SEQ_FILE}"

source ./funcs.sh $1

setCP || exit 1

myOPTS="-Dprop=$1 -DrunID=${SEQ}"
myOPTS="${myOPTS} -Djava.security.egd=file:/dev/./urandom"

java -cp "$myCP" $myOPTS com.github.pgsqlio.benchmarksql.jtpcc.jTPCC &
PID=$!
while true ; do
    kill -0 $PID 2>/dev/null || break
    sleep 1
done
