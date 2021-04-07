#!/bin/sh

mvn || exit 1
podman build -t benchmarksql-v6.0 .
