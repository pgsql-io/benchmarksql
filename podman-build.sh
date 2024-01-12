#!/bin/sh

mvn || exit 1
podman build -t benchmarksql:6.0-rc2 --http-proxy .
