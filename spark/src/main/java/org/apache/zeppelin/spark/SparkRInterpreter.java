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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Base64;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * R and SparkR interpreter with visualization support.
 */
public class SparkRInterpreter extends Interpreter {
  private static final Logger logger = LoggerFactory.getLogger(SparkRInterpreter.class);

  private static String renderOptions;

  static {
    Interpreter.register(
      "r",
      "spark",
      SparkRInterpreter.class.getName(),
      new InterpreterPropertyBuilder()
              .add("spark.master",
                      SparkInterpreter.getSystemDefault("MASTER", "spark.master", "local[*]"),
                      "Spark master uri. ex) spark://masterhost:7077")
              .add("spark.home",
                      SparkInterpreter.getSystemDefault("SPARK_HOME", "spark.home", "/opt/spark"),
                      "Spark distribution location")
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
    zeppelinR().open(getProperty("spark.master"),
            "/opt/spark", getSparkInterpreter());
    renderOptions = getProperty("zeppelin.R.render.options");
  }

  @Override
  public InterpreterResult interpret(String lines, InterpreterContext contextInterpreter) {

    String imageWidth = getProperty("zeppelin.R.image.width");

    String[] sl = lines.split("\n");
    if (sl[0].contains("{") && sl[0].contains("}")) {
      String jsonConfig = sl[0].substring(sl[0].indexOf("{"), sl[0].indexOf("}") + 1);
      ObjectMapper m = new ObjectMapper();
      try {
        JsonNode rootNode = m.readTree(jsonConfig);
        JsonNode imageWidthNode = rootNode.path("imageWidth");
        if (! imageWidthNode.isMissingNode()) imageWidth = imageWidthNode.textValue();
      }
      catch (Exception e) {
        logger.warn("Can not parse json config: " + jsonConfig, e);
      }
      finally {
        lines = lines.replace(jsonConfig, "");
      }
    }

    try {

      zeppelinR().set(".zcmd", "\n```{r " + renderOptions + "}\n" + lines + "\n```");
      zeppelinR().eval(".zres <- knit2html(text=.zcmd)");
      String html = zeppelinR().getS0(".zres");

      html = format(html, imageWidth);

      return new InterpreterResult(
              InterpreterResult.Code.SUCCESS,
              InterpreterResult.Type.HTML,
              html);

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

  /*
   * Ensure we return proper HTML to be displayed in the Zeppelin UI.
   */
  private String format(String html, String imageWidth) {

    Document document = Jsoup.parse(html);

    Element body = document.getElementsByTag("body").get(0);
    Elements images = body.getElementsByTag("img");
    Elements scripts = body.getElementsByTag("script");
    Elements paragraphs = body.getElementsByTag("p");

    if ((paragraphs.size() == 1)
            && (images.size() == 0)
            && (scripts.size() == 0)
            ) {

      // We are here with a pure text output, let's keep format intact...

      return html.substring(
              html.indexOf("<body>") + 6,
              html.indexOf("</body>")
      )
              .replace("<p>", "<pre style='background-color: white; border: 0px;'>")
              .replace("</p>", "</pre>");

    }

    // OK, we have more than text...

    for (Element image : images) {
      image.attr("width", imageWidth);
    }

    return body.html()
            .replaceAll("src=\"//", "src=\"http://")
            .replaceAll("href=\"//", "href=\"http://");
  }

  @Override
  public void close() {
    zeppelinR().close();
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
    for (Interpreter intp : getInterpreterGroup()) {
      if (intp.getClassName().equals(SparkInterpreter.class.getName())) {
        Interpreter p = intp;
        while (p instanceof WrappedInterpreter) {
          if (p instanceof LazyOpenInterpreter) {
            p.open();
          }
          p = ((WrappedInterpreter) p).getInnerInterpreter();
        }
        return (SparkInterpreter) p;
      }
    }
    return null;
  }

  protected static ZeppelinRFactory zeppelinR() {
    return ZeppelinRFactory.instance();
  }

  /**
   * Java Factory to support tests with Mockito.
   *
   * (Mockito can not mock the zeppelinR final scala object class).
   */
  protected static class ZeppelinRFactory {
    private static ZeppelinRFactory instance;
    private static ZeppelinR zeppelinR;
    private ZeppelinRFactory() {
      // Singleton
    }

    protected static synchronized ZeppelinRFactory instance() {
      if (instance == null) instance = new ZeppelinRFactory();
      return instance;
    }
    protected void open(String master, String sparkHome, SparkInterpreter sparkInterpreter) {
      zeppelinR.open(master, sparkHome, sparkInterpreter);
    }
    protected Object eval(String command) {
      return zeppelinR.eval(command);
    }
    protected void set(String key, Object value) {
      zeppelinR.set(key, value);
    }
    protected Object get(String key) {
      return zeppelinR.get(key);
    }
    protected String getS0(String key) {
      return zeppelinR.getS0(key);
    }
    protected void close() {
      zeppelinR.close();
    }
  }

}
