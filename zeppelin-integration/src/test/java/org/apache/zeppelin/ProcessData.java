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

package org.apache.zeppelin;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessData {
  public enum Types_Of_Data {
    OUTPUT,
    ERROR,
    EXIT_CODE,
    STREAMS_MERGED,
    PROCESS_DATA_OBJECT
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessData.class);

  // Patterns to filter out download-related messages from error stream
  private static final Pattern[] DOWNLOAD_PATTERNS = {
    Pattern.compile(".*\\[INFO\\]\\s+(Downloading|Downloaded):.*"),     // Maven download messages
    Pattern.compile(".*Downloading\\s+.*"),                              // Generic downloading messages
    Pattern.compile(".*Downloaded\\s+.*"),                               // Generic downloaded messages
    Pattern.compile(".*progress:\\s*\\d+%.*", Pattern.CASE_INSENSITIVE), // Progress indicators
    Pattern.compile(".*\\d+/\\d+\\s*(KB|MB|GB|bytes).*"),               // Size progress (e.g., 1024/2048 KB)
    Pattern.compile(".*\\d+\\.\\d+\\s*(KB|MB|GB)\\s+(downloaded|transferred).*", Pattern.CASE_INSENSITIVE) // Size with downloaded/transferred
  };

  private Process checked_process;
  private boolean printToConsole = false;

  public ProcessData(Process connected_process, boolean printToConsole, int silenceTimeout, TimeUnit timeUnit) {
    this.checked_process = connected_process;
    this.printToConsole = printToConsole;
    this.silenceTimeout = TimeUnit.MILLISECONDS.convert(silenceTimeout, timeUnit);
  }

  public ProcessData(Process connected_process, boolean printToConsole, int silenceTimeoutSec) {
    this.checked_process = connected_process;
    this.printToConsole = printToConsole;
    this.silenceTimeout = TimeUnit.MILLISECONDS.convert(silenceTimeoutSec, TimeUnit.SECONDS);
  }

  public ProcessData(Process connected_process, boolean printToConsole) {
    this.checked_process = connected_process;
    this.printToConsole = printToConsole;
  }

  public ProcessData(Process connected_process) {
    this.checked_process = connected_process;
    this.printToConsole = true;
  }


  boolean returnCodeRetrieved = false;

  private String outPutStream = null;
  private String errorStream = null;
  private int returnCode;
  private long silenceTimeout = 10 * 60 * 1000;
  private final long unconditionalExitDelayMinutes = 30;

  public static boolean isRunning(Process process) {
    try {
      process.exitValue();
      return false;
    } catch (IllegalThreadStateException e) {
      return true;
    }
  }

  public Object getData(Types_Of_Data type) {
    //TODO get rid of Pseudo-terminal will not be allocated because stdin is not a terminal.
    switch (type) {
      case OUTPUT: {
        return this.getOutPutStream();
      }
      case ERROR: {
        return this.getErrorStream();
      }
      case EXIT_CODE: {
        return this.getExitCodeValue();
      }
      case STREAMS_MERGED: {
        return this.getOutPutStream() + "\n" + this.getErrorStream();
      }
      case PROCESS_DATA_OBJECT: {
        this.getErrorStream();
        return this;
      }
      default: {
        throw new IllegalArgumentException("Data Type " + type + " not supported yet!");
      }
    }
  }

  public int getExitCodeValue() {
    try {
      if (!returnCodeRetrieved) {
        this.checked_process.waitFor();
        this.returnCode = this.checked_process.exitValue();
        this.returnCodeRetrieved = true;
        this.checked_process.destroy();
      }
    } catch (Exception inter) {
      throw new RuntimeException("Couldn't finish waiting for process " + this.checked_process + " termination", inter);
    }
    return this.returnCode;
  }

  public String getOutPutStream() {
    if (this.outPutStream == null) {
      try {
        buildOutputAndErrorStreamData();
      } catch (Exception e) {
        throw new RuntimeException("Couldn't retrieve Output Stream data from process: " + this.checked_process.toString(), e);

      }
    }
    this.outPutStream = this.outPutStream.replace("Pseudo-terminal will not be allocated because stdin is not a terminal.", "");
    this.errorStream = this.errorStream.replace("Pseudo-terminal will not be allocated because stdin is not a terminal.", "");
    return this.outPutStream;
  }

  public String getErrorStream() {
    if (this.errorStream == null) {
      try {
        buildOutputAndErrorStreamData();
      } catch (Exception e) {
        throw new RuntimeException("Couldn't retrieve Error Stream data from process: " + this.checked_process.toString(), e);

      }
    }
    this.outPutStream = this.outPutStream.replace("Pseudo-terminal will not be allocated because stdin is not a terminal.", "");
    this.errorStream = this.errorStream.replace("Pseudo-terminal will not be allocated because stdin is not a terminal.", "");
    return this.errorStream;
  }

  /**
   * Filters out download-related messages from error stream output.
   * This method checks if the given message matches common download patterns
   * (Maven downloads, progress indicators, size information, etc.)
   *
   * @param message The message to check
   * @return true if the message should be filtered out (is a download message), false otherwise
   */
  private boolean isDownloadMessage(String message) {
    if (message == null || message.trim().isEmpty()) {
      return false;
    }

    for (Pattern pattern : DOWNLOAD_PATTERNS) {
      if (pattern.matcher(message).matches()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    result.append(String.format("[OUTPUT STREAM]\n%s\n", this.outPutStream));
    result.append(String.format("[ERROR STREAM]\n%s\n", this.errorStream));
    result.append(String.format("[EXIT CODE]\n%d", this.returnCode));
    return result.toString();
  }

  private void buildOutputAndErrorStreamData() throws IOException {
    StringBuilder sbInStream = new StringBuilder();
    StringBuilder sbErrorStream = new StringBuilder();

    try {
      InputStream in = this.checked_process.getInputStream();
      InputStream inErrors = this.checked_process.getErrorStream();
      BufferedReader inReader = new BufferedReader(new InputStreamReader(in));
      BufferedReader inReaderErrors = new BufferedReader(new InputStreamReader(inErrors));
      LOGGER.trace("Started retrieving data from streams of attached process: " + this.checked_process);

      long lastStreamDataTime = System.currentTimeMillis();   //Store start time to be able to finish method if command hangs
      long unconditionalExitTime = System.currentTimeMillis() + TimeUnit.MILLISECONDS.convert(unconditionalExitDelayMinutes, TimeUnit.MINUTES); // Stop after 'unconditionalExitDelayMinutes' even if process is alive and sending output
      final int BUFFER_LEN = 300;
      char charBuffer[] = new char[BUFFER_LEN];     //Use char buffer to read output, size can be tuned.
      boolean outputProduced = true;                //Flag to check if previous iteration produced any output
      while (isRunning(this.checked_process) || outputProduced) {   //Continue if process is alive or some output was produced on previous iteration and there may be still some data to read.
        outputProduced = false;
        ZeppelinITUtils.sleep(100, false);                                  //Some local commands can exit fast, but immediate stream reading will give no output and after iteration, 'while' condition will be false so we will not read out any output while it is still there, just need to wait for some time for it to appear in streams.

        StringBuilder tempSB = new StringBuilder();
        while (inReader.ready()) {
          tempSB.setLength(0);                                // clean temporary StringBuilder
          int readCount = inReader.read(charBuffer, 0, BUFFER_LEN); //read up to 'BUFFER_LEN' chars to buffer
          if (readCount < 1) {                                     // if nothing read or error occurred
            break;
          }
          tempSB.append(charBuffer, 0, readCount);

          sbInStream.append(tempSB);
          if (tempSB.length() > 0) {
            outputProduced = true;                                //set flag to know that we read something and there may be moire data, even if process already exited
          }

          lastStreamDataTime = System.currentTimeMillis();        //remember last time data was read from streams to be sure we are not looping infinitely
        }

        tempSB = new StringBuilder();                               //Same, but for error stream
        while (inReaderErrors.ready()) {
          tempSB.setLength(0);
          int readCount = inReaderErrors.read(charBuffer, 0, BUFFER_LEN);
          if (readCount < 1) {
            break;
          }
          tempSB.append(charBuffer, 0, readCount);
          sbErrorStream.append(tempSB);
          if (tempSB.length() > 0) {
            outputProduced = true;
            String temp = new String(tempSB);
            temp = temp.replaceAll("Pseudo-terminal will not be allocated because stdin is not a terminal.", "");
            if (printToConsole) {
              if (!temp.trim().equals("") && !isDownloadMessage(temp)) {
                if (temp.toLowerCase().contains("error") || temp.toLowerCase().contains("failed")) {
                  LOGGER.warn(temp.trim());
                } else {
                  LOGGER.debug(temp.trim());
                }
              }
            }
          }
          lastStreamDataTime = System.currentTimeMillis();
        }


        if ((System.currentTimeMillis() - lastStreamDataTime > silenceTimeout) ||     //Exit if silenceTimeout ms has passed from last stream read. Means process is alive but not sending any data.
            (System.currentTimeMillis() > unconditionalExitTime)) {                    //Exit unconditionally - guards against alive process continuously sending data.
          LOGGER.info("Conditions: " + (System.currentTimeMillis() - lastStreamDataTime > silenceTimeout) + " " +
              (System.currentTimeMillis() > unconditionalExitTime));
          this.checked_process.destroy();
          try {
            if ((System.currentTimeMillis() > unconditionalExitTime)) {
              LOGGER.error(
                  "!@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@Unconditional exit occured@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@!\nsome process hag up for more than "
                      + unconditionalExitDelayMinutes + " minutes.");
            }
            LOGGER.error("!##################################!");
            StringWriter sw = new StringWriter();
            Exception e = new Exception("Exited from buildOutputAndErrorStreamData by timeout");
            e.printStackTrace(new PrintWriter(sw)); //Get stack trace
            LOGGER.error(String.valueOf(e), e);
          } catch (Exception ignore) {
            LOGGER.info("Exception in ProcessData while buildOutputAndErrorStreamData ", ignore);
          }
          break;
        }
      }

      in.close();
      inErrors.close();
    } finally {
      this.outPutStream = sbInStream.toString();
      this.errorStream = sbErrorStream.toString();
    }
  }
}
