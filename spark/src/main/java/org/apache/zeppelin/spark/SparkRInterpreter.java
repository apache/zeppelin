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

package org.apache.zeppelin.spark;

import static org.apache.zeppelin.spark.ZeppelinRDisplay.render;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.spark.SparkRBackend;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * R and SparkR interpreter with visualization support.
 */
public class SparkRInterpreter extends Interpreter {
  private static final Logger logger = LoggerFactory.getLogger(SparkRInterpreter.class);

  private static String renderOptions;
  private ZeppelinR zeppelinR;

  static {
    Interpreter.register(
      "r",
      "spark",
      SparkRInterpreter.class.getName(),
      new InterpreterPropertyBuilder()
          .add("zeppelin.R.cmd",
              SparkInterpreter.getSystemDefault("ZEPPELIN_R_CMD", "zeppelin.R.cmd", "R"),
              "R repl path")
          .add("zeppelin.R.knitr",
              SparkInterpreter.getSystemDefault("ZEPPELIN_R_KNITR", "zeppelin.R.knitr", "true"),
              "whether use knitr or not")
          .add("zeppelin.R.image.width",
              SparkInterpreter.getSystemDefault("ZEPPELIN_R_IMAGE_WIDTH",
                  "zeppelin.R.image.width", "100%"),
              "")
          .add("zeppelin.R.render.options",
              SparkInterpreter.getSystemDefault("ZEPPELIN_R_RENDER_OPTIONS",
                  "zeppelin.R.render.options",
                  "out.format = 'html', comment = NA, "
                      + "echo = FALSE, results = 'asis', message = F, warning = F"),
              "")
          .build());
  }


  public SparkRInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    String rCmdPath = getProperty("zeppelin.R.cmd");
    String sparkRLibPath;

    if (System.getenv("SPARK_HOME") != null) {
      sparkRLibPath = System.getenv("SPARK_HOME") + "/R/lib";
    } else {
      sparkRLibPath = System.getenv("ZEPPELIN_HOME") + "/interpreter/spark/R/lib";
      // workaround to make sparkr work without SPARK_HOME
      System.setProperty("spark.test.home", System.getenv("ZEPPELIN_HOME") + "/interpreter/spark");
    }

    synchronized (SparkRBackend.backend()) {
      if (!SparkRBackend.isStarted()) {
        SparkRBackend.init();
        SparkRBackend.start();
      }
    }

    int port = SparkRBackend.port();

    SparkInterpreter sparkInterpreter = getSparkInterpreter();
    ZeppelinRContext.setSparkContext(sparkInterpreter.getSparkContext());
    ZeppelinRContext.setSqlContext(sparkInterpreter.getSQLContext());
    ZeppelinRContext.setZepplinContext(sparkInterpreter.getZeppelinContext());

    zeppelinR = new ZeppelinR(rCmdPath, sparkRLibPath, port);
    try {
      zeppelinR.open();
    } catch (IOException e) {
      logger.error("Exception while opening SparkRInterpreter", e);
      throw new InterpreterException(e);
    }

    if (useKnitr()) {
      zeppelinR.eval("library('knitr')");
    }
    renderOptions = getProperty("zeppelin.R.render.options");
  }

  @Override
  public InterpreterResult interpret(String lines, InterpreterContext interpreterContext) {

    String imageWidth = getProperty("zeppelin.R.image.width");

    String[] sl = lines.split("\n");
    if (sl[0].contains("{") && sl[0].contains("}")) {
      String jsonConfig = sl[0].substring(sl[0].indexOf("{"), sl[0].indexOf("}") + 1);
      ObjectMapper m = new ObjectMapper();
      try {
        JsonNode rootNode = m.readTree(jsonConfig);
        JsonNode imageWidthNode = rootNode.path("imageWidth");
        if (!imageWidthNode.isMissingNode()) imageWidth = imageWidthNode.textValue();
      }
      catch (Exception e) {
        logger.warn("Can not parse json config: " + jsonConfig, e);
      }
      finally {
        lines = lines.replace(jsonConfig, "");
      }
    }

    try {
      // render output with knitr
      if (useKnitr()) {
        zeppelinR.setInterpreterOutput(null);
        zeppelinR.set(".zcmd", "\n```{r " + renderOptions + "}\n" + lines + "\n```");
        zeppelinR.eval(".zres <- knit2html(text=.zcmd)");
        String html = zeppelinR.getS0(".zres");

        RDisplay rDisplay = render(html, imageWidth);

        return new InterpreterResult(
            rDisplay.code(),
            rDisplay.type(),
            rDisplay.content()
        );
      } else {
        // alternatively, stream the output (without knitr)
        zeppelinR.setInterpreterOutput(interpreterContext.out);
        zeppelinR.eval(lines);
        return new InterpreterResult(InterpreterResult.Code.SUCCESS, "");
      }
    } catch (Exception e) {
      logger.error("Exception while connecting to R", e);
      return new InterpreterResult(InterpreterResult.Code.ERROR, e.getMessage());
    } finally {
      try {
      } catch (Exception e) {
        // Do nothing...
      }
    }
  }

  @Override
  public void close() {
    zeppelinR.close();
  }

  @Override
  public void cancel(InterpreterContext context) {}

  @Override
  public FormType getFormType() {
    return FormType.NONE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetFIFOScheduler(
            SparkRInterpreter.class.getName() + this.hashCode());
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    return new ArrayList<String>();
  }

  private SparkInterpreter getSparkInterpreter() {
    LazyOpenInterpreter lazy = null;
    SparkInterpreter spark = null;
    Interpreter p = getInterpreterInTheSameSessionByClassName(SparkInterpreter.class.getName());

    while (p instanceof WrappedInterpreter) {
      if (p instanceof LazyOpenInterpreter) {
        lazy = (LazyOpenInterpreter) p;
      }
      p = ((WrappedInterpreter) p).getInnerInterpreter();
    }
    spark = (SparkInterpreter) p;

    if (lazy != null) {
      lazy.open();
    }
    return spark;
  }

  private boolean useKnitr() {
    try {
      return Boolean.parseBoolean(getProperty("zeppelin.R.knitr"));
    } catch (Exception e) {
      return false;
    }
  }

}
