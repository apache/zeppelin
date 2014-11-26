package com.nflabs.zeppelin.rest;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * Response wrapper.
 * 
 * @author anthonycorbacho
 *
 */
@XmlRootElement
public class NotebookResponse {
  private String msg;

  public NotebookResponse() {}

  public NotebookResponse(String msg) {
    this.msg = msg;
  }
}
