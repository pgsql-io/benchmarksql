#!/usr/bin/env python3
# ----------------------------------------------------------------------
# mc_collectd_graphite.py
#
#   OS Metric collector script for BenchmarkSQL that retrieves data
#   for selected hosts from a graphite-web instance and saves it in
#   the os-metric.json file.
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
        'targets': [
        ],
        'startepoch': time.time(),
    }

    opts, args = getopt.getopt(sys.argv[1:], "u:t:r:S:", [
            "url=", "target=", "resultdir=", "startepoch=",
        ])
    for opt, val in opts:
        if opt in ['-u', '--url']:
            cargs['url'] = val
        elif opt in ['-t', '--target']:
            cargs['targets'].append(val)
        elif opt in ['-r', '--resultdir']:
            cargs['resultdir'] = val
        elif opt in ['-S', '--startepoch']:
            cargs['startepoch'] = float(val)

    coll = Collector(**cargs)

    try:
        coll.run()
    except KeyboardInterrupt:
        pass

    coll.shutdown()

class Collector:
    def __init__(self, url = 'http://localhost:8080',
                 targets = {}, resultdir = '.', startepoch = 0.0):
        self.url = url
        self.targets = targets
        self.resultdir = resultdir
        self.startepoch = float(startepoch)
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
        # graphite server by calling the /render API with
        # &format=json for all the targets specified.
        # ----
        minutes = int((time.time() - self.starttime) / 60) + 1
        params = urllib.parse.urlencode(
                [('target', t) for t in self.targets] +
                [('from', '-{}min'.format(minutes)), ('format', 'json')])
        url = self.url + "?" + params

        try:
            with urllib.request.urlopen(url) as fd:
                gdata = json.loads(fd.read().decode('utf-8'))
        except Exception as ex:
            print(str(ex), file = sys.stderr)
            print("url was:", url)
            return 1

        # ----
        # We need to reformat the data slightly since the hostnames
        # in the graphite metric paths have '_' instead of '.' as
        # domain name separators and the actual metric data is in
        # (value, timestamp) order, while we need that the other way
        # around.
        # ----
        result = {}
        for entry in gdata:
            esplit = entry['target'].split('.')
            host = esplit[1].replace('_', '.')
            metric = '.'.join(esplit[2:])
            if host not in result:
                result[host] = {}
            result[host][metric] = [(t - self.startepoch, v)
                                    for v, t in entry['datapoints']]

        with open(os.path.join(self.resultdir, 'os-metric.json'), 'w') as fd:
            fd.write(json.dumps(result))

if __name__ == '__main__':
    main()
