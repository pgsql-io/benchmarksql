# ----
# BenchmarkSQL.py - Control interface to run BenchmarkSQL components
# ----

# -*- coding: utf-8

import codecs
import os
import subprocess
import shutil
import signal
import threading
import time
import json

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
        self.run_dir = os.path.abspath(os.path.join(head, '..', 'run'))
        if not os.path.exists(os.path.join(self.run_dir, 'runBenchmark.sh')):
            raise Exception("BenchmarkSQL run components not found at '{0}'".format(self.run_dir))
        os.chdir(self.run_dir)

        # ----
        # The "results" directory defaults to benchmarksql/results. If
        # that does not exist we assume to live in the docker container
        # and try /data.
        # ----
        self.data_dir = os.path.abspath(os.path.join(head, '..', 'results'))
        if os.path.isdir(self.data_dir):
            print "using existing", self.data_dir
        else:
            if os.path.isdir('/data'):
                self.data_dir = '/data'
                print "assuming docker container, using", self.data_dir
            else:
                os.mkdir(self.data_dir)
                print "created empty directory", self.data_dir

        self.status_file = os.path.join(self.data_dir, 'status.json')
        self.status_data = self.load_status()

        self.current_job_type = 'IDLE'
        self.current_job = None
        self.current_job_name = ""
        self.current_job_output = ""
        self.current_job_start = 0.0

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
            }

    def save_status(self):
        """
        Save the current status data into the /data/status.json file
        """
        with open(self.status_file, 'w') as fd:
            fd.write(json.dumps(self.status_data, indent = 4))

    def get_job_type(self):
        if self.current_job is not None:
            self.current_job.join(0.0)
            if not self.current_job.is_alive():
                self.current_job = None
                self.current_job_type = 'IDLE'
                self.current_job_name = ""
        return self.current_job_type

    def get_job_runtime(self):
        if self.current_job is None:
            return "--:--:--"
        runtime = int(time.time() - self.current_job_start)
        return "{0:02d}:{1:02d}:{2:02d}".format(
                runtime / 3600, (runtime / 60) % 60, runtime % 60)

    def get_job_output(self):
        return self.current_job_output

    def get_properties(self):
        last_path = os.path.join(self.data_dir, 'last.properties')
        if os.path.exists(last_path):
            with open(last_path, 'r') as fd:
                properties = fd.read()
            return properties
        sample_path = os.path.join(self.run_dir, 'sample.flask.properties')
        with open(sample_path, 'r') as fd:
            properties = fd.read()
        return properties

    def get_results(self):
        results = []
        for result in self.status_data['results']:
            result_dir = os.path.join(self.data_dir, result['name'])
            if not os.path.exists(result_dir):
                continue
            results.append(
                (
                    result['run_id'],
                    result['name'],
                    result['start'],
                    result['name'] == self.current_job_name,
                )
            )
        return results

    def save_properties(self, properties):
        last_path = os.path.join(self.data_dir, 'last.properties')
        with open(last_path, 'w') as fd:
            fd.write(properties)

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

    def delete_result(self, run_id):
        try:
            run_id = int(run_id)
        except Exception as e:
            return "What?"

        html_path = os.path.join(self.data_dir, "result_{0:06d}.html".format(run_id))
        data_path = os.path.join(self.data_dir, "result_{0:06d}".format(run_id))
        new_results = [x for x in self.status_data['results'] if x['run_id'] != run_id]

        try:
            shutil.rmtree(data_path)
        except Exception as e:
            print str(e)
        try:
            os.remove(html_path)
        except Exception as e:
            print str(e)
        self.status_data['results'] = new_results
        self.save_status()

    def run_benchmark(self):
        self.status_data['run_count'] += 1
        run_id = self.status_data['run_count']
        self.status_data['results'] = [
            {
                'run_id':   run_id,
                'name':     "result_{0:06d}".format(run_id),
                'start':    time.asctime(),
            }] + self.status_data['results']
        self.save_status()

        self.current_job_type = 'RUN'
        self.current_job_name = "result_{0:06d}".format(run_id)
        self.current_job = RunBenchmark(self, run_id)
        self.current_job_output = ""
        self.current_job_start = time.time()

        self.current_job.start()

    def run_build(self):
        self.current_job_type = 'BUILD'
        self.current_job = RunDatabaseBuild(self)
        self.current_job_output = ""
        self.current_job_start = time.time()

        self.current_job.start()

    def run_destroy(self):
        self.current_job_type = 'DESTROY'
        self.current_job = RunDatabaseDestroy(self)
        self.current_job_output = ""
        self.current_job_start = time.time()

        self.current_job.start()

    def cancel_job(self):
        if self.current_job is None:
            print "no current job"
            return
        if self.current_job.proc is None:
            print "current job has no process"
            return
        self.current_job.proc.send_signal(signal.SIGINT)

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
        with open(os.path.join(self.bench.run_dir, '.jTPCC_run_seq.dat'), 'w') as fd:
            fd.write(str(self.run_id - 1) + '\n')

        cmd = ['setsid', './runBenchmark.sh', run_props, ]
        self.proc = subprocess.Popen(cmd, stdout = subprocess.PIPE, stderr = subprocess.STDOUT, stdin = None)
        while True:
            line = self.proc.stdout.readline()
            if line == "":
                break
            self.bench.current_job_output += line
        self.proc.wait()
        rc = self.proc.returncode
        self.proc = None

        if rc != 0:
            self.bench.current_job_output += "\n\nBenchmarkSQL had exit code {0} - not generating report\n".format(rc)
            return

        self.bench.current_job_output += "\nBenchmarkSQL run complete - generating report\n"
        cmd = ['sh', './generateReport.sh', result_dir]
        self.proc = subprocess.Popen(cmd, stdout = subprocess.PIPE, stderr = subprocess.STDOUT, stdin = None)
        while True:
            line = self.proc.stdout.readline().decode('utf-8')
            if line == "":
                break
            self.bench.current_job_output += line
        self.proc.wait()
        self.proc = None

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

        cmd = ['setsid', './runDatabaseBuild.sh', run_props, ]
        self.proc = subprocess.Popen(cmd, 
            stdout = subprocess.PIPE, 
            stderr = subprocess.STDOUT,
            stdin = None,
            creationflags = 0)
        while True:
            line = self.proc.stdout.readline()
            if line == "":
                break
            self.bench.current_job_output += line
        self.proc.wait()
        rc = self.proc.returncode
        self.proc = None

        if rc != 0:
            self.bench.current_job_output += "\n\nBenchmarkSQL terminated with exit code {0}\n".format(rc)
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

        cmd = ['setsid', './runDatabaseDestroy.sh', run_props, ]
        self.proc = subprocess.Popen(cmd, stdout = subprocess.PIPE, stderr = subprocess.STDOUT, stdin = None)
        while True:
            line = self.proc.stdout.readline()
            if line == "":
                break
            self.bench.current_job_output += line
        self.proc.wait()
        rc = self.proc.returncode
        self.proc = None

        if rc != 0:
            self.bench.current_job_output += "\n\nBenchmarkSQL had exit code {0}\n".format(rc)
            return
