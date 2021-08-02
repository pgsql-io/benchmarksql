package com.github.pgsqlio.benchmarksql.jtpcc;
/*
 * jTPCCTDataList - A double linked list of Terminal Data objects.
 *
 */

public class jTPCCTDataList {
  private jTPCCTData head;
  private jTPCCTData tail;

  public jTPCCTDataList() {
    this.head = null;
    this.tail = null;
  }

  public void append(jTPCCTData tdata) {
    if (head == null) {
      head = tail = tdata;
      tdata.term_left = null;
      tdata.term_right = null;
    } else {
      tail.term_right = tdata;
      tdata.term_left = tail;
      tdata.term_right = null;
      tail = tdata;
    }
  }

  public void prepend(jTPCCTData tdata) {
    if (head == null) {
      head = tail = tdata;
      tdata.term_left = null;
      tdata.term_right = null;
    } else {
      head.term_left = tdata;
      tdata.term_left = null;
      tdata.term_right = head;
      head = tdata;
    }
  }

  public void remove(jTPCCTData tdata) {
    if (head == tdata)
      head = tdata.term_right;
    else
      tdata.term_left.term_right = tdata.term_right;
    if (tail == tdata)
      tail = tdata.term_left;
    else
      tdata.term_right.term_left = tdata.term_left;

    tdata.term_left = null;
    tdata.term_right = null;
  }

  public jTPCCTData first() {
    return head;
  }

  public jTPCCTData last() {
    return tail;
  }

  public jTPCCTData next(jTPCCTData tdata) {
    return tdata.term_right;
  }

  public jTPCCTData prev(jTPCCTData tdata) {
    return tdata.term_left;
  }

  public void truncate() {
    jTPCCTData next;

    while (head != null) {
      next = head.term_right;
      head.term_left = null;
      head.term_right = null;
      head = next;
    }
    tail = null;
  }
}
