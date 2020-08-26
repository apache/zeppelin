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

package org.apache.zeppelin.client.examples;

import org.apache.zeppelin.client.ClientConfig;
import org.apache.zeppelin.client.NoteResult;
import org.apache.zeppelin.client.ParagraphResult;
import org.apache.zeppelin.client.ZeppelinClient;


/**
 * Basic example of running zeppelin note/paragraph via ZeppelinClient (low level api)
 */
public class ZeppelinClientExample {

  public static void main(String[] args) throws Exception {
    ClientConfig clientConfig = new ClientConfig("http://localhost:8080");
    ZeppelinClient zClient = new ZeppelinClient(clientConfig);

    String zeppelinVersion = zClient.getVersion();
    System.out.println("Zeppelin version: " + zeppelinVersion);

    String notePath = "/zeppelin_client_examples/note_1";
    String noteId = null;
    try {
      noteId = zClient.createNote(notePath);
      System.out.println("Created note: " + noteId);

      String paragraphId = zClient.addParagraph(noteId, "the first paragraph", "%python print('hello world')");
      ParagraphResult paragraphResult = zClient.executeParagraph(noteId, paragraphId);
      System.out.println("Added new paragraph and execute it.");
      System.out.println("Paragraph result: " + paragraphResult);

      String paragraphId2 = zClient.addParagraph(noteId, "the second paragraph",
              "%python\nimport time\ntime.sleep(5)\nprint('done')");
      zClient.submitParagraph(noteId, paragraphId2);
      zClient.waitUtilParagraphRunning(noteId, paragraphId2);
      zClient.cancelParagraph(noteId, paragraphId2);
      paragraphResult = zClient.waitUtilParagraphFinish(noteId, paragraphId2);
      System.out.println("Added new paragraph, submit it then cancel it");
      System.out.println("Paragraph result: " + paragraphResult);

      NoteResult noteResult = zClient.executeNote(noteId);
      System.out.println("Execute note and the note result: " + noteResult);

      zClient.submitNote(noteId);
      noteResult = zClient.waitUntilNoteFinished(noteId);
      System.out.println("Submit note and the note result: " + noteResult);
    } finally {
      if (noteId != null) {
        zClient.deleteNote(noteId);
        System.out.println("Note " + noteId + " is deleted");
      }
    }
  }
}
