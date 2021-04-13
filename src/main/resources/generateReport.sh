#!/usr/bin/env bash

if [ $# -lt 1 ] ; then
    echo "usage: $(basename $0) RESULT_DIR" >&2
    exit 2
fi

exec $(dirname $0)/generateReport/main.py "$1"
