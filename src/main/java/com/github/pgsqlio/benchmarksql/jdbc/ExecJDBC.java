package com.github.pgsqlio.benchmarksql.jdbc;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ExecJDBC - Command line program to process SQL DDL statements, from a text input file, to any
 * JDBC Data Source
 *
 * Copyright (C) 2004-2016, Denis Lussier Copyright (C) 2016, Jan Wieck
 *
 */
public class ExecJDBC {

  private static Logger log = LogManager.getLogger(ExecJDBC.class);

  public static void main(String[] args) {

    Connection conn = null;
    Statement stmt = null;
    String rLine = null;
    String sLine = null;
    StringBuffer sql = new StringBuffer();

    try {

      Properties ini = new Properties();
      ini.load(new FileInputStream(System.getProperty("prop")));

      // Register jdbcDriver
      Class.forName(ini.getProperty("driver"));

      // make connection
      conn = DriverManager.getConnection(ini.getProperty("conn"), ini.getProperty("user"),
          ini.getProperty("password"));
      conn.setAutoCommit(true);

      // Retrieve datbase type
      String dbType = ini.getProperty("db");

      // For oracle : Boolean that indicates whether or not there is a statement ready to be
      // executed.
      Boolean ora_ready_to_execute = false;

      // Create Statement
      stmt = conn.createStatement();

      // Open inputFile
      BufferedReader in = new BufferedReader(new FileReader(getSysProp("commandFile", null)));

      // loop thru input file and concatenate SQL statement fragments
      while ((rLine = in.readLine()) != null) {

        if (ora_ready_to_execute == true) {
          String query = sql.toString();

          execJDBC(stmt, query);
          sql = new StringBuffer();
          ora_ready_to_execute = false;
        }

        String line = rLine.trim();

        if (line.length() != 0) {
          if (line.startsWith("--") && !line.startsWith("-- {")) {
            log.info(rLine); // print comment line
          } else {
            if (line.equals("$$")) {
              sql.append(rLine);
              sql.append("\n");
              while ((rLine = in.readLine()) != null) {
                line = rLine.trim();
                sql.append(rLine);
                sql.append("\n");
                if (line.equals("$$")) {
                  break;
                }
              }
              continue;
            }

            if (line.startsWith("-- {")) {
              sql.append(rLine);
              sql.append("\n");
              while ((rLine = in.readLine()) != null) {
                line = rLine.trim();
                sql.append(rLine);
                sql.append("\n");
                if (line.startsWith("-- }")) {
                  ora_ready_to_execute = true;
                  break;
                }
              }
              continue;
            }

            if (line.endsWith("\\;")) {
              sql.append(rLine.replaceAll("\\\\;", ";"));
              sql.append("\n");
            } else {
              sql.append(line.replaceAll("\\\\;", ";"));
              if (line.endsWith(";")) {
                String query = sql.toString();

                execJDBC(stmt, query.substring(0, query.length() - 1));
                sql = new StringBuffer();
              } else {
                sql.append("\n");
              }
            }
          }

        } // end if

      } // end while

      in.close();

    } catch (IOException ie) {
      log.error(ie.getMessage());
      log.info(ie);
      System.exit(1);
    } catch (SQLException se) {
      log.error(se.getMessage());
      log.info(se);
      System.exit(1);
    } catch (Exception e) {
      log.error(e);
      System.exit(1);
      // exit Cleanly
    } finally {
      try {
        if (conn != null)
          conn.close();
      } catch (SQLException se) {
        log.error(se);
      } // end finally

    } // end try

  } // end main


  static void execJDBC(Statement stmt, String query) {

    log.info("{};", query);

    try {
      stmt.execute(query);
    } catch (SQLException se) {
      log.error(se.getMessage());
      log.info(se);
    } // end try

  } // end execJDBCCommand

  public static String getSysProp(String inSysProperty, String defaultValue) {

    String outPropertyValue = null;

    try {
      outPropertyValue = System.getProperty(inSysProperty, defaultValue);
    } catch (Exception e) {
      log.error("Error Reading Required System Property '{}'", inSysProperty);
    }

    return (outPropertyValue);

  } // end getSysProp

} // end ExecJDBC Class
