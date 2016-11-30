/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.event;

import java.util.Map;
import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.internal.StringMap;

/**
 * 
 * Send email notifications
 *
 */

public class EmailNotification implements Notifications {
  static Logger logger = LoggerFactory.getLogger(EmailNotification.class);
  private ZeppelinConfiguration conf;

  public EmailNotification(ZeppelinConfiguration conf) {
    this.conf = conf;
  }

  @Override
  public void start(Map<String, Object> config, String msg) {
    Map<String, Object> notification = (Map<String, Object>) config.get("notification");
    Map<String, Object> emailMap = (Map<String, Object>) notification.get("email");
    String onStart = (String) emailMap.get("start");
    for (String email : onStart.split(",")) {
      logger.info("Send email to: " + email);
      sendEmail(onStart, msg);
    }
  }

  @Override
  public void finish(Map<String, Object> config, String msg) {
    Map<String, Object> notification = (Map<String, Object>) config.get("notification");
    Map<String, Object> emailMap = (Map<String, Object>) notification.get("email");
    String onSuccess = (String) emailMap.get("finish");
    for (String email : onSuccess.split(",")) {
      logger.info("Send email to: " + email);
      sendEmail(email, msg);
    }
  }

  @Override
  public void error(Map<String, Object> config, String msg) {
    Map<String, Object> notification = (Map<String, Object>) config.get("notification");
    Map<String, Object> emailMap = (Map<String, Object>) notification.get("email");
    String onError = (String) emailMap.get("error");
    for (String email : onError.split(",")) {
      logger.info("Send email to: " + email);
      sendEmail(onError, msg);
    }
  }

  public void sendEmail(String email, String msg) {
    Email sessionEmail = new SimpleEmail();

    try {
      sessionEmail.setSmtpPort(Integer.parseInt(conf.getString(ConfVars.ZEPPELIN_SMTP_PORT)));
      sessionEmail.setAuthenticator(new DefaultAuthenticator(
          conf.getString(ConfVars.ZEPPELIN_SMTP_USER),
          conf.getString(ConfVars.ZEPPELIN_SMTP_PASS)));
      sessionEmail.setHostName(conf.getString(ConfVars.ZEPPELIN_SMTP_HOST));

      sessionEmail.getMailSession().getProperties().put("mail.smtp.host", 
          conf.getString(ConfVars.ZEPPELIN_SMTP_HOST));
      sessionEmail.getMailSession().getProperties().put("mail.smtp.protocol",
          conf.getString(ConfVars.ZEPPELIN_SMTP_PROTOCOL));
      sessionEmail.getMailSession().getProperties().put("mail.smtp.port",
          conf.getString(ConfVars.ZEPPELIN_SMTP_PORT));
      sessionEmail.getMailSession().getProperties().put("mail.smtp.starttls.enable",
          conf.getString(ConfVars.ZEPPELIN_SMTP_STARTTLS));
      sessionEmail.getMailSession().getProperties().put("mail.smtp.auth",
          conf.getString(ConfVars.ZEPPELIN_SMTP_AUTH));
      sessionEmail.getMailSession().getProperties().put("mail.smtp.socketFactory.port",
          conf.getString(ConfVars.ZEPPELIN_SMTP_SOCKETFACTORY));
      sessionEmail.getMailSession().getProperties().put("mail.smtp.socketFactory.class", 
          conf.getString(ConfVars.ZEPPELIN_SMTP_SOCKETFACTORY_CLASS));

      sessionEmail.setFrom(conf.getString(ConfVars.ZEPPELIN_SMTP_USER));
      sessionEmail.addTo(email);
      sessionEmail.setSubject("Note scheduler in Zeppelin");  
      sessionEmail.setMsg(msg);
      sessionEmail.send();

    } catch (EmailException e) {
      logger.error("Error: ", e);
    }
  }


}
