# ----
# BenchmarkSQL.py - Control interface to run BenchmarkSQL components
# ----

# -*- coding: utf-8

import codecs
import json
import os
import shutil
import signal
import subprocess
import threading
import time
import csv
import shlex
import jproperties

class BenchmarkSQL:
    """
    Control interface for running BenchmarkSQL components.
    """
    def __init__(self):
        """
        Initialize the instance
        """
        # ----
        # Set the path where we find the BenchmarkSQL run components.
        # We also make this our current directory for launching the
        # actual job scripts.
        # ----
        head, tail = os.path.split(__file__)
        self.run_dir = os.path.abspath(os.path.join(head, '..'))
        if not os.path.exists(os.path.join(self.run_dir, 'runBenchmark.sh')):
            raise Exception("BenchmarkSQL run components not found at '{0}'".format(self.run_dir))
        os.chdir(self.run_dir)

        # ----
        # The "service_data" directory defaults to ./service_data.
        # It that does not exist we assume to live in the docker container
        # and try /service_data.
        # ----
        self.data_dir = os.path.abspath(os.path.join(os.path.curdir, 'service_data'))
        if os.path.isdir(self.data_dir):
            print("using existing", self.data_dir)
        else:
            if os.path.isdir('/service_data'):
                self.data_dir = '/service_data'
                print("assuming docker container, using", self.data_dir)
            else:
                os.mkdir(self.data_dir)
                print("created empty directory", self.data_dir)

        self.status_file = os.path.join(self.data_dir, 'status.json')
        self.status_data = self.load_status()

        self.lock = threading.Lock()
        self.current_job_type = 'IDLE'
        self.current_job = None
        self.current_job_id = 0
        self.current_job_name = ""
        self.current_job_output = ""
        self.current_job_start = 0.0
        self.current_job_properties = self.get_properties()

    def load_status(self):
        """
        Load the current status data from the /data/status.json file
        """
        if os.path.exists(self.status_file):
            with open(self.status_file, 'r') as fd:
                data = json.loads(fd.read())
            return data
        else:
            return {
                'run_count':    0,
                'results':      [],
                'filename':     'default.properties',
            }

    def save_status(self):
        """
        Save the current status data into the /data/status.json file
        """
        # ----
        # Caller should hold lock
        # ----
        with open(self.status_file, 'w') as fd:
            fd.write(json.dumps(self.status_data, indent = 4))

    def get_status(self):
        self.get_job_type()
        self.lock.acquire()
        result = {
                'current_job_type': self.current_job_type,
                'current_job_id': self.current_job_id,
                'current_job_name': self.current_job_name,
                'current_job_output': self.current_job_output,
                'current_job_start': self.current_job_start,
                'current_job_properties': self.current_job_properties,
            }
        self.lock.release()
        return result

    def get_job_type(self):
        self.lock.acquire()
        if self.current_job is not None:
            self.current_job.join(0.0)
            if not self.current_job.is_alive():
                for entry in self.status_data['results']:
                    if entry['name'] == self.current_job_name:
                        if entry['state'] == 'RUN':
                            entry['state'] = 'FINISHED'
                        break
                self.current_job = None
                self.current_job_id = 0
                self.current_job_type = 'IDLE'
                self.current_job_name = ""
        result = self.current_job_type
        self.save_status()
        self.lock.release()
        return result

    def get_job_runtime(self):
        self.lock.acquire()
        if self.current_job is None:
            result = "--:--:--"
        else:
            runtime = int(time.time() - self.current_job_start)
            result =  "{0:02d}:{1:02d}:{2:02d}".format(
                      int(runtime / 3600), int((runtime / 60) % 60), int(runtime % 60))
        self.lock.release()
        return result

    def get_job_output(self):
        self.lock.acquire()
        result = self.current_job_output
        self.lock.release()
        return result

    def add_job_output(self, output):
        self.lock.acquire()
        self.current_job_output += str(output)
        self.lock.release()

    def get_job_txsummary(self, run_id):
        self.lock.acquire()
        try:
            fname = os.path.join(self.data_dir, "result_{0:06d}".format(run_id), "data", "summary.csv")
            result = {}
            with open(fname, 'r') as fd:
                csv_reader = csv.DictReader(fd)
                for row in csv_reader:
                    result[row['ttype']] = {key: row[key].rstrip('s%') for key in row.keys() if key != 'ttype'}
        except Exception as e:
            self.lock.release()
            raise e
        self.lock.release()
        return result

    def get_properties(self):
        self.lock.acquire()
        last_path = os.path.join(self.data_dir, 'last.properties')
        if os.path.exists(last_path):
            with open(last_path, 'r') as fd:
                result = fd.read()
        else:
            sample_path = os.path.join(os.path.dirname(__file__), 'sample.last.properties')
            with open(sample_path, 'r') as fd:
                result = fd.read()
        self.current_job_properties = result
        self.lock.release()
        return result

    def get_results(self):
        self.lock.acquire()
        results = []
        for entry in self.status_data['results']:
            result_dir = os.path.join(self.data_dir, entry['name'])
            results.append(
                (
                    entry['run_id'],
                    entry['name'],
                    entry['start'],
                    entry['state'],
                )
            )
        self.lock.release()
        return results

    def save_properties(self, properties):
        self.lock.acquire()
        last_path = os.path.join(self.data_dir, 'last.properties')
        with open(last_path, 'w') as fd:
            fd.write(properties)
        self.current_job_properties = properties
        self.lock.release()

    def get_report(self, run_id):
        try:
            run_id = int(run_id)
        except Exception as e:
            return "What?"

        html_path = os.path.join(self.data_dir, "result_{0:06d}.html".format(run_id))
        try:
            with open(html_path, 'r') as fd:
                report = fd.read()
        except Exception as e:
            return str(e)
        return report

    def get_log(self, run_id):
        try:
            run_id = int(run_id)
        except Exception as e:
            return "What?"

        html_path = os.path.join(self.data_dir, "result_{0:06d}".format(run_id), 'console.log')
        try:
            with open(html_path, 'r') as fd:
                log = fd.read()
        except Exception as e:
            return str(e)
        return log

    def delete_result(self, run_id):
        try:
            run_id = int(run_id)
        except Exception as e:
            return "What?"

        self.lock.acquire()
        html_path = os.path.join(self.data_dir, "result_{0:06d}.html".format(run_id))
        data_path = os.path.join(self.data_dir, "result_{0:06d}".format(run_id))
        new_results = [x for x in self.status_data['results'] if x['run_id'] != run_id]
        if len(new_results) > 0:
            new_count = max([x['run_id'] for x in new_results])
        else:
            new_count = 0

        try:
            shutil.rmtree(data_path)
        except Exception as e:
            print(str(e))
        try:
            os.remove(html_path)
        except Exception as e:
            print(str(e))
        self.status_data['run_count'] = new_count
        self.status_data['results'] = new_results

        self.save_status()
        with open(os.path.join(self.data_dir, 'run_seq.dat'), 'w') as fd:
            fd.write(str(new_count) + '\n')

        self.lock.release()

    def run_benchmark(self):
        self.lock.acquire()
        if self.current_job_type != 'IDLE':
            self.lock.release()
            return

        self.status_data['run_count'] += 1
        run_id = self.status_data['run_count']
        self.status_data['results'] = [
            {
                'run_id':   run_id,
                'name':     "result_{0:06d}".format(run_id),
                'start':    time.asctime(),
                'state':    'RUN',
            }] + self.status_data['results']
        self.save_status()

        self.current_job_type = 'RUN'
        self.current_job_id = run_id
        self.current_job_name = "result_{0:06d}".format(run_id)
        self.current_job = RunBenchmark(self, run_id)
        self.current_job_output = ""
        self.current_job_start = time.time()
        self.current_job.start()
        self.lock.release()

    def run_build(self):
        self.lock.acquire()
        if self.current_job_type != 'IDLE':
            self.lock.release()
            return

        self.current_job_type = 'BUILD'
        self.current_job = RunDatabaseBuild(self)
        self.current_job_output = ""
        self.current_job_start = time.time()
        self.current_job.start()
        self.lock.release()

    def run_destroy(self):
        self.lock.acquire()
        if self.current_job_type != 'IDLE':
            self.lock.release()
            return

        self.current_job_type = 'DESTROY'
        self.current_job = RunDatabaseDestroy(self)
        self.current_job_output = ""
        self.current_job_start = time.time()
        self.current_job.start()
        self.lock.release()

    def cancel_job(self):
        self.lock.acquire()
        if self.current_job is None:
            print("no current job")
            self.lock.release()
            return
        if self.current_job.proc is None:
            print("current job has no process")
            self.lock.release()
            return
        for entry in self.status_data['results']:
            if entry['name'] == self.current_job_name:
                entry['state'] = 'CANCELED'
                break
        os.killpg(os.getpgid(self.current_job.proc.pid), signal.SIGKILL)
        self.save_status()
        self.lock.release()

