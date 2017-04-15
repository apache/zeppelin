package org.apache.zeppelin.cluster.yarn;

/**
 *
 */
public class DSConstants {
  /**
   * Environment key name pointing to the shell script's location
   */
  public static final String DISTRIBUTEDSHELLSCRIPTLOCATION = "DISTRIBUTEDSHELLSCRIPTLOCATION";

  /**
   * Environment key name denoting the file timestamp for the shell script.
   * Used to validate the local resource.
   */
  public static final String DISTRIBUTEDSHELLSCRIPTTIMESTAMP = "DISTRIBUTEDSHELLSCRIPTTIMESTAMP";

  /**
   * Environment key name denoting the file content length for the shell script.
   * Used to validate the local resource.
   */
  public static final String DISTRIBUTEDSHELLSCRIPTLEN = "DISTRIBUTEDSHELLSCRIPTLEN";

  /**
   * Environment key name denoting the timeline domain ID.
   */
  public static final String DISTRIBUTEDSHELLTIMELINEDOMAIN = "DISTRIBUTEDSHELLTIMELINEDOMAIN";
}
