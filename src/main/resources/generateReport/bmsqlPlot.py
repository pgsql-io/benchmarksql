
import os
import io
import csv
import base64
import numpy
import warnings
import matplotlib.pyplot as pyplot
from mpl_toolkits.axes_grid1 import Divider, Size
from mpl_toolkits.axes_grid1.mpl_axes import Axes

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

    def delay_svg(self, b64encode = True):
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

        # ----
        # offset the timestamps by -rampupMins so that the graph
        # starts with negative minutes elapsed and switches to
        # positive when the measurement begins.
        # ----
        offset = (int(runinfo['rampupMins'])) * 60.0

        # ----
        # NEW_ORDER transaction delay. First get the timestamp
        # and delay numbers from the result data.
        # The X vector then is the sorted unique timestamps rounded
        # to an interval.
        # ----
        interval = 10
        data = numpy.array([[(int(tup[0] / interval) * interval - offset) / 60,
                           tup[1], tup[5]]
                           for tup in result.result_ttype['NEW_ORDER']])
        x = sorted(numpy.unique(data[:,0]))

        # ----
        # The Y vector is the sums of transactions delay divided by
        # the sums of the count, grouped by X.
        # ----
        y = []
        for ts in x:
            tmp = data[numpy.where(data[:,0] == ts)]
            y.append(numpy.sum(tmp[:,2]) / (numpy.sum(tmp[:,1]) + 0.000001))

        # ----
        # Plot the NEW_ORDER delay and add all the decorations
        # ----
        plt.plot(x, y, 'r', label = 'Delay')

        # ----
        # Now do the same aggregation for the latency
        # ----
        data = numpy.array([[(int(tup[0] / interval) * interval - offset) / 60,
                           tup[1], tup[2]]
                           for tup in result.result_ttype['NEW_ORDER']])

        # ----
        # The Y vector is similar by based on latency
        # ----
        y = []
        for ts in x:
            tmp = data[numpy.where(data[:,0] == ts)]
            y.append(numpy.sum(tmp[:,2]) / (numpy.sum(tmp[:,1]) + 0.000001))
        plt.plot(x, y, 'b', label = 'Latency')

        plt.set_title("NEW_ORDER Latency and Delay")
        plt.set_xlabel("Elapsed Minutes")
        plt.set_ylabel("Latency/Delay in ms")
        plt.legend(loc = 'upper left')
        plt.grid()

        buf = io.StringIO()
        pyplot.savefig(buf, format = 'svg')

        if not b64encode:
            return buf.getvalue()
        return base64.b64encode(buf.getvalue().encode('utf-8')).decode('utf-8')

    def metric_svg(self, title = "undefined", ylabel = "undefined",
               metrics = [], b64encode = True):
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

        # ----
        # Draw all the requested graphs
        # ----
        for m in metrics:
            x, y = self._get_metric_xy(m)
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

    def cpu_svg(self, title = "CPU Usage", ylabel = "Percent",
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

        # ----
        # Gather all the relevant data
        # ----
        x, y_user = self._get_metric_xy({
            'device': 'cpu',
            'metric': 'percent-user',
        })
        _, y_sys = self._get_metric_xy({
            'device': 'cpu',
            'metric': 'percent-system',
        }, x)
        _, y_wait = self._get_metric_xy({
            'device': 'cpu',
            'metric': 'percent-wait',
        }, x)
        _, y_intr = self._get_metric_xy({
            'device': 'cpu',
            'metric': 'percent-interrupt',
        }, x)
        _, y_softirq = self._get_metric_xy({
            'device': 'cpu',
            'metric': 'percent-softirq',
        }, x)
        _, y_idle = self._get_metric_xy({
            'device': 'cpu',
            'metric': 'percent-idle',
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

    def memory_svg(self, title = "Memory Usage", unit = "Bytes",
               factor = 1.0, b64encode = True):
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

        x, y_used = self._get_metric_xy({
            'device': 'memory',
            'metric': 'memory-used',
            'factor': factor,
        })
        _, y_cached = self._get_metric_xy({
            'device': 'memory',
            'metric': 'memory-cached',
            'factor': factor,
        }, x)
        _, y_buffered = self._get_metric_xy({
            'device': 'memory',
            'metric': 'memory-buffered',
            'factor': factor,
        }, x)
        _, y_free = self._get_metric_xy({
            'device': 'memory',
            'metric': 'memory-free',
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
        if 'index' not in m:
            m['index'] = 1
        if m['device'] not in self.data:
            self.data[m['device']] = {}
        if m['metric'] not in self.data[m['device']]:
            csvdata = []
            path = os.path.join(self.result.datadir, m['device'],
                                m['metric'] + '.csv')
            with open(path, newline = '') as fd:
                rdr = csv.reader(fd)
                for row in rdr:
                    csvdata.append([float(d) for d in row])
            
            interval = 10
            data = numpy.array([[(int(tup[0] / interval) * interval - offset) / 60,]
                       + [val * m['factor'] for val in tup[1:]]
                       for tup in csvdata])
            self.data[m['device']][m['metric']] = data
        else:
            data = self.data[m['device']][m['metric']]

        if x is None:
            x = sorted(numpy.unique(data[:,0]))

        with warnings.catch_warnings():
            warnings.simplefilter("ignore", category=RuntimeWarning)
            y = []
            for ts in x:
                tmp = data[numpy.where(data[:,0] == ts)]
                y.append(numpy.mean(tmp[:,m['index']]))
        
        return x, y
