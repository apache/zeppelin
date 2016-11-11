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

import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.internal.StringMap;

/**
 * Event notifications
 */
public class EventNotification {
  static Logger logger = LoggerFactory.getLogger(EventNotification.class);
  private static ZeppelinConfiguration conf;
  
  public EventNotification(ZeppelinConfiguration conf) {
    this.conf = conf;
  }
  
  public void schedulerEvent(Note note) {
    StringMap email = (StringMap) note.getConfig().get("email");

    String onStart = (String) email.get("start");
    String onSuccess = (String) email.get("success");
    String onError = (String) email.get("error");
    
    if (!onStart.isEmpty()) {
      //send email when start
      sendEmail(onStart, "Start execute note " + note.getName());
    }
    
    while (!note.getLastParagraph().isTerminated()) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        logger.error(e.toString(), e);
      }
      for (Paragraph para : note.paragraphs) {
        if (para.getStatus().isError()) {
          //improve mail messages
          String msg = "Error in paragraphs ";
          if (para.getTitle() != null) {
            msg = msg + para.getTitle() + "\n"
                + para.getResult().message();
              
          } else {
            msg = msg + para.getId() + "\n"
                + para.getResult().message();
          }
          if (!onError.isEmpty()){
            //send error email
            sendEmail(onError, msg);  
          }
        }
      }
    }
    
    if (!onSuccess.isEmpty()) {
      //send email when finish
      sendEmail(onSuccess, "Note " + note.getName() + " has finish."); 
    }
  }
  
  public void paragraphsEvent() {}
  
  public void notebookEvent() {}
  
  public static void sendEmail(String email, String text) {
    
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
      for (String mail : email.split(",")) {
        logger.info("Send email to " + mail);
        sessionEmail.addTo(mail);
      }      

      sessionEmail.setSubject("Note scheduler in Zeppelin");  
      sessionEmail.setMsg(text);
      sessionEmail.send();

    } catch (EmailException e) {
      logger.error("Error: ", e);
    }
  }
}
