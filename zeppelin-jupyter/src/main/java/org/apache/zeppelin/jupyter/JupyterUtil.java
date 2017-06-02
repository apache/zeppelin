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
package org.apache.zeppelin.jupyter;

import com.google.common.base.Joiner;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Type;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.jupyter.nbformat.Cell;
import org.apache.zeppelin.jupyter.nbformat.CodeCell;
import org.apache.zeppelin.jupyter.nbformat.DisplayData;
import org.apache.zeppelin.jupyter.nbformat.Error;
import org.apache.zeppelin.jupyter.nbformat.ExecuteResult;
import org.apache.zeppelin.jupyter.nbformat.MarkdownCell;
import org.apache.zeppelin.jupyter.nbformat.Nbformat;
import org.apache.zeppelin.jupyter.nbformat.Output;
import org.apache.zeppelin.jupyter.nbformat.RawCell;
import org.apache.zeppelin.jupyter.nbformat.Stream;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.user.AuthenticationInfo;

/**
 *
 */
public class JupyterUtil {

  private static final String TEXT_PLAIN = "text/plain";
  private static final String IMAGE_PNG = "image/png";

  private final RuntimeTypeAdapterFactory<Cell> cellTypeFactory;
  private final RuntimeTypeAdapterFactory<Output> outputTypeFactory;

  public JupyterUtil() {
    this.cellTypeFactory = RuntimeTypeAdapterFactory.of(Cell.class, "cell_type")
        .registerSubtype(MarkdownCell.class, "markdown").registerSubtype(CodeCell.class, "code")
        .registerSubtype(RawCell.class, "raw");
    this.outputTypeFactory = RuntimeTypeAdapterFactory.of(Output.class, "output_type")
        .registerSubtype(ExecuteResult.class, "execute_result")
        .registerSubtype(DisplayData.class, "display_data")
        .registerSubtype(Stream.class, "stream").registerSubtype(Error.class, "error");
  }

  public Nbformat getNbformat(Reader in) {
    return getNbformat(in, new GsonBuilder());
  }

  public Nbformat getNbformat(Reader in, GsonBuilder gsonBuilder) {
    return getGson(gsonBuilder).fromJson(in, Nbformat.class);
  }

  public Note getNote(Nbformat nbformat, String codeReplaced, String markdownReplaced) {
    Note note = new Note();
    AuthenticationInfo anonymous = new AuthenticationInfo("anonymous", "anonymous");

    note.setName(nbformat.getMetadata().getTitle());

    String lineSeparator = System.lineSeparator();
    Paragraph paragraph;
    String interpreterName;
    List<InterpreterResultMessage> interpreterResultMessageList;
    InterpreterResult.Type type;
    String result;

    for (Cell cell : nbformat.getCells()) {
      paragraph = note.addNewParagraph(anonymous);
      interpreterResultMessageList = new ArrayList<>();

      if (cell instanceof CodeCell) {
        interpreterName = "%" + codeReplaced;
        for (Output output : ((CodeCell) cell).getOutputs()) {
          InterpreterResultMessage interpreterResultMessage;
          if (output instanceof Stream) {
            type = Type.TEXT;
            result = Joiner.on(lineSeparator).join(((Stream) output).getText());
          } else if (output instanceof ExecuteResult) {
            ExecuteResult executeResult = (ExecuteResult) output;
            for (Map.Entry<String, Object> data : executeResult.getData().entrySet()) {
              if (TEXT_PLAIN.equals(data.getKey())) {
                type = Type.TEXT;
                result = Joiner.on(lineSeparator).join((List<String>) data.getValue());
              }
              interpreterResultMessage = new InterpreterResultMessage(type, result);
            }
          } else if (output instanceof DisplayData) {

          } else {
            // Error
            Error error = (Error) output;
            type = Type.TEXT;
            result = Joiner.on(lineSeparator)
                .join(new String[]{error.getEname(), error.getEvalue()});

          }

          interpreterResultMessage = new InterpreterResultMessage(type, result);

          interpreterResultMessageList.add(interpreterResultMessage);

        }

      } else if (cell instanceof MarkdownCell) {
        interpreterName = "%" + markdownReplaced;
      } else {
        interpreterName = "";
      }

      paragraph.setText(
          interpreterName + lineSeparator + Joiner.on(lineSeparator).join(cell.getSource()));
    }

    return note;
  }

  private Gson getGson(GsonBuilder gsonBuilder) {
    return gsonBuilder.registerTypeAdapterFactory(cellTypeFactory)
        .registerTypeAdapterFactory(outputTypeFactory).create();
  }

  public static void main(String[] args) {

  }
}
