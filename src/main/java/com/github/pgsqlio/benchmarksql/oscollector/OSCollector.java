package com.github.pgsqlio.benchmarksql.oscollector;

import java.io.File;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.lang.ProcessBuilder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tools.ant.types.Commandline;

import com.github.pgsqlio.benchmarksql.jtpcc.jTPCC;

/**
 * OSCollector.java
 *
 * Copyright (C) 2016, Denis Lussier Copyright (C) 2016, Jan Wieck
 */
public class OSCollector {
  private static Logger log = LogManager.getLogger(OSCollector.class);

  private Runtime runTime;
  private Process collector;
  private OutputStream stdin;

  private Thread stdoutThread;
  private Thread stderrThread;

  public OSCollector(String cmdLine, File outputDir)
      throws IOException
  {
    /*
     * Assemble the command line for the collector by splitting
     * the osCollectorScript property into strings (bash style),
     * then append the --startepoch and --resultdir options.
     */
    ArrayList<String> cmd = new ArrayList<String>();
    cmd.addAll(Arrays.asList(Commandline.translateCommandline(cmdLine)));
    cmd.add("--startepoch");
    cmd.add(String.valueOf(jTPCC.csv_begin / 1000));
    cmd.add("--resultdir");
    cmd.add(outputDir.getPath());

    /*
     * Create a child process executing that command
     */
    ProcessBuilder pb = new ProcessBuilder(cmd);
    collector = pb.start();

    /*
     * Create two helpler threads that shovel stdout and stderr of
     * the child process into our logs.
     */
    stdin = collector.getOutputStream();
    stdoutThread = new Thread(new stdoutLogger(collector.getInputStream()));
    stderrThread = new Thread(new stderrLogger(collector.getErrorStream()));
    stdoutThread.start();
    stderrThread.start();
  }

  public void stop()
      throws IOException, InterruptedException
  {
    /*
     * We use closing stdin of the child process to signal it is
     * time to exit. So just close that stream and wait for it to
     * exit.
     */
    stdin.close();
    collector.waitFor();

    /*
     * Now wait until the stdout and stderr logger threads terminate,
     * which they will when the collector script child process exits.
     */
    try {
      stdoutThread.join();
    } catch (InterruptedException ie) {
      log.error("OSCollector, {}", ie.getMessage());
    }
    try {
      stderrThread.join();
    } catch (InterruptedException ie) {
      log.error("OSCollector, {}", ie.getMessage());
    }
  }

  private class stdoutLogger implements Runnable {
    private BufferedReader stdout;

    public stdoutLogger(InputStream stdout) {
      this.stdout = new BufferedReader(new InputStreamReader(stdout));
    }

    public void run() {
      String line;

      while (true) {
        try {
          line = stdout.readLine();
        } catch (IOException e) {
          log.error("OSCollector, {}", e.getMessage());
          break;
        }
        if (line == null)
          break;
        log.info(line);
      }
    }
  }

  private class stderrLogger implements Runnable {
    private BufferedReader stderr;

    public stderrLogger(InputStream stderr) {
      this.stderr = new BufferedReader(new InputStreamReader(stderr));
    }

    public void run() {
      String line;

      while (true) {
        try {
          line = stderr.readLine();
        } catch (IOException e) {
          log.error("OSCollector, {}", e.getMessage());
          break;
        }
        if (line == null)
          break;
        log.error(line);
      }
    }
  }
}
