#!/usr/bin/env bash

if [ $# -ne 1 ] ; then
    echo "usage: $(basename $0) PROPS" >&2
    exit 2
fi

# ----
# In case we get a SIGINT or SIGTERM, kill the whole process group.
# Note that below we start all load steps in the background and
# test with kill -0 when they end. This is because bash doesn't
# process signals while it is waiting on a command.
# ----
function abort_destroy() {
    echo "" >&2
    echo "abort_destroy called" >&2
    PGID=$(ps -h -o pgid $$ | sed -e 's/[^0-9]*//g')
    setsid kill -- -$PGID
    exit 1
}
trap abort_destroy SIGINT SIGTERM

PROPS="$1"
if [ ! -f "${PROPS}" ] ; then
    echo "${PROPS}: no such file or directory" >&2
    exit 1
fi

DB="$(grep '^db=' $PROPS | sed -e 's/^db=//')"
USER="$(grep '^user=' $PROPS | sed -e 's/^user=//' )"
PASSWORD="$(grep '^password=' $PROPS | sed -e 's/^password=//' )"

STEPS="tableDrops storedProcedureDrops"

for step in ${STEPS} ; do
    ./runSQL.sh "${PROPS}" $step || exit 1 &
    PID=$!
    while true ; do
	kill -0 $PID 2>/dev/null || break
	sleep 1
    done
    wait $PID
done
