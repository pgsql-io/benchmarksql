# Launching the Service Container

Once the Docker image is built, a container can be started with the
`service-start.sh` script.

```
#!/bin/sh

mkdir -p ./service_data

docker run --rm -it --name benchmarksql-service \
	--publish 5000:5000 \
	--volume "`pwd`/service_data:/service_data" \
	--user `id -u`:`id -g` \
	benchmarksql-v6.0
```

* It creates a local directory to preserve configuration and result data.
  This directory is mounted into the container.
* It runs the docker image **benchmarksql-v6.0** as a container
  with a tag **benchmarksql-service**. This container is running the
  service under the current user (not root) and it forwards port 5000/tcp
  into the container for the Flask UI and API.

This container will run in the foreground and show the Flask log for debugging
purposes.
To run it in the background simply replace theflags `-it` with `-d`.

At this point the BenchmarkSQL service is running and you can connect to it with
you browser on [http://localhost:5000](http://localhost:5000).

If you created this service on a remote machine, don't simply open port 5000/tcp
in the firewall.
**NOTE:** Keep in mind that the configuration file, controlling the benchmark
run settings, contains all the connection credentials for your database in clear
text!
The plan is to substantially enhance the Flask GUI and API with user and
configuration management.
Then, provide instructions on how to secure the container behind an
[nginx](https://www.nginx.com/) reverse proxy for encryption.
In the meantime, please use ssh to tunnel port 5000/tcp securely to the
benchmark driver machine.
Since that tunnel is only for the WEB UI and API traffic, it won't affect the
benchmark results at all.

[comment]: # (TODO: Tutorial of how to use the WEB UI.) 
