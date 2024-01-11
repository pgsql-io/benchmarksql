#!/usr/bin/env python3

import requests
import json
import re
import sys

def main():
    if len(sys.argv) < 5:
        usage()
        sys.exit(2)

    with open(sys.argv[1], 'r') as fd:
        config = json.load(fd)

    command = sys.argv[2]
    appnode = sys.argv[3]
    dbnode = sys.argv[4]

    extra_opts = {}
    for opt in sys.argv[5:]:
        key, val = opt.split('=')
        extra_opts[key] = val

    # TODO: sanity checks for all args

    bmsql = BenchmarkSQL(config, appnode, dbnode, extra_opts)

    if command == 'build':
        result = bmsql.build()
        print(result['current_job_type'])
    elif command == 'destroy':
        result = bmsql.destroy()
        print(result['current_job_type'])
    elif command == 'run':
        result = bmsql.run()
        print(result['current_job_type'])
        print(result['current_job_id'])
    elif command == 'cancel':
        result = bmsql.cancel()
        print(result['current_job_type'])
    elif command == 'status':
        result = bmsql.status()
        print(result['current_job_type'])
        print(result['current_job_id'])
    elif command == 'txsummary':
        result = bmsql.txsummary(sys.argv[5])
        print(json.dumps(result['txsummary'], indent=2))
    else:
        print("unknown command '%s'"%(command,))
        sys.exit(2)

def usage():
    print("""usage: benchmarkctl CONFIG.json COMMAND APPNODE DBNODE
""", file = sys.stderr)


class BenchmarkSQL:
    def __init__(self, config, appnode, dbnode, extra_opts = None):
        with open(config['properties_template'], 'r') as fd:
            properties = fd.read()
        overrides = config['properties']
        overrides.update(config['dbnodes'][dbnode]['properties'])
        overrides.update(config['appnodes'][appnode]['properties'])
        if extra_opts is not None:
            overrides.update(extra_opts)
        for key in overrides:
            properties, n = re.subn('^%s=.*$'%(key),
                                '%s=%s'%(key, overrides[key]),
                                properties,
                                flags = re.MULTILINE)
            if n == 0:
                properties += "\n\n" + key + "=" + overrides[key] + "\n"

        self.config = config
        self.appnode = appnode
        self.appconf = config['appnodes'][appnode]
        self.dbnode = dbnode
        self.dbconf = config['dbnodes'][dbnode]
        self.properties = properties

    def status(self):
        url = self.appconf['api_url']
        req = {
            'command': 'status'
        }
        res = requests.post(url, data = {'request': json.dumps(req)})
        return json.loads(res.text)

    def txsummary(self, run_id):
        url = self.appconf['api_url']
        req = {
            'command': 'txsummary',
            'run_id': int(run_id)
        }
        res = requests.post(url, data = {'request': json.dumps(req)})
        return json.loads(res.text)

    def build(self):
        url = self.appconf['api_url']
        req = {
            'command': 'build',
            'properties': self.properties
        }
        res = requests.post(url, data = {'request': json.dumps(req)})
        return json.loads(res.text)

    def destroy(self):
        url = self.appconf['api_url']
        req = {
            'command': 'destroy',
            'properties': self.properties
        }
        res = requests.post(url, data = {'request': json.dumps(req)})
        return json.loads(res.text)

    def run(self):
        url = self.appconf['api_url']
        req = {
            'command': 'run',
            'properties': self.properties
        }
        res = requests.post(url, data = {'request': json.dumps(req)})
        return json.loads(res.text)

    def cancel(self):
        url = self.appconf['api_url']
        req = {
            'command': 'cancel',
            'properties': self.properties
        }
        res = requests.post(url, data = {'request': json.dumps(req)})
        return json.loads(res.text)

if __name__ == '__main__':
    main()
