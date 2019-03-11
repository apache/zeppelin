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

package org.apache.zeppelin.submarine;

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.submarine.job.SubmarineJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

public class SubmarineContext {
  private Logger LOGGER = LoggerFactory.getLogger(SubmarineContext.class);

  private static SubmarineContext instance = null;

  // noteId -> SubmarineJob
  private Map<String, SubmarineJob> mapSubmarineJob = new HashMap<>();

  public static SubmarineContext getInstance() {
    synchronized (SubmarineContext.class) {
      if (instance == null) {
        instance = new SubmarineContext();
      }
      return instance;
    }
  }

  public SubmarineJob addOrGetSubmarineJob(Properties properties, InterpreterContext context) {
    SubmarineJob submarineJob = null;
    String noteId = context.getNoteId();
    if (!mapSubmarineJob.containsKey(noteId)) {
      submarineJob = new SubmarineJob(context, properties);
      mapSubmarineJob.put(noteId, submarineJob);
    } else {
      submarineJob = mapSubmarineJob.get(noteId);
    }
    // need update InterpreterContext
    submarineJob.setIntpContext(context);

    return submarineJob;
  }

  public SubmarineJob getSubmarineJob(String nodeId) {
    if (!mapSubmarineJob.containsKey(nodeId)) {
      return null;
    }

    return mapSubmarineJob.get(nodeId);
  }

  public void stopAllSubmarineJob() {
    Iterator<Map.Entry<String, SubmarineJob>> iterator = mapSubmarineJob.entrySet().iterator();

    while (iterator.hasNext()) {
      Map.Entry<String, SubmarineJob> entry = iterator.next();

      entry.getValue().stopRunning();
    }
  }
}
