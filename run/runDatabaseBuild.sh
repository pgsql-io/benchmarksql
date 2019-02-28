#!/usr/bin/env bash

if [ $# -lt 1 ] ; then
    echo "usage: $(basename $0) PROPS [OPT VAL [...]]" >&2
    exit 2
fi

# ----
# In case we get a SIGINT or SIGTERM, kill the whole process group.
# Note that below we start all load steps in the background and
# test with kill -0 when they end. This is because bash doesn't
# process signals while it is waiting on a command.
# ----
function abort_load() {
    echo "" >&2
    echo "abort_load called" >&2
    PGID=$(ps -h -o pgid $$ | sed -e 's/[^0-9]*//g')
    setsid kill -- -$PGID
    exit 1
}
trap abort_load SIGINT SIGTERM

PROPS="$1"
shift
if [ ! -f "${PROPS}" ] ; then
    echo "${PROPS}: no such file or directory" >&2
    exit 1
fi

DB="$(grep '^db=' $PROPS | sed -e 's/^db=//')"

BEFORE_LOAD="tableCreates extraCommandsBeforeLoad storedProcedureCreates"

AFTER_LOAD="indexCreates foreignKeys extraHistID buildFinish"

for step in ${BEFORE_LOAD} ; do
    ./runSQL.sh "${PROPS}" $step &
    PID=$!
    while true ; do
	kill -0 $PID 2>/dev/null || break
	sleep 1
    done
    wait $PID
    rc=$?
    [ $rc -eq 0 ] || exit $rc
done

./runLoader.sh "${PROPS}" $* &
PID=$!
while true ; do
    kill -0 $PID 2>/dev/null || break
    sleep 1
done
wait $PID
rc=$?
[ $rc -eq 0 ] || exit $rc

for step in ${AFTER_LOAD} ; do
    ./runSQL.sh "${PROPS}" $step &
    PID=$!
    while true ; do
	kill -0 $PID 2>/dev/null || break
	sleep 1
    done
    wait $PID
    rc=$?
    [ $rc -eq 0 ] || exit $rc
done
