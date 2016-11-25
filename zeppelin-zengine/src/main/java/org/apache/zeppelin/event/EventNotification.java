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
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Paragraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Event notifications
 */
public class EventNotification {
  static Logger logger = LoggerFactory.getLogger(EventNotification.class);
  private static ZeppelinConfiguration conf;
  Notifications emailNotification;

  public EventNotification(ZeppelinConfiguration conf) {
    this.conf = conf;
    emailNotification = new EmailNotification(conf);
  }

  public void enabledNotification(Note note) {
    Map<String, Object> config = note.getConfig();

    emailNotification.start(config, "Start execute note " + note.getName());
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
          emailNotification.error(config, msg);
        }
      }
    }
    emailNotification.finish(config, "Note " + note.getName() + " has finish.");
  }
}
