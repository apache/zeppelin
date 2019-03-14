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

import com.google.common.io.Resources;
import com.hubspot.jinjava.Jinjava;
import org.apache.commons.io.Charsets;
import org.apache.zeppelin.submarine.commons.SubmarineConstants;
import org.apache.zeppelin.submarine.job.SubmarineJob;
import org.apache.zeppelin.submarine.commons.SubmarineUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class JinjaTemplatesTest {
  private static Logger LOGGER = LoggerFactory.getLogger(JinjaTemplatesTest.class);

  @Test
  public void jobRunJinjaTemplateTest1() throws IOException {
    String str = jobRunJinjaTemplateTest(Boolean.TRUE, Boolean.TRUE);

    StringBuffer sbCheck = new StringBuffer();
    sbCheck.append("DOCKER_HADOOP_HDFS_HOME_VALUE/bin/yarn jar " +
        "HADOOP_YARN_SUBMARINE_JAR_VALUE \\\n" +
        "  job run \\\n" +
        "  --name JOB_NAME_VALUE \\\n" +
        "  --env DOCKER_JAVA_HOME=DOCKER_JAVA_HOME_VALUE \\\n" +
        "  --env DOCKER_HADOOP_HDFS_HOME=DOCKER_HADOOP_HDFS_HOME_VALUE \\\n" +
        "  --env PYTHONPATH=\"./submarine_algorithm:$PYTHONPATH\" \\\n" +
        "  --env YARN_CONTAINER_RUNTIME_DOCKER_CONTAINER_NETWORK=" +
        "DOCKER_CONTAINER_NETWORK_VALUE \\\n" +
        "  --env HADOOP_LOG_DIR=/tmp \\\n" +
        "  --env TZ=\"\" \\\n" +
        "  --input_path INPUT_PATH_VALUE \\\n" +
        "  --checkpoint_path CHECKPOINT_PATH_VALUE \\\n" +
        "  --queue SUBMARINE_YARN_QUEUE \\\n" +
        "  --num_ps TF_PARAMETER_SERVICES_NUM_VALUE \\\n" +
        "  --ps_docker_image TF_PARAMETER_SERVICES_DOCKER_IMAGE_VALUE \\\n" +
        "  --ps_resources memory=TF_PARAMETER_SERVICES_MEMORY_VALUE," +
        "vcores=TF_PARAMETER_SERVICES_CPU_VALUE,gpu=TF_PARAMETER_SERVICES_GPU_VALUE \\\n" +
        "  --ps_launch_cmd \"PS_LAUNCH_CMD_VALUE\" \\\n" +
        "  --num_workers TF_WORKER_SERVICES_NUM_VALUE \\\n" +
        "  --worker_docker_image TF_WORKER_SERVICES_DOCKER_IMAGE_VALUE \\\n" +
        "  --worker_resources memory=TF_WORKER_SERVICES_MEMORY_VALUE," +
        "vcores=TF_WORKER_SERVICES_CPU_VALUE,gpu=TF_WORKER_SERVICES_GPU_VALUE \\\n" +
        "  --worker_launch_cmd \"WORKER_LAUNCH_CMD_VALUE\" \\\n" +
        "  --localization \"hdfs://file1:.\" \\\n" +
        "  --localization \"hdfs://file2:.\" \\\n" +
        "  --localization \"hdfs://file3:.\" \\\n" +
        "  --localization \"SUBMARINE_ALGORITHM_HDFS_PATH_VALUE:./submarine_algorithm\" \\\n" +
        "  --localization \"SUBMARINE_HADOOP_CONF_DIR_VALUE:" +
        "SUBMARINE_HADOOP_CONF_DIR_VALUE\" \\\n" +
        "  --keytab SUBMARINE_HADOOP_KEYTAB_VALUE \\\n" +
        "  --principal SUBMARINE_HADOOP_PRINCIPAL_VALUE \\\n" +
        "  --distribute_keytab \\\n" +
        "  --verbose");

    assertEquals(str, sbCheck.toString());
  }

  @Test
  public void jobRunJinjaTemplateTest2() throws IOException {
    String str = jobRunJinjaTemplateTest(Boolean.TRUE, Boolean.FALSE);
    StringBuffer sbCheck = new StringBuffer();
    sbCheck.append("SUBMARINE_HADOOP_HOME_VALUE/bin/yarn jar HADOOP_YARN_SUBMARINE_JAR_VALUE \\\n" +
        "  job run \\\n" +
        "  --name JOB_NAME_VALUE \\\n" +
        "  --env DOCKER_JAVA_HOME=DOCKER_JAVA_HOME_VALUE \\\n" +
        "  --env DOCKER_HADOOP_HDFS_HOME=DOCKER_HADOOP_HDFS_HOME_VALUE \\\n" +
        "  --env PYTHONPATH=\"./submarine_algorithm:$PYTHONPATH\" \\\n" +
        "  --env YARN_CONTAINER_RUNTIME_DOCKER_CONTAINER_NETWORK=" +
        "DOCKER_CONTAINER_NETWORK_VALUE \\\n" +
        "  --env HADOOP_LOG_DIR=/tmp \\\n" +
        "  --env TZ=\"\" \\\n" +
        "  --input_path INPUT_PATH_VALUE \\\n" +
        "  --checkpoint_path CHECKPOINT_PATH_VALUE \\\n" +
        "  --queue SUBMARINE_YARN_QUEUE \\\n" +
        "  --num_ps TF_PARAMETER_SERVICES_NUM_VALUE \\\n" +
        "  --ps_docker_image TF_PARAMETER_SERVICES_DOCKER_IMAGE_VALUE \\\n" +
        "  --ps_resources memory=TF_PARAMETER_SERVICES_MEMORY_VALUE," +
        "vcores=TF_PARAMETER_SERVICES_CPU_VALUE,gpu=TF_PARAMETER_SERVICES_GPU_VALUE \\\n" +
        "  --ps_launch_cmd \"PS_LAUNCH_CMD_VALUE\" \\\n" +
        "  --num_workers TF_WORKER_SERVICES_NUM_VALUE \\\n" +
        "  --worker_docker_image TF_WORKER_SERVICES_DOCKER_IMAGE_VALUE \\\n" +
        "  --worker_resources memory=TF_WORKER_SERVICES_MEMORY_VALUE," +
        "vcores=TF_WORKER_SERVICES_CPU_VALUE,gpu=TF_WORKER_SERVICES_GPU_VALUE \\\n" +
        "  --worker_launch_cmd \"WORKER_LAUNCH_CMD_VALUE\" \\\n" +
        "  --localization \"hdfs://file1:.\" \\\n" +
        "  --localization \"hdfs://file2:.\" \\\n" +
        "  --localization \"hdfs://file3:.\" \\\n" +
        "  --localization \"SUBMARINE_ALGORITHM_HDFS_PATH_VALUE:./submarine_algorithm\" \\\n" +
        "  --localization \"SUBMARINE_HADOOP_CONF_DIR_VALUE:" +
        "SUBMARINE_HADOOP_CONF_DIR_VALUE\" \\\n" +
        "  --keytab SUBMARINE_HADOOP_KEYTAB_VALUE \\\n" +
        "  --principal SUBMARINE_HADOOP_PRINCIPAL_VALUE \\\n" +
        "  --distribute_keytab \\\n" +
        "  --verbose");
    assertEquals(str, sbCheck.toString());
  }

  @Test
  public void jobRunJinjaTemplateTest3() throws IOException {
    String str = jobRunJinjaTemplateTest(Boolean.TRUE, null);
    StringBuffer sbCheck = new StringBuffer();
    sbCheck.append("SUBMARINE_HADOOP_HOME_VALUE/bin/yarn jar HADOOP_YARN_SUBMARINE_JAR_VALUE \\\n" +
        "  job run \\\n" +
        "  --name JOB_NAME_VALUE \\\n" +
        "  --env DOCKER_JAVA_HOME=DOCKER_JAVA_HOME_VALUE \\\n" +
        "  --env DOCKER_HADOOP_HDFS_HOME=DOCKER_HADOOP_HDFS_HOME_VALUE \\\n" +
        "  --env PYTHONPATH=\"./submarine_algorithm:$PYTHONPATH\" \\\n" +
        "  --env YARN_CONTAINER_RUNTIME_DOCKER_CONTAINER_NETWORK=" +
        "DOCKER_CONTAINER_NETWORK_VALUE \\\n" +
        "  --env HADOOP_LOG_DIR=/tmp \\\n" +
        "  --env TZ=\"\" \\\n" +
        "  --input_path INPUT_PATH_VALUE \\\n" +
        "  --checkpoint_path CHECKPOINT_PATH_VALUE \\\n" +
        "  --queue SUBMARINE_YARN_QUEUE \\\n" +
        "  --num_ps TF_PARAMETER_SERVICES_NUM_VALUE \\\n" +
        "  --ps_docker_image TF_PARAMETER_SERVICES_DOCKER_IMAGE_VALUE \\\n" +
        "  --ps_resources memory=TF_PARAMETER_SERVICES_MEMORY_VALUE," +
        "vcores=TF_PARAMETER_SERVICES_CPU_VALUE,gpu=TF_PARAMETER_SERVICES_GPU_VALUE \\\n" +
        "  --ps_launch_cmd \"PS_LAUNCH_CMD_VALUE\" \\\n" +
        "  --num_workers TF_WORKER_SERVICES_NUM_VALUE \\\n" +
        "  --worker_docker_image TF_WORKER_SERVICES_DOCKER_IMAGE_VALUE \\\n" +
        "  --worker_resources memory=TF_WORKER_SERVICES_MEMORY_VALUE," +
        "vcores=TF_WORKER_SERVICES_CPU_VALUE,gpu=TF_WORKER_SERVICES_GPU_VALUE \\\n" +
        "  --worker_launch_cmd \"WORKER_LAUNCH_CMD_VALUE\" \\\n" +
        "  --localization \"hdfs://file1:.\" \\\n" +
        "  --localization \"hdfs://file2:.\" \\\n" +
        "  --localization \"hdfs://file3:.\" \\\n" +
        "  --localization \"SUBMARINE_ALGORITHM_HDFS_PATH_VALUE:./submarine_algorithm\" \\\n" +
        "  --localization \"SUBMARINE_HADOOP_CONF_DIR_VALUE:" +
        "SUBMARINE_HADOOP_CONF_DIR_VALUE\" \\\n" +
        "  --keytab SUBMARINE_HADOOP_KEYTAB_VALUE \\\n" +
        "  --principal SUBMARINE_HADOOP_PRINCIPAL_VALUE \\\n" +
        "  --distribute_keytab \\\n" +
        "  --verbose");
    assertEquals(str, sbCheck.toString());
  }

  @Test
  public void jobRunJinjaTemplateTest4() throws IOException {
    String str = jobRunJinjaTemplateTest(Boolean.FALSE, Boolean.TRUE);
    StringBuffer sbCheck = new StringBuffer();
    sbCheck.append("DOCKER_HADOOP_HDFS_HOME_VALUE/bin/yarn jar " +
        "HADOOP_YARN_SUBMARINE_JAR_VALUE \\\n" +
        "  job run \\\n" +
        "  --name JOB_NAME_VALUE \\\n" +
        "  --env DOCKER_JAVA_HOME=DOCKER_JAVA_HOME_VALUE \\\n" +
        "  --env DOCKER_HADOOP_HDFS_HOME=DOCKER_HADOOP_HDFS_HOME_VALUE \\\n" +
        "  --env PYTHONPATH=\"./submarine_algorithm:$PYTHONPATH\" \\\n" +
        "  --env YARN_CONTAINER_RUNTIME_DOCKER_CONTAINER_NETWORK=" +
        "DOCKER_CONTAINER_NETWORK_VALUE \\\n" +
        "  --env HADOOP_LOG_DIR=/tmp \\\n" +
        "  --env TZ=\"\" \\\n" +
        "  --input_path INPUT_PATH_VALUE \\\n" +
        "  --checkpoint_path CHECKPOINT_PATH_VALUE \\\n" +
        "  --queue SUBMARINE_YARN_QUEUE \\\n" +
        "  --num_workers 1 \\\n" +
        "  --worker_docker_image TF_WORKER_SERVICES_DOCKER_IMAGE_VALUE \\\n" +
        "  --worker_resources memory=TF_WORKER_SERVICES_MEMORY_VALUE," +
        "vcores=TF_WORKER_SERVICES_CPU_VALUE,gpu=TF_WORKER_SERVICES_GPU_VALUE \\\n" +
        "  --worker_launch_cmd \"WORKER_LAUNCH_CMD_VALUE\" \\\n" +
        "  --localization \"hdfs://file1:.\" \\\n" +
        "  --localization \"hdfs://file2:.\" \\\n" +
        "  --localization \"hdfs://file3:.\" \\\n" +
        "  --localization \"SUBMARINE_ALGORITHM_HDFS_PATH_VALUE:./submarine_algorithm\" \\\n" +
        "  --localization \"SUBMARINE_HADOOP_CONF_DIR_VALUE:" +
        "SUBMARINE_HADOOP_CONF_DIR_VALUE\" \\\n" +
        "  --keytab SUBMARINE_HADOOP_KEYTAB_VALUE \\\n" +
        "  --principal SUBMARINE_HADOOP_PRINCIPAL_VALUE \\\n" +
        "  --distribute_keytab \\\n" +
        "  --verbose");
    assertEquals(str, sbCheck.toString());
  }

  @Test
  public void jobRunJinjaTemplateTest5() throws IOException {
    String str = jobRunJinjaTemplateTest(Boolean.FALSE, Boolean.FALSE);
    StringBuffer sbCheck = new StringBuffer();
    sbCheck.append("SUBMARINE_HADOOP_HOME_VALUE/bin/yarn jar " +
        "HADOOP_YARN_SUBMARINE_JAR_VALUE \\\n" +
        "  job run \\\n" +
        "  --name JOB_NAME_VALUE \\\n" +
        "  --env DOCKER_JAVA_HOME=DOCKER_JAVA_HOME_VALUE \\\n" +
        "  --env DOCKER_HADOOP_HDFS_HOME=DOCKER_HADOOP_HDFS_HOME_VALUE \\\n" +
        "  --env PYTHONPATH=\"./submarine_algorithm:$PYTHONPATH\" \\\n" +
        "  --env YARN_CONTAINER_RUNTIME_DOCKER_CONTAINER_NETWORK=" +
        "DOCKER_CONTAINER_NETWORK_VALUE \\\n" +
        "  --env HADOOP_LOG_DIR=/tmp \\\n" +
        "  --env TZ=\"\" \\\n" +
        "  --input_path INPUT_PATH_VALUE \\\n" +
        "  --checkpoint_path CHECKPOINT_PATH_VALUE \\\n" +
        "  --queue SUBMARINE_YARN_QUEUE \\\n" +
        "  --num_workers 1 \\\n" +
        "  --worker_docker_image TF_WORKER_SERVICES_DOCKER_IMAGE_VALUE \\\n" +
        "  --worker_resources memory=TF_WORKER_SERVICES_MEMORY_VALUE," +
        "vcores=TF_WORKER_SERVICES_CPU_VALUE,gpu=TF_WORKER_SERVICES_GPU_VALUE \\\n" +
        "  --worker_launch_cmd \"WORKER_LAUNCH_CMD_VALUE\" \\\n" +
        "  --localization \"hdfs://file1:.\" \\\n" +
        "  --localization \"hdfs://file2:.\" \\\n" +
        "  --localization \"hdfs://file3:.\" \\\n" +
        "  --localization \"SUBMARINE_ALGORITHM_HDFS_PATH_VALUE:./submarine_algorithm\" \\\n" +
        "  --localization \"SUBMARINE_HADOOP_CONF_DIR_VALUE:" +
        "SUBMARINE_HADOOP_CONF_DIR_VALUE\" \\\n" +
        "  --keytab SUBMARINE_HADOOP_KEYTAB_VALUE \\\n" +
        "  --principal SUBMARINE_HADOOP_PRINCIPAL_VALUE \\\n" +
        "  --distribute_keytab \\\n" +
        "  --verbose");
    assertEquals(str, sbCheck.toString());
  }

  @Test
  public void jobRunJinjaTemplateTest6() throws IOException {
    String str = jobRunJinjaTemplateTest(null, Boolean.FALSE);
    StringBuffer sbCheck = new StringBuffer();
    sbCheck.append("SUBMARINE_HADOOP_HOME_VALUE/bin/yarn jar HADOOP_YARN_SUBMARINE_JAR_VALUE \\\n" +
        "  job run \\\n" +
        "  --name JOB_NAME_VALUE \\\n" +
        "  --env DOCKER_JAVA_HOME=DOCKER_JAVA_HOME_VALUE \\\n" +
        "  --env DOCKER_HADOOP_HDFS_HOME=DOCKER_HADOOP_HDFS_HOME_VALUE \\\n" +
        "  --env PYTHONPATH=\"./submarine_algorithm:$PYTHONPATH\" \\\n" +
        "  --env YARN_CONTAINER_RUNTIME_DOCKER_CONTAINER_NETWORK=" +
        "DOCKER_CONTAINER_NETWORK_VALUE \\\n" +
        "  --env HADOOP_LOG_DIR=/tmp \\\n" +
        "  --env TZ=\"\" \\\n" +
        "  --input_path INPUT_PATH_VALUE \\\n" +
        "  --checkpoint_path CHECKPOINT_PATH_VALUE \\\n" +
        "  --queue SUBMARINE_YARN_QUEUE \\\n" +
        "  --num_workers 1 \\\n" +
        "  --worker_docker_image TF_WORKER_SERVICES_DOCKER_IMAGE_VALUE \\\n" +
        "  --worker_resources memory=TF_WORKER_SERVICES_MEMORY_VALUE," +
        "vcores=TF_WORKER_SERVICES_CPU_VALUE,gpu=TF_WORKER_SERVICES_GPU_VALUE \\\n" +
        "  --worker_launch_cmd \"WORKER_LAUNCH_CMD_VALUE\" \\\n" +
        "  --localization \"hdfs://file1:.\" \\\n" +
        "  --localization \"hdfs://file2:.\" \\\n" +
        "  --localization \"hdfs://file3:.\" \\\n" +
        "  --localization \"SUBMARINE_ALGORITHM_HDFS_PATH_VALUE:./submarine_algorithm\" \\\n" +
        "  --localization \"SUBMARINE_HADOOP_CONF_DIR_VALUE:" +
        "SUBMARINE_HADOOP_CONF_DIR_VALUE\" \\\n" +
        "  --keytab SUBMARINE_HADOOP_KEYTAB_VALUE \\\n" +
        "  --principal SUBMARINE_HADOOP_PRINCIPAL_VALUE \\\n" +
        "  --distribute_keytab \\\n" +
        "  --verbose");
    assertEquals(str, sbCheck.toString());
  }

  @Test
  public void jobRunJinjaTemplateTest7() throws IOException {
    String str = jobRunJinjaTemplateTest(null, null);
    StringBuffer sbCheck = new StringBuffer();
    sbCheck.append("SUBMARINE_HADOOP_HOME_VALUE/bin/yarn jar HADOOP_YARN_SUBMARINE_JAR_VALUE \\\n" +
        "  job run \\\n" +
        "  --name JOB_NAME_VALUE \\\n" +
        "  --env DOCKER_JAVA_HOME=DOCKER_JAVA_HOME_VALUE \\\n" +
        "  --env DOCKER_HADOOP_HDFS_HOME=DOCKER_HADOOP_HDFS_HOME_VALUE \\\n" +
        "  --env PYTHONPATH=\"./submarine_algorithm:$PYTHONPATH\" \\\n" +
        "  --env YARN_CONTAINER_RUNTIME_DOCKER_CONTAINER_NETWORK=" +
        "DOCKER_CONTAINER_NETWORK_VALUE \\\n" +
        "  --env HADOOP_LOG_DIR=/tmp \\\n" +
        "  --env TZ=\"\" \\\n" +
        "  --input_path INPUT_PATH_VALUE \\\n" +
        "  --checkpoint_path CHECKPOINT_PATH_VALUE \\\n" +
        "  --queue SUBMARINE_YARN_QUEUE \\\n" +
        "  --num_workers 1 \\\n" +
        "  --worker_docker_image TF_WORKER_SERVICES_DOCKER_IMAGE_VALUE \\\n" +
        "  --worker_resources memory=TF_WORKER_SERVICES_MEMORY_VALUE," +
        "vcores=TF_WORKER_SERVICES_CPU_VALUE,gpu=TF_WORKER_SERVICES_GPU_VALUE \\\n" +
        "  --worker_launch_cmd \"WORKER_LAUNCH_CMD_VALUE\" \\\n" +
        "  --localization \"hdfs://file1:.\" \\\n" +
        "  --localization \"hdfs://file2:.\" \\\n" +
        "  --localization \"hdfs://file3:.\" \\\n" +
        "  --localization \"SUBMARINE_ALGORITHM_HDFS_PATH_VALUE:./submarine_algorithm\" \\\n" +
        "  --localization \"SUBMARINE_HADOOP_CONF_DIR_VALUE:" +
        "SUBMARINE_HADOOP_CONF_DIR_VALUE\" \\\n" +
        "  --keytab SUBMARINE_HADOOP_KEYTAB_VALUE \\\n" +
        "  --principal SUBMARINE_HADOOP_PRINCIPAL_VALUE \\\n" +
        "  --distribute_keytab \\\n" +
        "  --verbose");
    assertEquals(str, sbCheck.toString());
  }

  @Test
  public void jobRunJinjaTemplateTest8() throws IOException {
    String str = tensorboardJinjaTemplateTest(Boolean.TRUE, Boolean.TRUE);
    StringBuffer sbCheck = new StringBuffer();
    sbCheck.append("DOCKER_HADOOP_HDFS_HOME_VALUE/bin/yarn jar \\\n" +
        "  HADOOP_YARN_SUBMARINE_JAR_VALUE job run \\\n" +
        "  --name JOB_NAME_VALUE \\\n" +
        "  --env DOCKER_JAVA_HOME=DOCKER_JAVA_HOME_VALUE \\\n" +
        "  --env DOCKER_HADOOP_HDFS_HOME=DOCKER_HADOOP_HDFS_HOME_VALUE \\\n" +
        "  --env YARN_CONTAINER_RUNTIME_DOCKER_CONTAINER_NETWORK=bridge \\\n" +
        "  --env TZ=\"\" \\\n" +
        "  --checkpoint_path CHECKPOINT_PATH_VALUE \\\n" +
        "  --queue SUBMARINE_YARN_QUEUE \\\n" +
        "  --num_workers 0 \\\n" +
        "  --tensorboard \\\n" +
        "  --tensorboard_docker_image TF_PARAMETER_SERVICES_DOCKER_IMAGE_VALUE \\\n" +
        "  --keytab SUBMARINE_HADOOP_KEYTAB_VALUE \\\n" +
        "  --principal SUBMARINE_HADOOP_PRINCIPAL_VALUE \\\n" +
        "  --distribute_keytab \\\n" +
        "  --verbose");
    assertEquals(str, sbCheck.toString());
  }

  @Test
  public void jobRunJinjaTemplateTest9() throws IOException {
    String str = tensorboardJinjaTemplateTest(Boolean.TRUE, Boolean.FALSE);
    StringBuffer sbCheck = new StringBuffer();
    sbCheck.append("SUBMARINE_HADOOP_HOME_VALUE/bin/yarn jar \\\n" +
        "  HADOOP_YARN_SUBMARINE_JAR_VALUE job run \\\n" +
        "  --name JOB_NAME_VALUE \\\n" +
        "  --env DOCKER_JAVA_HOME=DOCKER_JAVA_HOME_VALUE \\\n" +
        "  --env DOCKER_HADOOP_HDFS_HOME=DOCKER_HADOOP_HDFS_HOME_VALUE \\\n" +
        "  --env YARN_CONTAINER_RUNTIME_DOCKER_CONTAINER_NETWORK=bridge \\\n" +
        "  --env TZ=\"\" \\\n" +
        "  --checkpoint_path CHECKPOINT_PATH_VALUE \\\n" +
        "  --queue SUBMARINE_YARN_QUEUE \\\n" +
        "  --num_workers 0 \\\n" +
        "  --tensorboard \\\n" +
        "  --tensorboard_docker_image TF_PARAMETER_SERVICES_DOCKER_IMAGE_VALUE \\\n" +
        "  --keytab SUBMARINE_HADOOP_KEYTAB_VALUE \\\n" +
        "  --principal SUBMARINE_HADOOP_PRINCIPAL_VALUE \\\n" +
        "  --distribute_keytab \\\n" +
        "  --verbose");
    assertEquals(str, sbCheck.toString());
  }

  public String jobRunJinjaTemplateTest(Boolean dist, Boolean launchMode) throws IOException {
    URL urlTemplate = Resources.getResource(SubmarineJob.SUBMARINE_JOBRUN_TF_JINJA);
    String template = Resources.toString(urlTemplate, Charsets.UTF_8);
    Jinjava jinjava = new Jinjava();
    HashMap<String, Object> jinjaParams = initJinjaParams(dist, launchMode);

    String submarineCmd = jinjava.render(template, jinjaParams);
    int pos = submarineCmd.indexOf("\n");
    if (pos == 0) {
      submarineCmd = submarineCmd.replaceFirst("\n", "");
    }

    LOGGER.info("------------------------");
    LOGGER.info(submarineCmd);
    LOGGER.info("------------------------");

    return submarineCmd;
  }

  public String tensorboardJinjaTemplateTest(Boolean dist, Boolean launchMode) throws IOException {
    URL urlTemplate = Resources.getResource(SubmarineJob.SUBMARINE_TENSORBOARD_JINJA);
    String template = Resources.toString(urlTemplate, Charsets.UTF_8);
    Jinjava jinjava = new Jinjava();
    HashMap<String, Object> jinjaParams = initJinjaParams(dist, launchMode);

    String submarineCmd = jinjava.render(template, jinjaParams);
    int pos = submarineCmd.indexOf("\n");
    if (pos == 0) {
      submarineCmd = submarineCmd.replaceFirst("\n", "");
    }
    LOGGER.info("------------------------");
    LOGGER.info(submarineCmd);
    LOGGER.info("------------------------");

    return submarineCmd;
  }

  private HashMap<String, Object> initJinjaParams(Boolean dist, Boolean launchMode) {
    HashMap<String, Object> jinjaParams = new HashMap();

    if (launchMode == Boolean.TRUE) {
      jinjaParams.put(SubmarineUtils.unifyKey(SubmarineConstants.INTERPRETER_LAUNCH_MODE), "yarn");
    } else if (launchMode == Boolean.FALSE) {
      jinjaParams.put(SubmarineUtils.unifyKey(SubmarineConstants.INTERPRETER_LAUNCH_MODE), "local");
    }

    if (dist == Boolean.TRUE) {
      jinjaParams.put(SubmarineUtils.unifyKey(
          SubmarineConstants.MACHINELEARNING_DISTRIBUTED_ENABLE), "true");
    } else if (dist == Boolean.FALSE) {
      jinjaParams.put(SubmarineUtils.unifyKey(
          SubmarineConstants.MACHINELEARNING_DISTRIBUTED_ENABLE), "false");
    }

    jinjaParams.put(SubmarineUtils.unifyKey(
        SubmarineConstants.TF_TENSORBOARD_ENABLE), "true");

    List<String> arrayHdfsFiles = new ArrayList<>();
    arrayHdfsFiles.add("hdfs://file1");
    arrayHdfsFiles.add("hdfs://file2");
    arrayHdfsFiles.add("hdfs://file3");
    jinjaParams.put(SubmarineUtils.unifyKey(
        SubmarineConstants.SUBMARINE_ALGORITHM_HDFS_FILES), arrayHdfsFiles);

    // mock
    jinjaParams.put(SubmarineUtils.unifyKey(
        SubmarineConstants.DOCKER_HADOOP_HDFS_HOME), "DOCKER_HADOOP_HDFS_HOME_VALUE");
    jinjaParams.put(SubmarineUtils.unifyKey(
        SubmarineConstants.JOB_NAME), "JOB_NAME_VALUE");
    jinjaParams.put(SubmarineUtils.unifyKey(
        SubmarineConstants.SUBMARINE_YARN_QUEUE), "SUBMARINE_YARN_QUEUE");
    jinjaParams.put(SubmarineUtils.unifyKey(
        SubmarineConstants.HADOOP_YARN_SUBMARINE_JAR), "HADOOP_YARN_SUBMARINE_JAR_VALUE");
    jinjaParams.put(SubmarineUtils.unifyKey(
        SubmarineConstants.SUBMARINE_HADOOP_HOME), "SUBMARINE_HADOOP_HOME_VALUE");
    jinjaParams.put(SubmarineUtils.unifyKey(
        SubmarineConstants.DOCKER_JAVA_HOME), "DOCKER_JAVA_HOME_VALUE");
    jinjaParams.put(SubmarineUtils.unifyKey(
        SubmarineConstants.DOCKER_CONTAINER_NETWORK), "DOCKER_CONTAINER_NETWORK_VALUE");
    jinjaParams.put(SubmarineUtils.unifyKey(
        SubmarineConstants.INPUT_PATH), "INPUT_PATH_VALUE");
    jinjaParams.put(SubmarineUtils.unifyKey(
        SubmarineConstants.CHECKPOINT_PATH), "CHECKPOINT_PATH_VALUE");
    jinjaParams.put(SubmarineUtils.unifyKey(
        SubmarineConstants.TF_PARAMETER_SERVICES_NUM), "TF_PARAMETER_SERVICES_NUM_VALUE");
    jinjaParams.put(SubmarineUtils.unifyKey(
        SubmarineConstants.TF_PARAMETER_SERVICES_DOCKER_IMAGE),
        "TF_PARAMETER_SERVICES_DOCKER_IMAGE_VALUE");
    jinjaParams.put(SubmarineUtils.unifyKey(
        SubmarineConstants.TF_PARAMETER_SERVICES_MEMORY), "TF_PARAMETER_SERVICES_MEMORY_VALUE");
    jinjaParams.put(SubmarineUtils.unifyKey(
        SubmarineConstants.TF_PARAMETER_SERVICES_CPU), "TF_PARAMETER_SERVICES_CPU_VALUE");
    jinjaParams.put(SubmarineUtils.unifyKey(
        SubmarineConstants.TF_PARAMETER_SERVICES_GPU), "TF_PARAMETER_SERVICES_GPU_VALUE");
    jinjaParams.put(SubmarineUtils.unifyKey(
        SubmarineConstants.PS_LAUNCH_CMD), "PS_LAUNCH_CMD_VALUE");
    jinjaParams.put(SubmarineUtils.unifyKey(
        SubmarineConstants.TF_WORKER_SERVICES_DOCKER_IMAGE),
        "TF_WORKER_SERVICES_DOCKER_IMAGE_VALUE");
    jinjaParams.put(SubmarineUtils.unifyKey(
        SubmarineConstants.TF_WORKER_SERVICES_NUM), "TF_WORKER_SERVICES_NUM_VALUE");
    jinjaParams.put(SubmarineUtils.unifyKey(
        SubmarineConstants.TF_WORKER_SERVICES_DOCKER_IMAGE),
        "TF_WORKER_SERVICES_DOCKER_IMAGE_VALUE");
    jinjaParams.put(SubmarineUtils.unifyKey(
        SubmarineConstants.TF_WORKER_SERVICES_MEMORY), "TF_WORKER_SERVICES_MEMORY_VALUE");
    jinjaParams.put(SubmarineUtils.unifyKey(
        SubmarineConstants.TF_WORKER_SERVICES_CPU), "TF_WORKER_SERVICES_CPU_VALUE");
    jinjaParams.put(SubmarineUtils.unifyKey(
        SubmarineConstants.TF_WORKER_SERVICES_GPU), "TF_WORKER_SERVICES_GPU_VALUE");
    jinjaParams.put(SubmarineUtils.unifyKey(
        SubmarineConstants.WORKER_LAUNCH_CMD), "WORKER_LAUNCH_CMD_VALUE");
    jinjaParams.put(SubmarineUtils.unifyKey(
        SubmarineConstants.SUBMARINE_ALGORITHM_HDFS_PATH),
        "SUBMARINE_ALGORITHM_HDFS_PATH_VALUE");
    jinjaParams.put(SubmarineUtils.unifyKey(
        SubmarineConstants.SUBMARINE_HADOOP_CONF_DIR), "SUBMARINE_HADOOP_CONF_DIR_VALUE");
    jinjaParams.put(SubmarineUtils.unifyKey(
        SubmarineConstants.SUBMARINE_HADOOP_KEYTAB), "SUBMARINE_HADOOP_KEYTAB_VALUE");
    jinjaParams.put(SubmarineUtils.unifyKey(
        SubmarineConstants.SUBMARINE_HADOOP_PRINCIPAL), "SUBMARINE_HADOOP_PRINCIPAL_VALUE");

    return jinjaParams;
  }
}
