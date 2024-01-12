#!/bin/sh

mvn || exit 1
podman build -t benchmarksql:6.0-rc1 --http-proxy .
