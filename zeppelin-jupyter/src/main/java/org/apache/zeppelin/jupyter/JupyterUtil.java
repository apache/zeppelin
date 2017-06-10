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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.base.Joiner;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import org.apache.zeppelin.jupyter.nbformat.Cell;
import org.apache.zeppelin.jupyter.nbformat.CodeCell;
import org.apache.zeppelin.jupyter.nbformat.DisplayData;
import org.apache.zeppelin.jupyter.nbformat.Error;
import org.apache.zeppelin.jupyter.nbformat.ExecuteResult;
import org.apache.zeppelin.jupyter.nbformat.HeadingCell;
import org.apache.zeppelin.jupyter.nbformat.MarkdownCell;
import org.apache.zeppelin.jupyter.nbformat.Nbformat;
import org.apache.zeppelin.jupyter.nbformat.Output;
import org.apache.zeppelin.jupyter.nbformat.RawCell;
import org.apache.zeppelin.jupyter.nbformat.Stream;
import org.apache.zeppelin.jupyter.zformat.Note;
import org.apache.zeppelin.jupyter.zformat.Paragraph;
import org.apache.zeppelin.jupyter.zformat.Result;
import org.apache.zeppelin.jupyter.zformat.TypeData;

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
        .registerSubtype(RawCell.class, "raw").registerSubtype(HeadingCell.class, "heading");
    this.outputTypeFactory = RuntimeTypeAdapterFactory.of(Output.class, "output_type")
        .registerSubtype(ExecuteResult.class, "execute_result")
        .registerSubtype(DisplayData.class, "display_data").registerSubtype(Stream.class, "stream")
        .registerSubtype(Error.class, "error");
  }

  public Nbformat getNbformat(Reader in) {
    return getNbformat(in, new GsonBuilder());
  }

  public Nbformat getNbformat(Reader in, GsonBuilder gsonBuilder) {
    return getGson(gsonBuilder).fromJson(in, Nbformat.class);
  }

  public Note getNote(Reader in, String codeReplaced, String markdownReplaced) {
    return getNote(in, new GsonBuilder(), codeReplaced, markdownReplaced);
  }

  public Note getNote(Reader in, GsonBuilder gsonBuilder, String codeReplaced,
      String markdownReplaced) {
    return getNote(getNbformat(in, gsonBuilder), codeReplaced, markdownReplaced);
  }

  public Note getNote(Nbformat nbformat, String codeReplaced, String markdownReplaced) {
    Note note = new Note();

    String name = nbformat.getMetadata().getTitle();
    if (null == name) {
      name = "Note converted from Jupyter";
    }
    note.setName(name);

    String lineSeparator = System.lineSeparator();
    Paragraph paragraph;
    List<Paragraph> paragraphs = new ArrayList<>();
    String interpreterName;
    List<TypeData> typeDataList;
    String type;
    String result;

    for (Cell cell : nbformat.getCells()) {
      paragraph = new Paragraph();
      typeDataList = new ArrayList<>();

      if (cell instanceof CodeCell) {
        interpreterName = codeReplaced;
        for (Output output : ((CodeCell) cell).getOutputs()) {
          TypeData typeData;
          if (output instanceof Stream) {
            type = TypeData.TEXT;
            result = Joiner.on(lineSeparator).join(((Stream) output).getText());
            typeData = new TypeData(type, result);
            typeDataList.add(typeData);
          } else if (output instanceof ExecuteResult || output instanceof DisplayData) {
            Map<String, Object> data = (output instanceof ExecuteResult) ?
                ((ExecuteResult) output).getData() :
                ((DisplayData) output).getData();
            for (Map.Entry<String, Object> datum : data.entrySet()) {
              if (TEXT_PLAIN.equals(datum.getKey())) {
                type = TypeData.TEXT;
                result = Joiner.on(lineSeparator).join((List<String>) datum.getValue());
              } else if (IMAGE_PNG.equals(datum.getKey())) {
                type = TypeData.HTML;
                result = makeHTML(((String) datum.getValue()).replace("\n", ""));
              } else {
                type = TypeData.TEXT;
                result = datum.getValue().toString();
              }
              typeData = new TypeData(type, result);
              typeDataList.add(typeData);
            }
          } else {
            // Error
            Error error = (Error) output;
            type = TypeData.TEXT;
            result =
                Joiner.on(lineSeparator).join(new String[] {error.getEname(), error.getEvalue()});
            typeData = new TypeData(type, result);
            typeDataList.add(typeData);
          }
        }
      } else if (cell instanceof MarkdownCell || cell instanceof HeadingCell) {
        interpreterName = markdownReplaced;
      } else {
        interpreterName = "";
      }

      paragraph.setText(
          interpreterName + lineSeparator + Joiner.on(lineSeparator).join(cell.getSource()));
      paragraph.setResults(new Result(Result.SUCCESS, typeDataList));

      paragraphs.add(paragraph);
    }

    note.setParagraphs(paragraphs);

    return note;
  }

  private Gson getGson(GsonBuilder gsonBuilder) {
    return gsonBuilder.registerTypeAdapterFactory(cellTypeFactory)
        .registerTypeAdapterFactory(outputTypeFactory).create();
  }

  private String makeHTML(String image) {
    return "<div style='width:auto;height:auto'><img src=data:image/png;base64," + image
        + " style='width=auto;height:auto'/></div>";
  }

  public static void main(String[] args) throws ParseException, IOException {
    Options options = new Options();
    options.addOption("i", true, "Jupyter notebook file");
    options.addOption("o", true, "Zeppelin note file. Default: note.json");

    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = parser.parse(options, args);

    if (!cmd.hasOption("i")) {
      new HelpFormatter().printHelp("java " + JupyterUtil.class.getName(), options);
      System.exit(1);
    }

    Path jupyterPath = Paths.get(cmd.getOptionValue("i"));
    Path zeppelinPath = Paths.get(cmd.hasOption("o") ? cmd.getOptionValue("o") : "note.json");

    try (BufferedReader in = new BufferedReader(new FileReader(jupyterPath.toFile()));
        FileWriter fw = new FileWriter(zeppelinPath.toFile())) {
      Note note = new JupyterUtil().getNote(in, "python", "md");
      Gson gson = new GsonBuilder().setPrettyPrinting().create();
      gson.toJson(note, fw);
    }
  }
}
