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
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.interpreter.util.InterpreterOutputStream;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * R and SparkR interpreter with visualization support.
 */
public class SparkRInterpreter extends Interpreter {
  private static final Logger logger = LoggerFactory.getLogger(SparkRInterpreter.class);

  private String renderOptions;
  private SparkInterpreter sparkInterpreter;
  private boolean isSpark2;
  private ZeppelinR zeppelinR;
  private AtomicBoolean rbackendDead = new AtomicBoolean(false);
  private SparkContext sc;
  private JavaSparkContext jsc;
  private String secret;

  public SparkRInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() throws InterpreterException {
    String rCmdPath = getProperty("zeppelin.R.cmd", "R");
    String sparkRLibPath;

    if (System.getenv("SPARK_HOME") != null) {
      // local or yarn-client mode when SPARK_HOME is specified
      sparkRLibPath = System.getenv("SPARK_HOME") + "/R/lib";
    } else if (System.getenv("ZEPPELIN_HOME") != null){
      // embedded mode when SPARK_HOME is not specified
      sparkRLibPath = System.getenv("ZEPPELIN_HOME") + "/interpreter/spark/R/lib";
      // workaround to make sparkr work without SPARK_HOME
      System.setProperty("spark.test.home", System.getenv("ZEPPELIN_HOME") + "/interpreter/spark");
    } else {
      // yarn-cluster mode
      sparkRLibPath = "sparkr";
    }

    this.sparkInterpreter = getSparkInterpreter();
    this.sc = sparkInterpreter.getSparkContext();
    this.jsc = sparkInterpreter.getJavaSparkContext();

    // Share the same SparkRBackend across sessions
    SparkVersion sparkVersion = new SparkVersion(sc.version());
    synchronized (SparkRBackend.backend()) {
      if (!SparkRBackend.isStarted()) {
        SparkRBackend.init(sparkVersion);
        SparkRBackend.start();
      }
    }
    this.isSpark2 = sparkVersion.newerThanEquals(SparkVersion.SPARK_2_0_0);
    int timeout = this.sc.getConf().getInt("spark.r.backendConnectionTimeout", 6000);

    ZeppelinRContext.setSparkContext(sc);
    ZeppelinRContext.setJavaSparkContext(jsc);
    if (isSpark2) {
      ZeppelinRContext.setSparkSession(sparkInterpreter.getSparkSession());
    }
    ZeppelinRContext.setSqlContext(sparkInterpreter.getSQLContext());
    ZeppelinRContext.setZeppelinContext(sparkInterpreter.getZeppelinContext());

    zeppelinR =
        new ZeppelinR(rCmdPath, sparkRLibPath, SparkRBackend.port(), sparkVersion, timeout, this);
    try {
      zeppelinR.open();
    } catch (IOException e) {
      throw new InterpreterException("Exception while opening SparkRInterpreter", e);
    }

    if (useKnitr()) {
      zeppelinR.eval("library('knitr')");
    }
    renderOptions = getProperty("zeppelin.R.render.options",
        "out.format = 'html', comment = NA, echo = FALSE, results = 'asis', message = F, " +
            "warning = F, fig.retina = 2");
  }

  @Override
  public InterpreterResult interpret(String lines, InterpreterContext interpreterContext)
      throws InterpreterException {

    sparkInterpreter.populateSparkWebUrl(interpreterContext);
    String jobGroup = Utils.buildJobGroupId(interpreterContext);
    String jobDesc = "Started by: " +
       Utils.getUserName(interpreterContext.getAuthenticationInfo());
    sparkInterpreter.getSparkContext().setJobGroup(jobGroup, jobDesc, false);

    String imageWidth = getProperty("zeppelin.R.image.width", "100%");

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

    String setJobGroup = "";
    // assign setJobGroup to dummy__, otherwise it would print NULL for this statement
    if (isSpark2) {
      setJobGroup = "dummy__ <- setJobGroup(\"" + jobGroup +
          "\", \" +" + jobDesc + "\", TRUE)";
    } else if (getSparkInterpreter().getSparkVersion().newerThanEquals(SparkVersion.SPARK_1_5_0)) {
      setJobGroup = "dummy__ <- setJobGroup(sc, \"" + jobGroup +
          "\", \"" + jobDesc + "\", TRUE)";
    }
    lines = setJobGroup + "\n" + lines;

    try {
      // render output with knitr
      if (rbackendDead.get()) {
        return new InterpreterResult(InterpreterResult.Code.ERROR,
            "sparkR backend is dead, please try to increase spark.r.backendConnectionTimeout");
      }
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
    }
  }

  @Override
  public void close() {
    zeppelinR.close();
  }

  @Override
  public void cancel(InterpreterContext context) {
    if (this.sc != null) {
      sc.cancelJobGroup(Utils.buildJobGroupId(context));
    }
  }

  @Override
  public FormType getFormType() {
    return FormType.NONE;
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
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
  public List<InterpreterCompletion> completion(String buf, int cursor,
                                                InterpreterContext interpreterContext) {
    return new ArrayList<>();
  }

  private SparkInterpreter getSparkInterpreter() throws InterpreterException {
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
    return Boolean.parseBoolean(getProperty("zeppelin.R.knitr", "true"));
  }

  public AtomicBoolean getRbackendDead() {
    return rbackendDead;
  }
}
