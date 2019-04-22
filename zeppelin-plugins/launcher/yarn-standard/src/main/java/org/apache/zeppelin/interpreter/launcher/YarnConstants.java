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

package org.apache.zeppelin.interpreter.launcher;

/*
 * NOTE: use lowercase + "_" for the option name
 */
public class YarnConstants {
  // Docker container Environmental variable at `submarine-job-run-tf.jinja`
  // and `/bin/interpreter.sh`
  public static final String DOCKER_HADOOP_HOME       = "DOCKER_HADOOP_HOME";
  public static final String DOCKER_JAVA_HOME         = "DOCKER_JAVA_HOME";
  public static final String DOCKER_HADOOP_CONF_DIR   = "DOCKER_HADOOP_CONF_DIR";
  public static final String DOCKER_CONTAINER_TIME_ZONE = "DOCKER_CONTAINER_TIME_ZONE";
  public static final String INTERPRETER_LAUNCH_MODE = "INTERPRETER_LAUNCH_MODE";

  // interpreter.sh Environmental variable
  public static final String SUBMARINE_HADOOP_HOME  = "SUBMARINE_HADOOP_HOME";
  public static final String HADOOP_YARN_SUBMARINE_JAR  = "HADOOP_YARN_SUBMARINE_JAR";
  public static final String SUBMARINE_INTERPRETER_DOCKER_IMAGE
      = "SUBMARINE_INTERPRETER_DOCKER_IMAGE";

  public static final String ZEPPELIN_SUBMARINE_AUTH_TYPE = "zeppelin.submarine.auth.type";
  public static final String SUBMARINE_HADOOP_CONF_DIR  = "SUBMARINE_HADOOP_CONF_DIR";
  public static final String SUBMARINE_HADOOP_KEYTAB    = "SUBMARINE_HADOOP_KEYTAB";
  public static final String SUBMARINE_HADOOP_PRINCIPAL = "SUBMARINE_HADOOP_PRINCIPAL";
  public static final String SUBMARINE_HADOOP_KRB5_CONF = "submarine.hadoop.krb5.conf";

  public static final String JOB_NAME = "JOB_NAME";
  public static final String CLEAN_CHECKPOINT = "CLEAN_CHECKPOINT";
  public static final String INPUT_PATH = "INPUT_PATH";
  public static final String CHECKPOINT_PATH = "CHECKPOINT_PATH";
  public static final String PS_LAUNCH_CMD = "PS_LAUNCH_CMD";
  public static final String WORKER_LAUNCH_CMD = "WORKER_LAUNCH_CMD";
  public static final String MACHINELEARNING_DISTRIBUTED_ENABLE
      = "machinelearning.distributed.enable";

  public static final String ZEPPELIN_INTERPRETER_RPC_PORTRANGE
      = "zeppelin.interpreter.rpc.portRange";

  public static final String DOCKER_CONTAINER_NETWORK   = "docker.container.network";
  public static final String SUBMARINE_YARN_QUEUE       = "submarine.yarn.queue";
  public static final String SUBMARINE_CONCURRENT_MAX   = "submarine.concurrent.max";

  public static final String SUBMARINE_ALGORITHM_HDFS_PATH  = "submarine.algorithm.hdfs.path";
  public static final String SUBMARINE_ALGORITHM_HDFS_FILES = "submarine.algorithm.hdfs.files";

  public static final String TF_PARAMETER_SERVICES_DOCKER_IMAGE
      = "tf.parameter.services.docker.image";
  public static final String TF_PARAMETER_SERVICES_NUM = "tf.parameter.services.num";
  public static final String TF_PARAMETER_SERVICES_GPU = "tf.parameter.services.gpu";
  public static final String TF_PARAMETER_SERVICES_CPU = "tf.parameter.services.cpu";
  public static final String TF_PARAMETER_SERVICES_MEMORY = "tf.parameter.services.memory";

  public static final String TF_WORKER_SERVICES_DOCKER_IMAGE = "tf.worker.services.docker.image";
  public static final String TF_WORKER_SERVICES_NUM = "tf.worker.services.num";
  public static final String TF_WORKER_SERVICES_GPU = "tf.worker.services.gpu";
  public static final String TF_WORKER_SERVICES_CPU = "tf.worker.services.cpu";
  public static final String TF_WORKER_SERVICES_MEMORY = "tf.worker.services.memory";

  public static final String TF_TENSORBOARD_ENABLE  = "tf.tensorboard.enable";
  public static final String TF_CHECKPOINT_PATH = "tf.checkpoint.path";

  public static final String COMMAND_TYPE     = "COMMAND_TYPE";
  public static final String OPERATION_TYPE   = "OPERATION_TYPE";
  public static final String COMMAND_USAGE    = "USAGE";
  public static final String COMMAND_JOB_LIST = "JOB LIST";
  public static final String COMMAND_JOB_SHOW = "JOB SHOW";
  public static final String COMMAND_JOB_RUN  = "JOB RUN";
  public static final String COMMAND_JOB_STOP = "JOB STOP";
  public static final String COMMAND_CLEAN    = "CLEAN";
  public static final String COMMAND_ACTIVE   = "COMMAND_ACTIVE";

  public static final String PARAGRAPH_ID  = "PARAGRAPH_ID";

  public static final String COMMANDLINE_OPTIONS = "COMMANDLINE_OPTIONS";

  // YARN
  public static final String YARN_WEB_ADDRESS
      = "yarn.webapp.http.address";

  public static final String YARN_APPLICATION_ID     = "YARN_APPLICATION_ID";
  public static final String YARN_APPLICATION_NAME   = "YARN_APPLICATION_NAME";
  public static final String YARN_APPLICATION_URL    = "YARN_APPLICATION_URL";
  public static final String YARN_APPLICATION_STATUS = "YARN_APPLICATION_STATUS";
  public static final String YARN_APPLICATION_FINAL_STATUS = "YARN_APPLICATION_FINAL_STATUS";
  public static final String YARN_TENSORBOARD_URL = "YARN_TENSORBOARD_URL";
  public static final String TENSORBOARD_URL         = "TENSORBOARD_URL";
  public static final String YARN_APP_STARTED_TIME   = "YARN_APP_STARTED_TIME";
  public static final String YARN_APP_LAUNCH_TIME    = "YARN_APP_LAUNCH_TIME";
  public static final String YARN_APP_FINISHED_TIME  = "YARN_APP_FINISHED_TIME";
  public static final String YARN_APP_ELAPSED_TIME   = "YARN_APP_ELAPSED_TIME";
  public static final String YARN_APP_STATE_NAME        = "state";
  public static final String YARN_APP_FINAL_STATUS_NAME = "finalStatus";
  public static final String YARN_APP_STARTEDTIME_NAME  = "startedTime";
  public static final String YARN_APP_LAUNCHTIME_NAME   = "launchTime";

  public static final String YARN_APPLICATION_STATUS_ACCEPT    = "ACCEPT";
  public static final String YARN_APPLICATION_STATUS_RUNNING   = "RUNNING";
  public static final String YARN_APPLICATION_STATUS_FINISHED  = "EXECUTE_SUBMARINE_FINISHED";
  public static final String YARN_APPLICATION_STATUS_FAILED    = "FAILED";

  public static final String JOB_STATUS   = "JOB_STATUS";


  // submarine.algorithm.hdfs.path support for replacing ${user.name} with real user name
  public static final String USERNAME_SYMBOL = "${user.name}";

  public static String unifyKey(String key) {
    key = key.replace(".", "_").toUpperCase();
    return key;
  }
}
