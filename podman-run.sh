#!/bin/sh

mkdir -p ./service_data || exit 1

podman run --rm \
	--network="host" \
	--user=`id -u`:`id -g` \
	--userns=keep-id \
	--volume="./service_data:/service_data" \
	-w "/benchmarksql" \
	localhost/benchmarksql:6.0-rc2

