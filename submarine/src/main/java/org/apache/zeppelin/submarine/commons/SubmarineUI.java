/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.submarine.commons;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hubspot.jinjava.Jinjava;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessageOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.apache.zeppelin.submarine.commons.SubmarineUtils.unifyKey;

public class SubmarineUI {
  private Logger LOGGER = LoggerFactory.getLogger(SubmarineUI.class);

  InterpreterContext intpContext;

  private static final String SUBMARINE_DASHBOARD_JINJA
      = "ui_templates/submarine-dashboard.jinja";
  //private static final String SUBMARINE_COMMAND_RUN_JINJA
  //    = "ui_templates/submarine-command-run.jinja";
  private static final String SUBMARINE_USAGE_JINJA
      = "ui_templates/submarine-usage.jinja";
  private static final String SUBMARINE_COMMAND_OPTIONS_JSON
      = "ui_templates/submarine-command-options.json";
  private static final String SUBMARINE_LOG_HEAD_JINJA
      = "ui_templates/submarine-log-head.jinja";

  public SubmarineUI(InterpreterContext context) {
    intpContext = context;

    createUILayer();
  }

  // context.out::List<InterpreterResultMessageOutput> resultMessageOutputs
  // resultMessageOutputs[0] is UI
  // resultMessageOutputs[1] is Execution information
  // resultMessageOutputs[2] is Execution LOG
  public void createUILayer() {
    try {
      // The first one is the UI interface.
      intpContext.out.setType(InterpreterResult.Type.ANGULAR);
      // The second is Execution information.
      intpContext.out.setType(InterpreterResult.Type.ANGULAR);
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  public void createSubmarineUI(SubmarineCommand submarineCmd) {
    try {
      HashMap<String, Object> mapParams = new HashMap();
      mapParams.put(unifyKey(SubmarineConstants.PARAGRAPH_ID), intpContext.getParagraphId());
      mapParams.put(unifyKey(SubmarineConstants.COMMAND_TYPE), submarineCmd.getCommand());

      String templateName = "";
      switch (submarineCmd) {
        case USAGE:
          templateName = SUBMARINE_USAGE_JINJA;
          List<CommandlineOption> commandlineOptions = getCommandlineOptions();
          mapParams.put(SubmarineConstants.COMMANDLINE_OPTIONS, commandlineOptions);
          break;
        default:
          templateName = SUBMARINE_DASHBOARD_JINJA;
          break;
      }

      URL urlTemplate = Resources.getResource(templateName);
      String template = Resources.toString(urlTemplate, Charsets.UTF_8);
      Jinjava jinjava = new Jinjava();
      String submarineUsage = jinjava.render(template, mapParams);

      // UI
      InterpreterResultMessageOutput outputUI = intpContext.out.getOutputAt(0);
      outputUI.clear();
      outputUI.write(submarineUsage);
      outputUI.flush();

      // UI update, log needs to be cleaned at the same time
      InterpreterResultMessageOutput outputLOG = intpContext.out.getOutputAt(1);
      outputLOG.clear();
      outputLOG.flush();
    } catch (IOException e) {
      LOGGER.error("Can't print usage", e);
    }
  }

  public List<CommandlineOption> getCommandlineOptions() throws IOException {
    List<CommandlineOption> commandlineOptions = new ArrayList<>();

    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(
        this.getClass().getResourceAsStream("/" + SUBMARINE_COMMAND_OPTIONS_JSON)));

    String line;
    StringBuffer strbuf = new StringBuffer();
    int licensedLineCount = 14;
    while ((line = bufferedReader.readLine()) != null) {
      if (licensedLineCount-- > 0) {
        continue;
      }
      strbuf.append(line);
    }
    Gson gson = new Gson();
    commandlineOptions = gson.fromJson(strbuf.toString(),
        new TypeToken<List<CommandlineOption>>() {
        }.getType());

    return commandlineOptions;
  }

  public void createUsageUI() {
    try {
      List<CommandlineOption> commandlineOptions = getCommandlineOptions();
      HashMap<String, Object> mapParams = new HashMap();
      mapParams.put(unifyKey(SubmarineConstants.PARAGRAPH_ID), intpContext.getParagraphId());
      mapParams.put(SubmarineConstants.COMMANDLINE_OPTIONS, commandlineOptions);

      URL urlTemplate = Resources.getResource(SUBMARINE_USAGE_JINJA);
      String template = Resources.toString(urlTemplate, Charsets.UTF_8);
      Jinjava jinjava = new Jinjava();
      String submarineUsage = jinjava.render(template, mapParams);

      InterpreterResultMessageOutput outputUI = intpContext.out.getOutputAt(0);
      outputUI.clear();
      outputUI.write(submarineUsage);
      outputUI.flush();

      // UI update, log needs to be cleaned at the same time
      InterpreterResultMessageOutput outputLOG = intpContext.out.getOutputAt(1);
      outputLOG.clear();
      outputLOG.flush();
    } catch (IOException e) {
      LOGGER.error("Can't print usage", e);
    }
  }

  public void createLogHeadUI() {
    try {
      HashMap<String, Object> mapParams = new HashMap();
      URL urlTemplate = Resources.getResource(SUBMARINE_LOG_HEAD_JINJA);
      String template = Resources.toString(urlTemplate, Charsets.UTF_8);
      Jinjava jinjava = new Jinjava();
      String submarineUsage = jinjava.render(template, mapParams);

      InterpreterResultMessageOutput outputUI = intpContext.out.getOutputAt(1);
      outputUI.clear();
      outputUI.write(submarineUsage);
      outputUI.flush();
    } catch (IOException e) {
      LOGGER.error("Can't print usage", e);
    }
  }

  public void printCommnadUI(SubmarineCommand submarineCmd) {
    try {
      HashMap<String, Object> mapParams = new HashMap();
      mapParams.put(unifyKey(SubmarineConstants.PARAGRAPH_ID), intpContext.getParagraphId());

      URL urlTemplate = Resources.getResource(SUBMARINE_DASHBOARD_JINJA);
      String template = Resources.toString(urlTemplate, Charsets.UTF_8);
      Jinjava jinjava = new Jinjava();
      String submarineUsage = jinjava.render(template, mapParams);

      InterpreterResultMessageOutput outputUI = intpContext.out.getOutputAt(0);
      outputUI.clear();
      outputUI.write(submarineUsage);
      outputUI.flush();

      // UI update, log needs to be cleaned at the same time
      InterpreterResultMessageOutput outputLOG = intpContext.out.getOutputAt(1);
      outputLOG.clear();
      outputLOG.flush();
    } catch (IOException e) {
      LOGGER.error("Can't print usage", e);
    }
  }

  public void outputLog(String title, String message) {
    try {
      StringBuffer formatMsg = new StringBuffer();
      InterpreterResultMessageOutput output = null;
      output = intpContext.out.getOutputAt(1);
      formatMsg.append("<div style=\"width:100%\">");
      if (!StringUtils.isEmpty(title)) {
        formatMsg.append(title);
      }
      formatMsg.append("<pre style=\"max-height:120px\">");
      formatMsg.append(message);
      formatMsg.append("</pre>");
      formatMsg.append("</div>\n");
      output.write(formatMsg.toString());
      output.flush();
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  /**
   * submarine Commandline Option
   */
  class CommandlineOption {
    private String name;
    private String description;

    CommandlineOption(String name, String description) {
      this.name = name;
      this.description = description;
    }

    public String getName() {
      return name;
    }

    public String getDescription() {
      return description;
    }
  }
}
