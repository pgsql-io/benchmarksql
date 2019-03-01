#!/usr/bin/env python

import flask
import benchmarksql
import os
import json

app = flask.Flask(__name__)
bench = benchmarksql.BenchmarkSQL()

@app.route('/', methods = ['POST', 'GET'])
def index():
    form = flask.request.form
    if 'action' in form:
        state = bench.get_job_type()

        if form['action'] == 'Load Oracle Sample':
            load_sample_properties('oracle')
        elif form['action'] == 'Load PostgreSQL Sample':
            load_sample_properties('postgresql')

        elif form['action'] == 'RUN' and state == 'IDLE':
            bench.save_properties(form['properties'])
            bench.run_benchmark()
            return flask.redirect(flask.url_for("index"))

        elif form['action'] == 'BUILD' and state == 'IDLE':
            bench.save_properties(form['properties'])
            bench.run_build()

        elif form['action'] == 'DESTROY' and state == 'IDLE':
            bench.save_properties(form['properties'])
            bench.run_destroy()

        elif form['action'] == 'CANCEL':
            bench.cancel_job()

    data = {}
    data['current_job_type'] = bench.get_job_type()
    data['current_job_runtime'] = bench.get_job_runtime()
    data['form'] = form
    data['properties'] = bench.get_properties()

    if data['current_job_type'] == 'IDLE':
        data['state_run'] = ''
        data['state_build'] = ''
        data['state_destroy'] = ''
        data['state_cancel'] = 'disabled'
        data['state_refresh'] = ''
    else:
        data['state_run'] = 'disabled'
        data['state_build'] = 'disabled'
        data['state_destroy'] = 'disabled'
        data['state_cancel'] = ''
        data['state_refresh'] = ''

    data['current_job_output'] = bench.get_job_output()
    data['url_job_status'] = flask.url_for('job_status')

    data['results'] = bench.get_results()
    # print "results:", data['results']

    return flask.render_template('main.html', **data)

@app.route('/job_status')
def job_status():
    result = [
            bench.get_job_type(),
            bench.get_job_runtime(),
            bench.get_job_output(),
        ]
    return json.dumps(result)

@app.route('/result_log/')
def result_log():
    args = flask.request.args
    return flask.Response(bench.get_log(args['run_id']), mimetype='text/plain')

@app.route('/result_show/')
def result_show():
    args = flask.request.args
    return bench.get_report(args['run_id'])

@app.route('/result_delete/')
def result_delete():
    args = flask.request.args
    bench.delete_result(args['run_id'])
    return flask.redirect(flask.url_for("index"))

def load_sample_properties(db_type):
    prop_file = os.path.join(bench.run_dir, "sample.{0}.properties".format(db_type))
    with open(prop_file, 'r') as fd:
        bench.save_properties(fd.read())
    print "loaded", prop_file

if __name__ == '__main__':
    app.run(host='0.0.0.0')
