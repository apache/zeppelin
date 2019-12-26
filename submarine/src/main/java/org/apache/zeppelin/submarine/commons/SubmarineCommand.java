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

public enum SubmarineCommand {
  DASHBOARD("DASHBOARD"),
  USAGE("USAGE"),
  JOB_RUN("JOB_RUN"),
  JOB_SHOW("JOB_SHOW"),
  JOB_LIST("JOB_LIST"),
  JOB_STOP("JOB_STOP"),
  CLEAN_RUNTIME_CACHE("CLEAN_RUNTIME_CACHE"),
  OLD_UI("OLD_UI"),
  TENSORBOARD_RUN("TENSORBOARD_RUN"),
  TENSORBOARD_STOP("TENSORBOARD_STOP"),
  UNKNOWN("Unknown");

  private String command;

  SubmarineCommand(String command){
    this.command = command;
  }

  public String getCommand(){
    return command;
  }

  public static SubmarineCommand fromCommand(String command) {
    for (SubmarineCommand type : SubmarineCommand.values()) {
      if (type.getCommand().equals(command)) {
        return type;
      }
    }

    return UNKNOWN;
  }
}
