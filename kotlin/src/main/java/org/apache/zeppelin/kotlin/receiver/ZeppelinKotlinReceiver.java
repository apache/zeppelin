package org.apache.zeppelin.kotlin.receiver;

import org.apache.zeppelin.interpreter.BaseZeppelinContext;

public class ZeppelinKotlinReceiver extends KotlinReceiver {
  public BaseZeppelinContext z;
  public ZeppelinKotlinReceiver(BaseZeppelinContext z) {
    this.z = z;
  }
}
