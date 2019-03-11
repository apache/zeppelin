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
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.ParagraphInfo;
import org.apache.zeppelin.python.IPythonInterpreter;
import org.apache.zeppelin.python.PythonInterpreter;
import org.apache.zeppelin.submarine.commons.SubmarineConstants;
import org.apache.zeppelin.submarine.job.SubmarineJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;

public class PySubmarineInterpreter extends PythonInterpreter {
  private static final Logger LOGGER = LoggerFactory.getLogger(PySubmarineInterpreter.class);

  private SubmarineInterpreter submarineInterpreter = null;
  private SubmarineContext submarineContext = null;

  public PySubmarineInterpreter(Properties property) {
    super(property);
    submarineContext = SubmarineContext.getInstance();
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context)
      throws InterpreterException {
    // algorithm & checkpoint path support replaces ${username} with real user name
    String algorithmPath = properties.getProperty(
        SubmarineConstants.SUBMARINE_ALGORITHM_HDFS_PATH, "");
    if (algorithmPath.contains(SubmarineConstants.USERNAME_SYMBOL)) {
      algorithmPath = algorithmPath.replace(SubmarineConstants.USERNAME_SYMBOL, userName);
      properties.setProperty(SubmarineConstants.SUBMARINE_ALGORITHM_HDFS_PATH, algorithmPath);
    }
    String checkpointPath = properties.getProperty(
        SubmarineConstants.TF_CHECKPOINT_PATH, "");
    if (checkpointPath.contains(SubmarineConstants.USERNAME_SYMBOL)) {
      checkpointPath = checkpointPath.replace(SubmarineConstants.USERNAME_SYMBOL, userName);
      properties.setProperty(SubmarineConstants.TF_CHECKPOINT_PATH, checkpointPath);
    }

    if (null == submarineInterpreter) {
      submarineInterpreter = getInterpreterInTheSameSessionByClassName(
          SubmarineInterpreter.class);
      submarineInterpreter.setPythonWorkDir(context.getNoteId(), getPythonWorkDir());
    }

    SubmarineJob submarineJob = submarineContext.addOrGetSubmarineJob(this.properties, context);
    if (null != submarineJob && null != submarineJob.getHdfsClient()) {
      try {
        String noteId = context.getNoteId();
        List<ParagraphInfo> paragraphInfos = context.getIntpEventClient()
            .getParagraphList(userName, noteId);
        submarineJob.getHdfsClient().saveParagraphToFiles(
            noteId, paragraphInfos, getPythonWorkDir().getAbsolutePath(), properties);
      } catch (Exception e) {
        LOGGER.error(e.getMessage(), e);
      }
    }
    return super.interpret(st, context);
  }

  @Override
  protected IPythonInterpreter getIPythonInterpreter() throws InterpreterException {
    return getInterpreterInTheSameSessionByClassName(IPySubmarineInterpreter.class, false);
  }
}
