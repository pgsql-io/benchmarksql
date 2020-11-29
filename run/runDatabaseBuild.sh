#!/usr/bin/env bash

if [ $# -lt 1 ] ; then
    echo "usage: $(basename $0) PROPS [OPT VAL [...]]" >&2
    exit 2
fi

PROPS="$1"
shift
if [ ! -f "${PROPS}" ] ; then
    echo "${PROPS}: no such file or directory" >&2
    exit 1
fi

DB="$(grep '^db=' $PROPS | sed -e 's/^db=//')"

BEFORE_LOAD="tableCreates extraCommandsBeforeLoad storedProcedureCreates"

AFTER_LOAD="indexCreates foreignKeys buildFinish"

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
