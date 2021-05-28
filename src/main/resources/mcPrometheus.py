#!/usr/bin/env python3
# ----------------------------------------------------------------------
# mcPrometheus.py
#
#   OS Metric collector script for BenchmarkSQL that retrieves data
#   for selected hosts from a Prometheus server.
# ----------------------------------------------------------------------
import os.path
import sys
import time
import select
import getopt

import json
import urllib.request
import urllib.parse

def main():
    cargs = {
        'url': 'http://localhost:8080',
        'instances': [
        ],
        'startepoch': time.time() - 60,
    }

    opts, args = getopt.getopt(sys.argv[1:], "u:i:I:r:S:", [
            "url=", "instance=", "interval=", "resultdir=", "startepoch=",
        ])
    for opt, val in opts:
        if opt in ['-u', '--url']:
            cargs['url'] = val
        elif opt in ['-i', '--instance']:
            cargs['instances'].append(val)
        elif opt in ['-I', '--interval']:
            cargs['interval'] = val
        elif opt in ['-r', '--resultdir']:
            cargs['resultdir'] = val
        elif opt in ['-S', '--startepoch']:
            cargs['startepoch'] = float(val) - 60

    coll = Collector(**cargs)

    try:
        coll.run()
    except KeyboardInterrupt:
        pass

    coll.shutdown()

