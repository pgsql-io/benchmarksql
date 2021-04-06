#!/bin/sh

mkdir -p ./service_data || exit 1

podman run --rm \
	--network="host" \
	--user=`id -u`:`id -g` \
	--userns=keep-id \
	--volume="${HOME}:${HOME}" \
	--volume="./service_data:/service_data" \
	-w "$PWD" \
	localhost/benchmarksql-v6.0

