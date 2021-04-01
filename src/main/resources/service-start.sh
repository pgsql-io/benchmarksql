#!/bin/sh

mkdir -p ./service_data

docker run --rm -it --name benchmarksql-service \
	--publish 5000:5000 \
	--volume "`pwd`/service_data:/service_data" \
	--user `id -u`:`id -g` \
	benchmarksql-v6.0
