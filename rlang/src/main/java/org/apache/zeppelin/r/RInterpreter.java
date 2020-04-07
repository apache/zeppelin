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

package org.apache.zeppelin.r;

import org.apache.zeppelin.interpreter.AbstractInterpreter;
import org.apache.zeppelin.interpreter.ZeppelinContext;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * R interpreter with visualization support.
 */
public class RInterpreter extends AbstractInterpreter {
  private static final Logger LOGGER = LoggerFactory.getLogger(RInterpreter.class);
  private static RZeppelinContext z;

  private SparkRBackend sparkRBackend;
  private ZeppelinR zeppelinR;
  private String renderOptions;
  private boolean useKnitr;
  private AtomicBoolean rbackendDead = new AtomicBoolean(false);

  public RInterpreter(Properties property) {
    super(property);
  }

  /**
   * RInterpreter just use spark-core for the communication between R process and jvm process.
   * SparkContext is not created in this RInterpreter.
   * Sub class can override this, e.g. SparkRInterpreter
   * @return
   */
  protected boolean isSparkSupported() {
    return false;
  }

  /**
   * The spark version specified in pom.xml
   * Sub class can override this, e.g. SparkRInterpreter
   * @return
   */
  protected int sparkVersion() {
    return 20403;
  }

  /**
   * Spark 2.4.3 need secret for socket communication between R process and jvm process.
   * Sub class can override this, e.g. SparkRInterpreter
   * @return
   */
  protected boolean isSecretSupported() {
    return true;
  }

  @Override
  public void open() throws InterpreterException {
    this.sparkRBackend = SparkRBackend.get();
    // Share the same SparkRBackend across sessions
    synchronized (sparkRBackend) {
      if (!sparkRBackend.isStarted()) {
        try {
          sparkRBackend.init(isSecretSupported());
        } catch (Exception e) {
          throw new InterpreterException("Fail to init SparkRBackend", e);
        }
        sparkRBackend.start();
      }
    }

    synchronized (RInterpreter.class) {
      if (this.z == null) {
        z = new RZeppelinContext(getInterpreterGroup().getInterpreterHookRegistry(),
                Integer.parseInt(getProperty("zeppelin.r.maxResult", "1000")));
      }
    }
    this.renderOptions = getProperty("zeppelin.R.render.options",
            "out.format = 'html', comment = NA, echo = FALSE, results = 'asis', message = F, " +
                    "warning = F, fig.retina = 2");
    this.useKnitr = Boolean.parseBoolean(getProperty("zeppelin.R.knitr", "true"));
    zeppelinR = new ZeppelinR(this);
    try {
      zeppelinR.open();
      LOGGER.info("ZeppelinR is opened successfully.");
    } catch (IOException e) {
      throw new InterpreterException("Exception while opening RInterpreter", e);
    }

    if (useKnitr) {
      zeppelinR.eval("library('knitr')");
    }
  }

  @Override
  public InterpreterResult internalInterpret(String lines, InterpreterContext interpreterContext)
      throws InterpreterException {

    String imageWidth = getProperty("zeppelin.R.image.width", "100%");
    // paragraph local propery 'imageWidth' can override this
    if (interpreterContext.getLocalProperties().containsKey("imageWidth")) {
      imageWidth = interpreterContext.getLocalProperties().get("imageWidth");
    }
    try {
      // render output with knitr
      if (rbackendDead.get()) {
        return new InterpreterResult(InterpreterResult.Code.ERROR,
            "sparkR backend is dead, please try to increase spark.r.backendConnectionTimeout");
      }
      if (useKnitr) {
        zeppelinR.setInterpreterOutput(null);
        zeppelinR.set(".zcmd", "\n```{r " + renderOptions + "}\n" + lines + "\n```");
        zeppelinR.eval(".zres <- knit2html(text=.zcmd)");
        String html = zeppelinR.getS0(".zres");
        RDisplay rDisplay = ZeppelinRDisplay.render(html, imageWidth);
        return new InterpreterResult(
            rDisplay.getCode(),
            rDisplay.getTyp(),
            rDisplay.getContent()
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
  public void close() throws InterpreterException {
    if (this.zeppelinR != null) {
      zeppelinR.close();
    }
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {

  }

  @Override
  public FormType getFormType() {
    return FormType.NATIVE;
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return 0;
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetFIFOScheduler(
            RInterpreter.class.getName() + this.hashCode());
  }

  @Override
  public ZeppelinContext getZeppelinContext() {
    return this.z;
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor,
                                                InterpreterContext interpreterContext) {
    return new ArrayList<>();
  }

  public AtomicBoolean getRbackendDead() {
    return rbackendDead;
  }

  public static RZeppelinContext getRZeppelinContext() {
    return z;
  }
}
