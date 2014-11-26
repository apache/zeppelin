package com.nflabs.zeppelin.socket;

import java.util.HashMap;
import java.util.Map;

/**
 * Zeppelin websocker massage template class.
 * 
 * @author Leemoonsoo
 *
 */
public class Message {
  /**
   * Representation of event type.
   * 
   * @author Leemoonsoo
   *
   */
  public static enum OP {
    GET_NOTE, // [c-s] client load note
              // @param id note id

    NOTE, // [s-c] note info
          // @param note serlialized Note object

    PARAGRAPH, // [s-c] paragraph info
               // @param paragraph serialized paragraph object

    PROGRESS, // [s-c] progress update
              // @param id paragraph id
              // @param progress percentage progress

    NEW_NOTE, // [c-s] create new notebook
    DEL_NOTE, // [c-s] delete notebook
              // @param id note id
    NOTE_UPDATE,

    RUN_PARAGRAPH, // [c-s] run paragraph
                   // @param id paragraph id
                  // @param paragraph paragraph content.ie. script
                  // @param config paragraph config
                  // @param params paragraph params

    COMMIT_PARAGRAPH, // [c-s] commit paragraph
                      // @param id paragraph id
                      // @param title paragraph title
                      // @param paragraph paragraph content.ie. script
                      // @param config paragraph config
                      // @param params paragraph params

    CANCEL_PARAGRAPH, // [c-s] cancel paragraph run
                      // @param id paragraph id

    MOVE_PARAGRAPH, // [c-s] move paragraph order
                    // @param id paragraph id
                    // @param index index the paragraph want to go

    INSERT_PARAGRAPH, // [c-s] create new paragraph below current paragraph
                      // @param target index

    COMPLETION, // [c-s] ask completion candidates
                // @param id
                // @param buf current code
                // @param cursor cursor position in code

    COMPLETION_LIST, // [s-c] send back completion candidates list
                     // @param id
                     // @param completions list of string

    LIST_NOTES, // [c-s] ask list of note

    NOTES_INFO, // [s-c] list of note infos
                // @param notes serialized List<NoteInfo> object

    PARAGRAPH_REMOVE,
  }

  public OP op;
  public Map<String, Object> data = new HashMap<String, Object>();

  public Message(OP op) {
    this.op = op;
  }

  public Message put(String k, Object v) {
    data.put(k, v);
    return this;
  }

  public Object get(String k) {
    return data.get(k);
  }
}
