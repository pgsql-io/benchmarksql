
import io
import numpy
import matplotlib.pyplot as pyplot

from bmsqlResult import *

class bmsqlPlot:
    def __init__(self, result):
        self.result = result

    def tpm_tpmc(self):
        fig, plt = pyplot.subplots(figsize=[12, 4])
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
        interval = 20
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
        return buf.getvalue()

    def latency_and_delay(self):
        fig, plt = pyplot.subplots(figsize=[12, 4])
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
        interval = 20
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
        return buf.getvalue()