class RunBenchmark(threading.Thread):
    def __init__(self, bench, run_id):
        threading.Thread.__init__(self)

        self.bench = bench
        self.run_id = run_id
        self.proc = None

    def run(self):
        last_props = os.path.join(self.bench.data_dir, 'last.properties')
        run_props = os.path.join(self.bench.data_dir, 'run.properties')
        result_dir = os.path.join(self.bench.data_dir, "result_{0:06d}".format(self.run_id))

        with open(last_props, 'r') as fd:
            props = fd.read()
        with open(run_props, 'w') as fd:
            fd.write(props)
            fd.write("\n")
            fd.write("resultDirectory={0}\n".format(result_dir))
        with open(os.path.join(self.bench.data_dir, 'run_seq.dat'), 'w') as fd:
            fd.write(str(self.run_id - 1) + '\n')

        cmd = ['./runBenchmark.sh', run_props, ]
        self.proc = subprocess.Popen(cmd,
                                     stdout = subprocess.PIPE,
                                     stderr = subprocess.STDOUT,
                                     stdin = None,
                                     preexec_fn = os.setsid)
        while True:
            line = self.proc.stdout.readline().decode('utf-8')
            if len(line) == 0:
                break
            self.bench.add_job_output(line)
        self.proc.wait()
        rc = self.proc.returncode
        self.proc = None

        if rc != 0:
            self.bench.add_job_output("\n\nBenchmarkSQL had exit code {0}\n".format(rc))

        # ----
        # Read the current run properties and parse them this time.
        # We need to get the reportScript= property from that.
        # ----
        jprop = jproperties.Properties()
        with open(run_props, "rb") as fd:
            jprop.load(fd, 'utf-8')
        if 'reportScript' in jprop:

            self.bench.add_job_output("\nBenchmarkSQL run complete - generating report\n")
            cmd = shlex.split(jprop['reportScript'].data)
            cmd.append('--resultdir')
            cmd.append(result_dir)
            self.proc = subprocess.Popen(cmd,
                                         stdout = subprocess.PIPE,
                                         stderr = subprocess.STDOUT,
                                         stdin = None,
                                         preexec_fn = os.setsid)
            while True:
                line = self.proc.stdout.readline().decode('utf-8')
                if len(line) == 0:
                    break
                self.bench.add_job_output(line)
            self.proc.wait()
            rc = self.proc.returncode
            self.proc = None

            if rc != 0:
                self.bench.add_job_output("\n\nreportScript had exit code {0} - report may be incomplete\n".format(rc))

        else:
            self.bench.add_job_output("\nBenchmarkSQL run complete\n")

        result_log = os.path.join(result_dir, 'console.log')
        with codecs.open(result_log, 'w', encoding='utf8') as fd:
            fd.write(self.bench.current_job_output)

