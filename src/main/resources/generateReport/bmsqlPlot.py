
import os
import io
import csv
import base64
import numpy
import warnings
import matplotlib.pyplot as pyplot
from mpl_toolkits.axes_grid1 import Divider, Size
from mpl_toolkits.axes_grid1.mpl_axes import Axes
import json

from generateReport import *

class bmsqlPlot:
    FIGSIZE = [10, 2.5]

    def __init__(self, result):
        self.result = result
        self.data = {}

    def tpmc_svg(self, b64encode = True):
        fig = pyplot.figure(figsize = self.FIGSIZE)

        h = [Size.Fixed(1.2), Size.Scaled(1.), Size.Fixed(.2)]
        v = [Size.Fixed(0.7), Size.Scaled(1.), Size.Fixed(.5)]

        divider = Divider(fig, (0.0, 0.0, 1., 1.), h, v, aspect=False)
        plt = Axes(fig, divider.get_position())
        plt.set_axes_locator(divider.new_locator(nx=1, ny=1))

        fig.add_axes(plt)

        result = self.result
        runinfo = result.runinfo

        # ----
        # The X limits are -rampupMins, runMins
        # ----
        plt.set_xlim(-int(runinfo['rampupMins']), int(runinfo['runMins']))
        plt.axvspan(-int(runinfo['rampupMins']), 0,
                    facecolor = '0.2', alpha = 0.1)

        # ----
        # offset the timestamps by -rampupMins so that the graph
        # starts with negative minutes elapsed and switches to
        # positive when the measurement begins.
        # ----
        offset = (int(runinfo['rampupMins'])) * 60.0

        # ----
        # NEW_ORDER transactions per minute. First get the timestamp
        # and number of transactions from the result data.
        # The X vector then is the sorted unique timestamps rounded
        # to an interval.
        # ----
        interval = 10
        data = numpy.array([[(int(tup[0] / interval) * interval - offset)
                           / 60, tup[1] * (60 / interval)]
                           for tup in result.result_ttype['NEW_ORDER']])
        x = sorted(numpy.unique(data[:,0]))

        # ----
        # The Y vector is the sums of transactions grouped by X
        # ----
        y = []
        for ts in x:
            tmp = data[numpy.where(data[:,0] == ts)]
            y.append(numpy.sum(tmp[:,1]))

        # ----
        # Plot the NOPM and add all the decorations
        # ----
        plt.plot(x, y, 'b')
        plt.set_title("NEW_ORDER Transactions per Minute (tpmC)")
        plt.set_xlabel("Elapsed Minutes")
        plt.set_ylabel("tpmC")
        plt.grid()

        buf = io.StringIO()
        pyplot.savefig(buf, format = 'svg')

        if not b64encode:
            return buf.getvalue()
        return base64.b64encode(buf.getvalue().encode('utf-8')).decode('utf-8')

    def delay_avg_svg(self, ttype, b64encode = True):
        max_ms = self.result.percentile(ttype, 0.99) * 1100.0
        fig = pyplot.figure(figsize = self.FIGSIZE)

        h = [Size.Fixed(1.2), Size.Scaled(1.), Size.Fixed(.2)]
        v = [Size.Fixed(0.7), Size.Scaled(1.), Size.Fixed(.5)]

        divider = Divider(fig, (0.0, 0.0, 1., 1.), h, v, aspect=False)
        plt = Axes(fig, divider.get_position())
        plt.set_axes_locator(divider.new_locator(nx=1, ny=1))


        fig.add_axes(plt)

        result = self.result
        runinfo = result.runinfo

        # ----
        # The X limits are -rampupMins, runMins
        # ----
        plt.set_xlim(-int(runinfo['rampupMins']), int(runinfo['runMins']))
        plt.axvspan(-int(runinfo['rampupMins']), 0,
                    facecolor = '0.2', alpha = 0.1)

        # ----
        # offset the timestamps by -rampupMins so that the graph
        # starts with negative minutes elapsed and switches to
        # positive when the measurement begins.
        # ----
        offset = (int(runinfo['rampupMins'])) * 60.0

        # ----
        # ttype transaction delay. First get the timestamp
        # and delay numbers from the result data.
        # The X vector then is the sorted unique timestamps rounded
        # to an interval.
        # ----
        interval = 10
        data = numpy.array([[(int(tup[0] / interval) * interval - offset) / 60,
                           tup[1], tup[5]]
                           for tup in result.result_ttype[ttype]])
        x = sorted(numpy.unique(data[:,0]))

        # ----
        # The Y vector is the sums of transactions delay divided by
        # the sums of the count, grouped by X.
        # ----
        y = []
        for ts in x:
            tmp = data[numpy.where(data[:,0] == ts)]
            ms = numpy.sum(tmp[:,2]) / (numpy.sum(tmp[:,1]) + 0.000001)
            if ms <= max_ms:
                y.append(ms)
            else:
                y.append(max_ms)

        # ----
        # Plot the ttype delay and add all the decorations
        # ----
        plt.plot(x, y, 'r', label = 'Delay')

        # ----
        # Now do the same aggregation for the latency
        # ----
        data = numpy.array([[(int(tup[0] / interval) * interval - offset) / 60,
                           tup[1], tup[2]]
                           for tup in result.result_ttype[ttype]])

        # ----
        # The Y vector is similar by based on latency
        # ----
        y = []
        for ts in x:
            tmp = data[numpy.where(data[:,0] == ts)]
            ms = numpy.sum(tmp[:,2]) / (numpy.sum(tmp[:,1]) + 0.000001)
            if ms <= max_ms:
                y.append(ms)
            else:
                y.append(max_ms)
        plt.plot(x, y, 'b', label = 'Latency')

        plt.set_title("{} Average Latency and Delay".format(ttype))
        plt.set_xlabel("Elapsed Minutes")
        plt.set_ylabel("Latency/Delay in ms")
        plt.legend(loc = 'upper left')
        plt.grid()

        buf = io.StringIO()
        pyplot.savefig(buf, format = 'svg')

        if not b64encode:
            return buf.getvalue()
        return base64.b64encode(buf.getvalue().encode('utf-8')).decode('utf-8')

    def delay_max_svg(self, ttype, b64encode = True):
        max_ms = self.result.percentile(ttype, 0.99) * 1500.0
        fig = pyplot.figure(figsize = self.FIGSIZE)

        h = [Size.Fixed(1.2), Size.Scaled(1.), Size.Fixed(.2)]
        v = [Size.Fixed(0.7), Size.Scaled(1.), Size.Fixed(.5)]

        divider = Divider(fig, (0.0, 0.0, 1., 1.), h, v, aspect=False)
        plt = Axes(fig, divider.get_position())
        plt.set_axes_locator(divider.new_locator(nx=1, ny=1))


        fig.add_axes(plt)

        result = self.result
        runinfo = result.runinfo

        # ----
        # The X limits are -rampupMins, runMins
        # ----
        plt.set_xlim(-int(runinfo['rampupMins']), int(runinfo['runMins']))
        plt.axvspan(-int(runinfo['rampupMins']), 0,
                    facecolor = '0.2', alpha = 0.1)

        # ----
        # offset the timestamps by -rampupMins so that the graph
        # starts with negative minutes elapsed and switches to
        # positive when the measurement begins.
        # ----
        offset = (int(runinfo['rampupMins'])) * 60.0

        # ----
        # ttype transaction max delay and latency
        # The X vector is the sorted unique timestamps rounded
        # to an interval.
        # ----
        interval = 10
        data = numpy.array([[(int(tup[0] / interval) * interval - offset) / 60,
                           tup[4], tup[7]]
                           for tup in result.result_ttype[ttype]])
        x = sorted(numpy.unique(data[:,0]))

        # ----
        # The Y vector for delay is the max of data[2]
        # ----
        y = []
        for ts in x:
            tmp = data[numpy.where(data[:,0] == ts)]
            ms = numpy.max(tmp[:,2])
            if ms <= max_ms:
                y.append(ms)
            else:
                y.append(max_ms)

        # ----
        # Plot the ttype delay and add all the decorations
        # ----
        plt.plot(x, y, 'r', label = 'Delay')

        # ----
        # The Y vector for latency is the same on data[1]
        # ----
        y = []
        for ts in x:
            tmp = data[numpy.where(data[:,0] == ts)]
            ms = numpy.max(tmp[:,1])
            if ms <= max_ms:
                y.append(ms)
            else:
                y.append(max_ms)
        plt.plot(x, y, 'b', label = 'Latency')

        plt.set_title("{} Maximum Latency and Delay".format(ttype))
        plt.set_xlabel("Elapsed Minutes")
        plt.set_ylabel("Latency/Delay in ms")
        plt.legend(loc = 'upper left')
        plt.grid()

        buf = io.StringIO()
        pyplot.savefig(buf, format = 'svg')

        if not b64encode:
            return buf.getvalue()
        return base64.b64encode(buf.getvalue().encode('utf-8')).decode('utf-8')

    def metric_svg(self, host = None, title = "undefined",
                   ylabel = "undefined", metrics = [], b64encode = True):
        fig = pyplot.figure(figsize = self.FIGSIZE)

        h = [Size.Fixed(1.2), Size.Scaled(1.), Size.Fixed(.2)]
        v = [Size.Fixed(0.7), Size.Scaled(1.), Size.Fixed(.5)]

        divider = Divider(fig, (0.0, 0.0, 1., 1.), h, v, aspect=False)
        plt = Axes(fig, divider.get_position())
        plt.set_axes_locator(divider.new_locator(nx=1, ny=1))

        fig.add_axes(plt)

        result = self.result
        runinfo = result.runinfo

        # ----
        # The X limits are -rampupMins, runMins
        # ----
        plt.set_xlim(-int(runinfo['rampupMins']), int(runinfo['runMins']))
        plt.axvspan(-int(runinfo['rampupMins']), 0,
                    facecolor = '0.2', alpha = 0.1)

        # ----
        # Draw all the requested graphs
        # ----
        x = None
        for m in metrics:
            if x is None:
                x, y = self._get_metric_tree(m)
            else:
                _, y = self._get_metric_tree(m, x)

            plt.plot(x, y, m['color'], label = m['label'])

        

        # ----
        # Title and labels
        # ----
        plt.set_title(title)
        plt.set_xlabel("Elapsed Minutes")
        plt.set_ylabel(ylabel)
        plt.legend(loc = 'upper left')
        plt.grid()

        # ----
        # Now turn this into an in-memory SVG and return it as requested
        # (raw or b64-encoded)
        # ----
        buf = io.StringIO()
        pyplot.savefig(buf, format = 'svg')

        if not b64encode:
            return buf.getvalue()
        return base64.b64encode(buf.getvalue().encode('utf-8')).decode('utf-8')

    def cpu_svg(self, host = None, title = "CPU Usage", ylabel = "Percent",
               b64encode = True):
        fig = pyplot.figure(figsize = self.FIGSIZE)

        h = [Size.Fixed(1.2), Size.Scaled(1.), Size.Fixed(.2)]
        v = [Size.Fixed(0.7), Size.Scaled(1.), Size.Fixed(.5)]

        divider = Divider(fig, (0.0, 0.0, 1., 1.), h, v, aspect=False)
        plt = Axes(fig, divider.get_position())
        plt.set_axes_locator(divider.new_locator(nx=1, ny=1))

        fig.add_axes(plt)

        result = self.result
        runinfo = result.runinfo

        # ----
        # The X limits are -rampupMins, runMins, Y limits are 0..100
        # ----
        plt.set_xlim(-int(runinfo['rampupMins']), int(runinfo['runMins']))
        plt.set_ylim(0, 100)
        plt.axvspan(-int(runinfo['rampupMins']), 0,
                    facecolor = '0.2', alpha = 0.1)

        # ----
        # Gather all the relevant data
        # ----
        x, y_user = self._get_metric_xy({
            'host': host,
            'metric': 'cpu.percent-user',
        })
        _, y_sys = self._get_metric_xy({
            'host': host,
            'metric': 'cpu.percent-system',
        }, x)
        _, y_wait = self._get_metric_xy({
            'host': host,
            'metric': 'cpu.percent-wait',
        }, x)
        _, y_intr = self._get_metric_xy({
            'host': host,
            'metric': 'cpu.percent-interrupt',
        }, x)
        _, y_softirq = self._get_metric_xy({
            'host': host,
            'metric': 'cpu.percent-softirq',
        }, x)
        _, y_idle = self._get_metric_xy({
            'host': host,
            'metric': 'cpu.percent-idle',
        }, x)

        # ----
        # It is possible that the mqtt based metric collector produces
        # CSV files with different numbers of lines. We cut all of them
        # to a combined minimum length.
        # ----
        min_len = min(len(x), len(y_user), len(y_sys), len(y_wait),
                      len(y_intr), len(y_softirq), len(y_idle))
        x = x[:min_len]
        y_user = y_user[:min_len]
        y_sys = y_sys[:min_len]
        y_wait = y_wait[:min_len]
        y_intr = y_intr[:min_len]
        y_softirq = y_softirq[:min_len]
        y_idle = y_idle[:min_len]

        # ----
        # Plot the CPU usage
        # ----
        plt.stackplot(x, y_user, y_sys, y_wait, y_intr, y_softirq, y_idle,
                      colors = ['g', 'b', 'r', 'c', 'm', 'y', ],
                      labels = ['User', 'System', 'Wait', 'Interrupt',
                                'SoftIrq', 'Idle', ])


        # ----
        # Title and labels
        # ----
        plt.set_title(title)
        plt.set_xlabel("Elapsed Minutes")
        plt.set_ylabel(ylabel)
        plt.legend(loc = 'upper left')
        plt.grid()

        # ----
        # Now turn this into an in-memory SVG and return it as requested
        # (raw or b64-encoded)
        # ----
        buf = io.StringIO()
        pyplot.savefig(buf, format = 'svg')

        if not b64encode:
            return buf.getvalue()
        return base64.b64encode(buf.getvalue().encode('utf-8')).decode('utf-8')

    def memory_svg(self, host = None, title = "Memory Usage",
               unit = "Bytes", factor = 1.0, b64encode = True):
        fig = pyplot.figure(figsize = self.FIGSIZE)

        h = [Size.Fixed(1.2), Size.Scaled(1.), Size.Fixed(.2)]
        v = [Size.Fixed(0.7), Size.Scaled(1.), Size.Fixed(.5)]

        divider = Divider(fig, (0.0, 0.0, 1., 1.), h, v, aspect=False)
        plt = Axes(fig, divider.get_position())
        plt.set_axes_locator(divider.new_locator(nx=1, ny=1))

        fig.add_axes(plt)

        result = self.result
        runinfo = result.runinfo

        # ----
        # The X limits are -rampupMins, runMins
        # ----
        plt.set_xlim(-int(runinfo['rampupMins']), int(runinfo['runMins']))
        plt.axvspan(-int(runinfo['rampupMins']), 0,
                    facecolor = '0.2', alpha = 0.1)

        x, y_used = self._get_metric_xy({
            'host': host,
            'metric': 'memory.memory-used',
            'factor': factor,
        })
        _, y_cached = self._get_metric_xy({
            'host': host,
            'metric': 'memory.memory-cached',
            'factor': factor,
        }, x)
        _, y_buffered = self._get_metric_xy({
            'host': host,
            'metric': 'memory.memory-buffered',
            'factor': factor,
        }, x)
        _, y_free = self._get_metric_xy({
            'host': host,
            'metric': 'memory.memory-free',
            'factor': factor,
        }, x)

        # ----
        # It is possible that the mqtt based metric collector produces
        # CSV files with different numbers of lines. We cut all of them
        # to a combined minimum length.
        # ----
        min_len = min(len(x), len(y_used), len(y_cached), len(y_buffered),
                      len(y_free))
        x = x[:min_len]
        y_used = y_used[:min_len]
        y_cached = y_cached[:min_len]
        y_buffered = y_buffered[:min_len]
        y_free = y_free[:min_len]

        plt.stackplot(x, y_used, y_cached, y_buffered, y_free,
                      colors = ['b', 'y', 'g', 'gray', ],
                      labels = ['Used', 'Cached', 'Buffered', 'Free', ])


        # ----
        # Title and labels
        # ----
        plt.set_title(title)
        plt.set_xlabel("Elapsed Minutes")
        plt.set_ylabel(unit)
        plt.legend(loc = 'upper left')
        plt.grid()

        # ----
        # Now turn this into an in-memory SVG and return it as requested
        # (raw or b64-encoded)
        # ----
        buf = io.StringIO()
        pyplot.savefig(buf, format = 'svg')

        if not b64encode:
            return buf.getvalue()
        return base64.b64encode(buf.getvalue().encode('utf-8')).decode('utf-8')

    def _get_metric_tree(self, m, x = None):
        if m['op'] == 'VAL':
            return self._get_metric_xy(m, x)

        elif m['op'] == 'ADD':
            x, y1 = self._get_metric_tree(m['lval'], x)
            _, y2 = self._get_metric_tree(m['rval'], x)
            return x, [l + r for l, r in zip(y1, y2)]
        else:
            raise Exception("Unknown operand '{}'".format(m['op']))

    def _get_metric_xy(self, m, x = None):
        # ----
        # offset the timestamps by -rampupMins so that the graph
        # starts with negative minutes elapsed and switches to
        # positive when the measurement begins.
        # ----
        runinfo = self.result.runinfo
        offset = (int(runinfo['rampupMins'])) * 60.0

        if 'factor' not in m:
            m['factor'] = 1.0

        interval = 10
        data = numpy.array([[(int(tup[0] / interval) * interval - offset) / 60,
                             (tup[1] or 0.0) * m['factor']]
                   for tup in self.result.os_metric[m['host']][m['metric']]])

        if x is None:
            x = sorted(numpy.unique(data[:,0]))

        with warnings.catch_warnings():
            warnings.simplefilter("ignore", category=RuntimeWarning)
            y = []
            for ts in x:
                tmp = data[numpy.where(data[:,0] == ts)]
                y.append(numpy.mean(tmp[:,1]))
        
        #print("data for metric", m)
        #print("x:", x)
        #print("y:", y)
        return x, y
