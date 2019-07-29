package org.apache.zeppelin.kotlin.context;

import org.apache.zeppelin.interpreter.BaseZeppelinContext;

public class ZeppelinKotlinContext extends KotlinContext {
  public BaseZeppelinContext z;
  public ZeppelinKotlinContext(BaseZeppelinContext z) {
    this.z = z;
  }
}
