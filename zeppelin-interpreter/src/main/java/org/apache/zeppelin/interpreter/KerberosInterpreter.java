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

package org.apache.zeppelin.interpreter;

import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.zeppelin.annotation.ZeppelinApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interpreter wrapper for Kerberos initialization
 *
 * runKerberosLogin() method you need to implement that determine Zeppelin's behavior.
 * startKerberosLoginThread() needs to be called inside the open() and
 * shutdownExecutorService() inside close().
 */
public abstract class KerberosInterpreter extends Interpreter {

  private Integer kinitFailCount = 0;
  protected ScheduledExecutorService scheduledExecutorService;
  public static Logger logger = LoggerFactory.getLogger(KerberosInterpreter.class);

  public KerberosInterpreter(Properties property) {
    super(property);
  }

  @ZeppelinApi
  protected abstract boolean runKerberosLogin();

  public String getKerberosRefreshInterval() {
    if (System.getenv("KERBEROS_REFRESH_INTERVAL") == null) {
      return "1d";
    } else {
      return System.getenv("KERBEROS_REFRESH_INTERVAL");
    }
  }

  public Integer kinitFailThreshold() {
    if (System.getenv("KINIT_FAIL_THRESHOLD") == null) {
      return 5;
    } else {
      return new Integer(System.getenv("KINIT_FAIL_THRESHOLD"));
    }
  }

  public Long getTimeAsMs(String time) {
    if (time == null) {
      logger.error("Cannot convert to time value.", time);
      time = "1d";
    }

    Matcher m = Pattern.compile("(-?[0-9]+)([a-z]+)?").matcher(time.toLowerCase());
    if (!m.matches()) {
      throw new IllegalArgumentException("Invalid time string: " + time);
    }

    long val = Long.parseLong(m.group(1));
    String suffix = m.group(2);

    if (suffix != null && !Constants.TIME_SUFFIXES.containsKey(suffix)) {
      throw new IllegalArgumentException("Invalid suffix: \"" + suffix + "\"");
    }

    return TimeUnit.MILLISECONDS.convert(val,
        suffix != null ? Constants.TIME_SUFFIXES.get(suffix) : TimeUnit.MILLISECONDS);
  }

  protected ScheduledExecutorService startKerberosLoginThread() {
    scheduledExecutorService = Executors.newScheduledThreadPool(1);

    scheduledExecutorService.schedule(new Callable() {
      public Object call() throws Exception {

        if (runKerberosLogin()) {
          logger.info("Ran runKerberosLogin command successfully.");
          kinitFailCount = 0;
          // schedule another kinit run with a fixed delay.
          scheduledExecutorService
              .schedule(this, getTimeAsMs(getKerberosRefreshInterval()), TimeUnit.MILLISECONDS);
        } else {
          kinitFailCount++;
          logger.info("runKerberosLogin failed for " + kinitFailCount + " time(s).");
          // schedule another retry at once or close the interpreter if too many times kinit fails
          if (kinitFailCount >= kinitFailThreshold()) {
            logger.error("runKerberosLogin failed for  max attempts, calling close interpreter.");
            close();
          } else {
            scheduledExecutorService.submit(this);
          }
        }
        return null;
      }
    }, getTimeAsMs(getKerberosRefreshInterval()), TimeUnit.MILLISECONDS);

    return scheduledExecutorService;
  }

  protected void shutdownExecutorService() {
    if (scheduledExecutorService != null) {
      scheduledExecutorService.shutdown();
    }
  }

}
