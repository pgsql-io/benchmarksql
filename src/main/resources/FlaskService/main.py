#!/usr/bin/env python3

import flask
import werkzeug
import benchmarksql
import os
import json
import sys

app = flask.Flask(__name__)
bench = benchmarksql.BenchmarkSQL()

@app.route('/', methods = ['POST', 'GET'])
def index():
    form = flask.request.form

    if 'action' not in form:
        if 'file' in flask.request.files:
            propf = flask.request.files['file']
            propf.seek(0)
            props = propf.read().decode('utf-8')
            if props != "":
                bench.save_properties(props)
                bench.status_data['filename'] = propf.filename
                bench.save_status()
                return flask.redirect(flask.url_for("index"))

    if 'action' in form:
        state = bench.get_job_type()

        if form['action'] == 'SAVE':
            bench.save_properties(form['properties'])
            headers = werkzeug.datastructures.Headers()
            headers.add('Content-Disposition', 'attachment', filename=bench.status_data['filename'])
            return flask.Response(form['properties'],
                                  headers = headers,
                                  mimetype = 'application/octet-stream')

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

    return flask.render_template('main.html', **data)

@app.route('/api_call', methods = ['POST', 'GET'])
def api_call():
    if flask.request.method == 'POST':
        data = flask.request.form
    elif flask.request.method == 'GET':
        data = flask.request.args
    else:
        result = {
                'rc': "OK",
                'message': "Unsupported request method '{0}'".format(flask.request.method),
            }
        return flask.Response(json.dumps(result), mimetype = 'application/json')

    try:
        req = json.loads(data['request'])
        if req['command'].lower() == 'status':
            result = api_call_status()
        elif req['command'].lower() == 'run':
            status = bench.get_status()
            if status['current_job_type'] != 'IDLE':
                raise Exception("Current job type is {0}".format(status['current_job_type']))
            if 'properties' in req:
                bench.save_properties(req['properties'])
            bench.run_benchmark()
            result = api_call_status()
        elif req['command'].lower() == 'build':
            status = bench.get_status()
            if status['current_job_type'] != 'IDLE':
                raise Exception("Current job type is {0}".format(status['current_job_type']))
            if 'properties' in req:
                bench.save_properties(req['properties'])
            bench.run_build()
            result = api_call_status()
        elif req['command'].lower() == 'destroy':
            status = bench.get_status()
            if status['current_job_type'] != 'IDLE':
                raise Exception("Current job type is {0}".format(status['current_job_type']))
            if 'properties' in req:
                bench.save_properties(req['properties'])
            bench.run_destroy()
            result = api_call_status()
        elif req['command'].lower() == 'cancel':
            bench.cancel_job()
            result = api_call_status()
        elif req['command'].lower() == 'txsummary':
            if 'run_id' not in req:
                raise Exception("command txsummary requires run_id")
            txsummary = bench.get_job_txsummary(req['run_id'])
            result = api_call_status()
            result['txsummary'] = txsummary
        else:
            result = {
                    'rc': 'ERROR',
                    'message': "Unknown API command '{0}'".format(req['command']),
                }
    except Exception as e:
        result = {
                'rc': 'ERROR',
                'message': str(e),
            }
    return flask.Response(json.dumps(result), mimetype = 'application/json')

def api_call_status():
    status = bench.get_status()
    return {
            'rc': 'OK',
            'message': 'Success',
            'current_job_type': status['current_job_type'],
            'current_job_id': status['current_job_id'],
            'current_job_name': status['current_job_name'],
            'current_job_output': status['current_job_output'],
            'current_job_start': status['current_job_start'],
            'current_job_properties': status['current_job_properties'],
        }


@app.route('/job_status')
def job_status():
    result = [
            bench.get_job_type(),
            bench.get_job_runtime(),
            bench.get_job_output(),
        ]
    return json.dumps(result)

@app.route('/cancel_job')
def cancel_job():
    result = [
            bench.cancel_job(),
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

def upload_properties():
    print("files:", flask.request.files, file=sys.stderr)
    pass

if __name__ == '__main__':
    app.run(host='0.0.0.0')
