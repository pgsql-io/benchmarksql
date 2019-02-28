#!/usr/bin/env bash

if [ $# -ne 1 ] ; then
    echo "usage: $(basename $0) PROPS_FILE" >&2
    exit 2
fi

# ----
# In case we get a SIGINT or SIGTERM, kill the whole process group.
# Note that below we start all load steps in the background and
# test with kill -0 when they end. This is because bash doesn't
# process signals while it is waiting on a command.
# ----
function abort_run() {
    echo "" >&2
    echo "abort_run called" >&2
    PGID=$(ps -h -o pgid $$ | sed -e 's/[^0-9]*//g')
    setsid kill -- -$PGID
    exit 1
}
trap abort_run SIGINT SIGTERM

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

java -cp "$myCP" $myOPTS jTPCC &
PID=$!
while true ; do
    kill -0 $PID 2>/dev/null || break
    sleep 1
done
