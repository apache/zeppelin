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

package org.apache.zeppelin.submarine.job;

public enum SubmarineJobStatus {
  UNKNOWN("UNKNOWN"),
  READY("READY"),
  EXECUTE_SUBMARINE("EXECUTE SUBMARINE"),
  EXECUTE_SUBMARINE_ERROR("EXECUTE SUBMARINE ERROR"),
  EXECUTE_SUBMARINE_FINISHED("EXECUTE SUBMARINE FINISHED"),
  YARN_NEW("YARN NEW"),
  YARN_NEW_SAVING("YARN NEW SAVING"),
  YARN_SUBMITTED("YARN SUBMITTED"),
  YARN_ACCEPTED("YARN ACCEPTED"),
  YARN_RUNNING("YARN RUNNING"),
  YARN_FINISHED("YARN FINISHED"),
  YARN_FAILED("YARN FAILED"),
  YARN_STOPPED("YARN STOPPED"),
  YARN_KILLED("YARN KILLED");

  private String status;

  SubmarineJobStatus(String status){
    this.status = status;
  }

  public String getStatus(){
    return status;
  }

  public static SubmarineJobStatus fromState(String status) {
    for (SubmarineJobStatus noteStatus : SubmarineJobStatus.values()) {
      if (noteStatus.getStatus().equals(status)) {
        return noteStatus;
      }
    }

    return EXECUTE_SUBMARINE_ERROR;
  }
}
