package org.apache.zeppelin.interpreter.recovery;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.launcher.InterpreterClient;
import org.apache.zeppelin.util.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;


/**
 * Utility class for stopping interpreter in the case that you want to stop all the
 * interpreter process even when you enable recovery, or you want to kill interpreter process
 * to avoid orphan process.
 */
public class StopInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(StopInterpreter.class);

  public static void main(String[] args) throws IOException {
    ZeppelinConfiguration zConf = ZeppelinConfiguration.create();
    InterpreterSettingManager interpreterSettingManager =
            new InterpreterSettingManager(zConf, null, null, null);

    RecoveryStorage recoveryStorage  = ReflectionUtils.createClazzInstance(zConf.getRecoveryStorageClass(),
        new Class[] {ZeppelinConfiguration.class, InterpreterSettingManager.class},
        new Object[] {zConf, interpreterSettingManager});

    LOGGER.info("Using RecoveryStorage: {}", recoveryStorage.getClass().getName());
    Map<String, InterpreterClient> restoredClients = recoveryStorage.restore();
    if (restoredClients != null) {
      for (InterpreterClient client : restoredClients.values()) {
        LOGGER.info("Stop Interpreter Process: {}:{}", client.getHost(), client.getPort());
        client.stop();
      }
    }
  }
}
