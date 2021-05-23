#!/usr/bin/env python3
# ----------------------------------------------------------------------
# mc_collectd_mqtt.py
#
#   OS metric collector script for BenchmarkSQL that retrieves data
#   sent to an MQTT broker by collectd on the server system(s)
# ----------------------------------------------------------------------
import os.path
import sys
import paho.mqtt.client as mqttc
import csv
import time
import select
import getopt
import json

def main():
    cargs = {
        'host': 'localhost',
        'topics': [
        ],
        'startepoch': time.time(),
    }

    opts, args = getopt.getopt(sys.argv[1:], "h:p:U:P:t:r:i:S:", [
            "host=", "port=", "user=", "password=", "topic=",
            "resultdir=", "clientid=", "startepoch=",
        ])
    for opt, val in opts:
        if opt in ['-h', '--host']:
            cargs['host'] = val
        elif opt in ['-p', '--port']:
            cargs['port'] = val
        elif opt in ['-U', '--user']:
            cargs['user'] = val
        elif opt in ['-P', '--password']:
            cargs['password'] = val
        elif opt in ['-t', '--topic']:
            cargs['topics'].append(val)
        elif opt in ['-r', '--resultdir']:
            cargs['resultdir'] = val
        elif opt in ['-i', '--clientid']:
            cargs['clientId'] = val
        elif opt in ['-s', '--startepoch']:
            cargs['startepoch'] = float(val)

    coll = Collector(**cargs)

    try:
        coll.run()
    except KeyboardInterrupt:
        pass

    coll.shutdown()

class Collector:
    def __init__(self, host = 'localhost', port = '1883',
                 clientId = None, user = None, password = None,
                 topics = {}, resultdir = '.', startepoch = 0.0):
        self.host = host
        self.port = int(port)
        self.clientId = clientId
        self.user = user
        self.password = password
        self.topics = topics
        self.resultdir = resultdir
        self.startepoch = float(startepoch)

        self.result = {}

        self.mqttc = mqttc.Client(self.clientId)
        self.mqttc.on_connect = self.on_mqtt_connect
        self.mqttc.on_disconnect = self.on_mqtt_disconnect
        self.mqttc.on_message = self.on_mqtt_message

        if self.user is not None:
            self.mqttc.username_pw_set(self.user, self.password)
        self.mqttc.connect(host = self.host, port = self.port)

    def on_mqtt_connect(self, client, userdata, flags, rc):
        """
        On connect or reconnect we only need to subscribe to all
        the specified topics
        """
        for topic in self.topics:
            self.mqttc.subscribe(topic, qos = 0)

    def on_mqtt_disconnect(self, clien, userdata, rc):
        """
        Nothing to do on disconnect
        """
        pass

    def on_mqtt_message(self, client, userdata, msg):
        """
        Collect all metric data received for the specified topics
        in self.result
        """
        # ----
        # Extract the hostname and metric path from the topic
        # ----
        tl = msg.topic.split('/')
        host = tl[1]
        metrics = ['.'.join(tl[2:]),]

        # ----
        # Extract the timestamp adjusted by startepoch from the payload
        # ----
        pl = msg.payload.decode('utf-8').rstrip('\0').split(':')
        epoch = float(pl[0]) - self.startepoch

        # ----
        # Some metrics sent by collectd actually have two values in them.
        # We handle this by splitting them into two metrics. Every disk-
        # with two values will become METRIC.read and METRIC.write and
        # every network interface- one becomes METRIC.rx and METRIC.tx.
        # Having individual metric names simplifies the code in the
        # report generator.
        # ----
        if metrics[0].startswith('disk-') and len(pl) == 3:
            metrics = [
                '.'.join(tl[2:] + ['read']),
                '.'.join(tl[2:] + ['write']),
            ]
        elif metrics[0].startswith('interface-') and len(pl) == 3:
            metrics = [
                '.'.join(tl[2:] + ['rx']),
                '.'.join(tl[2:] + ['tx']),
            ]

        # ----
        # Add the data to self.result
        # ----
        if host not in self.result:
            self.result[host] = {}
        for i in range(0, len(metrics)):
            if metrics[i] not in self.result[host]:
                self.result[host][metrics[i]] = []
            self.result[host][metrics[i]].append([
                    epoch, float(pl[i + 1])])

    def run(self):
        # ----
        # We call mqttc.loop() which will return after each event. We stop
        # running when we receive any input on stdin (which is the way the
        # benchmark driver is signaling us to finish).
        # ----
        while True:
            self.mqttc.loop()
            r, w, x = select.select([sys.stdin], [], [], 0.0)
            if len(r) > 0:
                break

    def shutdown(self):
        # ----
        # On shutdown we dump all the collected data into the output file.
        # ----
        self.mqttc.loop_stop()
        with open(os.path.join(self.resultdir, 'os-metric.json'), 'w') as fd:
            fd.write(json.dumps(self.result))

if __name__ == '__main__':
    main()
