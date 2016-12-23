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
import org.apache.spark.SparkContext;
import org.apache.spark.SparkRBackend;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
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
  private SparkInterpreter sparkInterpreter;
  private ZeppelinR zeppelinR;
  private SparkContext sc;

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

    this.sparkInterpreter = getSparkInterpreter();
    this.sc = sparkInterpreter.getSparkContext();
    SparkVersion sparkVersion = new SparkVersion(sc.version());
    ZeppelinRContext.setSparkContext(sc);
    if (Utils.isSpark2()) {
      ZeppelinRContext.setSparkSession(sparkInterpreter.getSparkSession());
    }
    ZeppelinRContext.setSqlContext(sparkInterpreter.getSQLContext());
    ZeppelinRContext.setZeppelinContext(sparkInterpreter.getZeppelinContext());

    zeppelinR = new ZeppelinR(rCmdPath, sparkRLibPath, port, sparkVersion);
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

  String getJobGroup(InterpreterContext context){
    return "zeppelin-" + context.getParagraphId();
  }

  @Override
  public InterpreterResult interpret(String lines, InterpreterContext interpreterContext) {

    getSparkInterpreter().populateSparkWebUrl(interpreterContext);
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

    String jobGroup = getJobGroup(interpreterContext);
    String setJobGroup = "";
    // assign setJobGroup to dummy__, otherwise it would print NULL for this statement
    if (Utils.isSpark2()) {
      setJobGroup = "dummy__ <- setJobGroup(\"" + jobGroup +
          "\", \"zeppelin sparkR job group description\", TRUE)";
    } else if (getSparkInterpreter().getSparkVersion().newerThanEquals(SparkVersion.SPARK_1_5_0)) {
      setJobGroup = "dummy__ <- setJobGroup(sc, \"" + jobGroup +
          "\", \"zeppelin sparkR job group description\", TRUE)";
    }
    logger.debug("set JobGroup:" + setJobGroup);
    lines = setJobGroup + "\n" + lines;

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
  public void cancel(InterpreterContext context) {
    if (this.sc != null) {
      sc.cancelJobGroup(getJobGroup(context));
    }
  }

  @Override
  public FormType getFormType() {
    return FormType.NONE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    if (sparkInterpreter != null) {
      return sparkInterpreter.getProgress(context);
    } else {
      return 0;
    }
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetFIFOScheduler(
            SparkRInterpreter.class.getName() + this.hashCode());
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor) {
    return new ArrayList<>();
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
