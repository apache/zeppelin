package com.nflabs.zeppelin.server;

import java.util.ArrayList;

import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response.ResponseBuilder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Json response builder.
 * 
 * @author Leemoonsoo
 *
 * @param <T>
 */
public class JsonResponse<T> {
  private javax.ws.rs.core.Response.Status status;
  private String message;
  private T body;
  transient ArrayList<NewCookie> cookies;
  transient boolean pretty = false;

  public JsonResponse(javax.ws.rs.core.Response.Status status) {
    this.status = status;
    this.message = null;
    this.body = null;

  }

  public JsonResponse(javax.ws.rs.core.Response.Status status, String message) {
    this.status = status;
    this.message = message;
    this.body = null;
  }

  public JsonResponse(javax.ws.rs.core.Response.Status status, T body) {
    this.status = status;
    this.message = null;
    this.body = body;
  }

  public JsonResponse(javax.ws.rs.core.Response.Status status, String message, T body) {
    this.status = status;
    this.message = message;
    this.body = body;
  }

  public JsonResponse<T> setPretty(boolean pretty) {
    this.pretty = pretty;
    return this;
  }

  /**
   * Add cookie for building.
   * 
   * @param newCookie
   * @return
   */
  public JsonResponse<T> addCookie(NewCookie newCookie) {
    if (cookies == null) {
      cookies = new ArrayList<NewCookie>();
    }
    cookies.add(newCookie);

    return this;
  }

  /**
   * Add cookie for building.
   * 
   * @param name
   * @param value
   * @return
   */
  public JsonResponse<?> addCookie(String name, String value) {
    return addCookie(new NewCookie(name, value));
  }

  public String toString() {
    Gson gson = null;
    if (pretty) {
      gson = new GsonBuilder().setPrettyPrinting().create();
    } else {
      gson = new Gson();
    }
    return gson.toJson(this);
  }

  public javax.ws.rs.core.Response.Status getCode() {
    return status;
  }

  public void setCode(javax.ws.rs.core.Response.Status status) {
    this.status = status;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public T getBody() {
    return body;
  }

  public void setBody(T body) {
    this.body = body;
  }

  public javax.ws.rs.core.Response build() {
    ResponseBuilder r = javax.ws.rs.core.Response.status(status).entity(this.toString());
    if (cookies != null) {
      for (NewCookie nc : cookies) {
        r.cookie(nc);
      }
    }
    r.header("Access-Control-Allow-Origin", "*").header("Access-Control-Allow-Credentials", "true")
        .header("Access-Control-Allow-Headers", "authorization,Content-Type")
        .header("Access-Control-Allow-Methods", "POST, GET, OPTIONS , PUT, HEAD, DELETE");
    return r.build();
  }
}
