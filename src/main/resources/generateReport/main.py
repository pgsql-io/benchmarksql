#!/usr/bin/env python3

import os
import sys
import jinja2
import base64

from bmsqlResult import *
from bmsqlPlot import *

def main():
    if len(sys.argv) < 2:
        usage()
        return 2

    result = bmsqlResult(sys.argv[1])
    for tt in result.ttypes:
        break
        print("count {} = {}".format(tt, result.num_trans(tt)))
        print("mix {} = {:.3f}".format(tt, result.trans_mix(tt)))
        print("avg {} = {:.3f}".format(tt, result.avg_latency(tt)))
        print("max {} = {:.3f}".format(tt, result.max_latency(tt)))
        print("90th {} = {:.3f}".format(tt, result.percentile(tt, 0.9)))
        print("rbk {} = {} ({:.3f}%)".format(tt, result.num_rollbacks(tt),
                result.num_rollbacks(tt) / result.num_trans(tt) * 100))
        print("errors {} = {}".format(tt, result.num_errors(tt)))
        print("")

    reportFname = sys.argv[1].rstrip('/\\') + '.html'
    with open(reportFname, 'w') as fd:
        fd.write(generate_html(result))
    print("report generated as {}".format(reportFname))

def generate_html(result):
    env = jinja2.Environment(
        loader = jinja2.PackageLoader('bmsqlResult', 'templates')
    )

    plot = bmsqlPlot(result)
    tpmc_svg = plot.tpm_tpmc()
    delay_svg = plot.latency_and_delay()

    # ----
    # Collect all the data the template needs
    # ----
    data = {
        'ttypes': result.ttypes,
        'runinfo': result.runinfo,
        'summary': summary_data(result),
        'mix_warn': False,
        'rbk_warn': False,
        'tpm_c': '{:.2f}'.format(result.tpm_c()),
        'tpm_total': '{:.2f}'.format(result.tpm_total()),
        'tpm_percent': '{:.2f}'.format((result.tpm_c() * 100)
            / (12.86 * float(result.runinfo['runWarehouses']))),
        'tpmc_svg': base64.b64encode(tpmc_svg.encode('utf-8')).decode('utf-8'),
        'delay_svg': base64.b64encode(delay_svg.encode('utf-8')).decode('utf-8'),
    }

    # Propagate the mix_warn flag up to the toplevel
    for tt in result.ttypes:
        if data['summary'][tt]['mix_warn']:
            data['mix_warn'] = True

    template = env.get_template('report_default.html')
    return template.render(**data)

def summary_data(result):
    color_ok = '#008000'
    color_warn = '#f08000'
    color_error = '#c00000'

    data = {}
    for tt in result.ttypes:
        # ----
        # Determine the percentiles and the latency limit
        # ----
        if tt == 'DELIVERY_BG':
            limit = 80.0
        elif tt == 'STOCK_LEVEL':
            limit = 20.0
        else:
            limit = 5.0
        ninth = result.percentile(tt, 0.9)
        n5th = result.percentile(tt, 0.95)
        n8th = result.percentile(tt, 0.98)

        # ----
        # From that numbers we derive the color for the percentile numbers
        # ----
        color_limit = color_ok
        color_ninth = color_ok
        color_n5th = color_ok
        color_n8th = color_ok
        if ninth > limit:
            color_limit = color_error
            color_ninth = color_error
        if n5th > limit:
            if ninth <= limit:
                color_limit = color_warn
            color_n5th = color_warn
        if n8th > limit:
            if ninth <= limit:
                color_limit = color_warn
            color_n8th = color_warn

        # ----
        # Indicate if the transaction mix percentage is too low
        # by turning the number red.
        # ----
        mix = result.num_trans(tt) / result.total_trans * 100
        mix_warn = False
        color_mix = '#000000'
        if tt == 'NEW_ORDER' or tt == 'DELIVERY_BG':
            pass
        elif tt == 'PAYMENT':
            if mix < 43.0:
                color_mix = color_error
                mix_warn = True
        else:
            if mix < 4.0:
                color_mix = color_error
                mix_warn = True
        if tt == 'DELIVERY_BG':
            mix = 'N/A'
        else:
            mix = '{:.3f}%'.format(mix)

        # ----
        # Percentage of rollback is only relevant for NEW_ORDER
        # ----
        rbk = result.num_rollbacks(tt) / result.num_trans(tt) * 100
        color_rbk = '#000000'
        if tt == 'NEW_ORDER':
            if rbk < 1.0:
                color_rbk = color_error
            rbk = '{:.3f}%'.format(rbk)
        else:
            rbk = 'N/A'

        data[tt] = {
            'count': result.num_trans(tt),
            'mix': mix,
            'mix_warn': mix_warn,
            'style_mix': 'style="color:{};"'.format(color_mix),
            'avg': "{:.3f}".format(result.avg_latency(tt)),
            'max': "{:.3f}".format(result.max_latency(tt)),
            'ninth': "{:.3f}".format(ninth),
            'n5th': "{:.3f}".format(n5th),
            'n8th': "{:.3f}".format(n8th),
            'limit': "{:.3f}".format(limit),
            'style_ninth': 'style="color:{};"'.format(color_ninth),
            'style_n5th': 'style="color:{};"'.format(color_n5th),
            'style_n8th': 'style="color:{};"'.format(color_n8th),
            'style_limit': 'style="color:{};"'.format(color_limit),
            'rbk': rbk,
            'style_rbk': 'style="color:{};"'.format(color_rbk),
            'errors': result.num_errors(tt),
        }
    return data

def usage():
    sys.stderr.write("""usage: {} RESULT_DIR\n""".format(
            os.path.basename(sys.argv[0])))

if __name__ == '__main__':
    main()