class RunDatabaseBuild(threading.Thread):
    def __init__(self, bench):
        threading.Thread.__init__(self)

        self.bench = bench
        self.proc = None

    def run(self):
        last_props = os.path.join(self.bench.data_dir, 'last.properties')
        run_props = os.path.join(self.bench.data_dir, 'run.properties')

        with open(last_props, 'r') as fd:
            props = fd.read()
        with open(run_props, 'w') as fd:
            fd.write(props)

        cmd = ['./runDatabaseBuild.sh', run_props, ]
        self.proc = subprocess.Popen(cmd,
                                     stdout = subprocess.PIPE,
                                     stderr = subprocess.STDOUT,
                                     stdin = None,
                                     preexec_fn = os.setsid)
        while True:
            line = self.proc.stdout.readline().decode('utf-8')
            if len(line) == 0:
                break
            self.bench.add_job_output(line)
        self.proc.wait()
        rc = self.proc.returncode
        self.proc = None

        if rc != 0:
            self.bench.add_job_output("\n\nBenchmarkSQL terminated with exit code {0}\n".format(rc))
        return

class RunDatabaseDestroy(threading.Thread):
    def __init__(self, bench):
        threading.Thread.__init__(self)

        self.bench = bench
        self.proc = None

    def run(self):
        last_props = os.path.join(self.bench.data_dir, 'last.properties')
        run_props = os.path.join(self.bench.data_dir, 'run.properties')

        with open(last_props, 'r') as fd:
            props = fd.read()
        with open(run_props, 'w') as fd:
            fd.write(props)

        cmd = ['./runDatabaseDestroy.sh', run_props, ]
        self.proc = subprocess.Popen(cmd,
                                     stdout = subprocess.PIPE,
                                     stderr = subprocess.STDOUT,
                                     stdin = None,
                                     preexec_fn = os.setsid)
        while True:
            line = self.proc.stdout.readline().decode('utf-8')
            if len(line) == 0:
                break
            self.bench.add_job_output(line)
        self.proc.wait()
        rc = self.proc.returncode
        self.proc = None

        if rc != 0:
            self.bench.add_job_output("\n\nBenchmarkSQL terminated with exit code {0}\n".format(rc))
        return
