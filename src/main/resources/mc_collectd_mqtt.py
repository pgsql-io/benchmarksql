#!/usr/bin/env python3

import os.path
import sys
import paho.mqtt.client as mqttc
import csv
import time
import select
import getopt

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

        self.output = {}

        self.mqttc = mqttc.Client(self.clientId)
        self.mqttc.on_connect = self.on_mqtt_connect
        self.mqttc.on_disconnect = self.on_mqtt_disconnect
        self.mqttc.on_message = self.on_mqtt_message

        if self.user is not None:
            self.mqttc.username_pw_set(self.user, self.password)
        self.mqttc.connect(host = self.host, port = self.port)

    def on_mqtt_connect(self, client, userdata, flags, rc):
        for topic in self.topics:
            self.mqttc.subscribe(topic, qos = 0)

    def on_mqtt_disconnect(self, clien, userdata, rc):
        pass

    def on_mqtt_message(self, client, userdata, msg):
        path = '/'.join(msg.topic.split('/')[2:]) + '.csv'
        subdir = os.path.dirname(path)
        data = msg.payload.decode('utf-8').rstrip('\0').split(':')
        data = [str(float(data[0]) - self.startepoch)] + data[1:]
        if path not in self.output:
            fname = os.path.join(self.resultdir, path)
            dirname = os.path.dirname(fname)
            os.makedirs(dirname, exist_ok = True)
            fd = open(fname, 'w', newline = '')
            self.output[path] = [csv.writer(fd), fd]
        self.output[path][0].writerow(data)
        self.output[path][1].flush()

    def run(self):
        while True:
            self.mqttc.loop()
            r, w, x = select.select([sys.stdin], [], [], 0.0)
            if len(r) > 0:
                break

    def shutdown(self):
        self.mqttc.loop_stop()
        for path in self.output:
            self.output[path][1].close()

if __name__ == '__main__':
    main()