class Collector:
    def __init__(self, url = 'http://localhost:9090/api/v1/query_range',
                 instances = {}, resultdir = '.', interval = '1m',
                 startepoch = 0.0):
        self.url = url
        self.instances = instances
        self.resultdir = resultdir
        self.startepoch = float(startepoch)
        self.interval = interval
        self.starttime = time.time()

        self.output = {}

    def run(self):
        # ----
        # Run until we receive anything on stdin (which is the way the
        # benchmark driver is signaling us to finish).
        # ----
        while True:
            r, w, x = select.select([sys.stdin], [], [], None)
            if len(r) > 0:
                break

    def shutdown(self):
        # ----
        # On shutdown we retrieve the metric data from the
        # Prometheus server via the api.
        # ----
        result = {}
        for instance in self.instances:
            print("instance:", instance)
            iname = instance.split(':')[0]
            result[iname] = {}

            # ----
            # Get CPU usage in percent over all CPUs
            # ----
            params = {
                "query": """sum(irate(node_cpu_seconds_total {{instance="{instance}"}}[{interval}])) without (cpu) / count(node_cpu_seconds_total {{instance="{instance}"}}) without (cpu) * 100""".format(instance = instance,
                                     interval = self.interval),
                "start": self.startepoch,
                "end": time.time(),
                "step": self.interval,
            }
            url = self.url + '?' + urllib.parse.urlencode(params)

            try:
                with urllib.request.urlopen(url) as fd:
                    gdata = json.loads(fd.read().decode('utf-8'))
            except Exception as ex:
                print(str(ex), file = sys.stderr)
                print("url was:", url)
                return 1

            for entry in gdata['data']['result']:
                if entry['metric']['mode'] == 'iowait':
                    metric = "cpu.percent-wait"
                elif entry['metric']['mode'] == 'irq':
                    metric = "cpu.percent-interrupt"
                else:
                    metric = "cpu.percent-" + entry['metric']['mode']
                result[iname][metric] = [(ts - self.startepoch, float(v)) for ts, v in entry['values']]

            # ----
            # Get the disk metric data
            # ----
            metric_map = [
                ['disk_octets.read', 'node_disk_read_bytes_total'],
                ['disk_octets.write', 'node_disk_written_bytes_total'],
                ['disk_ops.read', 'node_disk_reads_completed_total'],
                ['disk_ops.write', 'node_disk_writes_completed_total'],
                ['disk_io_time.read', 'node_disk_read_time_seconds_total'],
                ['disk_io_time.write', 'node_disk_write_time_seconds_total'],
                ['disk_merged.read', 'node_disk_reads_merged_total'],
                ['disk_merged.write', 'node_disk_writes_merged_total'],
            ]
            for ment in metric_map:
                params = {
                    "query": """irate({metric} {{instance="{instance}"}} [{interval}])""".format(
                            metric = ment[1],
                            instance = instance,
                            interval = self.interval,
                        ),
                    "start": self.startepoch,
                    "end": time.time(),
                    "step": self.interval,
                }
                url = self.url + '?' + urllib.parse.urlencode(params)

                try:
                    with urllib.request.urlopen(url) as fd:
                        gdata = json.loads(fd.read().decode('utf-8'))
                except Exception as ex:
                    print(str(ex), file = sys.stderr)
                    print("url was:", url)
                    return 1

                for entry in gdata['data']['result']:
                    dev = entry['metric']['device']
                    mname = 'disk-' + dev + '.' + ment[0]
                    result[iname][mname] = [(ts - self.startepoch, float(v)) for ts, v in entry['values']]

            # ----
            # Get the network interface metric data
            # ----
            metric_map = [
                ['if_octets.rx', 'node_network_receive_bytes_total'],
                ['if_octets.tx', 'node_network_transmit_bytes_total'],
                ['if_packets.rx', 'node_network_receive_packets_total'],
                ['if_packets.tx', 'node_network_transmit_packets_total'],
                ['if_errors.rx', 'node_network_receive_errs_total'],
                ['if_errors.tx', 'node_network_transmit_errs_total'],
                ['if_dropped.rx', 'node_network_receive_drop_total'],
                ['if_dropped.tx', 'node_network_transmit_drop_total'],
            ]
            for ment in metric_map:
                params = {
                    "query": """irate({metric} {{instance="{instance}"}} [{interval}])""".format(
                            metric = ment[1],
                            instance = instance,
                            interval = self.interval,
                        ),
                    "start": self.startepoch,
                    "end": time.time(),
                    "step": self.interval,
                }
                url = self.url + '?' + urllib.parse.urlencode(params)

                try:
                    with urllib.request.urlopen(url) as fd:
                        gdata = json.loads(fd.read().decode('utf-8'))
                except Exception as ex:
                    print(str(ex), file = sys.stderr)
                    print("url was:", url)
                    return 1

                for entry in gdata['data']['result']:
                    dev = entry['metric']['device']
                    mname = 'interface-' + dev + '.' + ment[0]
                    result[iname][mname] = [(ts - self.startepoch, float(v)) for ts, v in entry['values']]

            # ----
            # Get the memory metric data
            # ----
            metric_map = [
                ['memory-used', 'special'],
                ['memory-buffered', 'node_memory_Buffers_bytes'],
                ['memory-cached', 'node_memory_Cached_bytes'],
                ['memory-free', 'node_memory_MemFree_bytes'],
            ]
            for ment in metric_map:
                params = {
                    "query": """{metric} {{instance="{instance}"}}""".format(
                            metric = ment[1],
                            instance = instance,
                            interval = self.interval,
                        ),
                    "start": self.startepoch,
                    "end": time.time(),
                    "step": self.interval,
                }
                if ment[0] == 'memory-used':
                    params['query'] = """node_memory_MemTotal_bytes {{instance="{instance}"}} - node_memory_MemFree_bytes {{instance="{instance}"}}""".format(
                            instance = instance,
                        )
                url = self.url + '?' + urllib.parse.urlencode(params)

                try:
                    with urllib.request.urlopen(url) as fd:
                        gdata = json.loads(fd.read().decode('utf-8'))
                except Exception as ex:
                    print(str(ex), file = sys.stderr)
                    print("url was:", url)
                    return 1

                mname = 'memory.' + ment[0]
                result[iname][mname] = [(ts - self.startepoch, float(v)) for ts, v in gdata['data']['result'][0]['values']]

        with open(os.path.join(self.resultdir, 'os-metric.json'), 'w') as fd:
            fd.write(json.dumps(result))

if __name__ == '__main__':
    main()
