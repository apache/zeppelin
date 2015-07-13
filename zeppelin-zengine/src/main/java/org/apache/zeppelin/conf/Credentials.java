package org.apache.zeppelin.conf;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;

/**
 * 
 * @author vgmartinez
 *
 */
public class Credentials {
  //static String aws_access_key_id = System.getenv("AWS_ACCESS_KEY_ID");
  //static String aws_secret_access_key = System.getenv("AWS_SECRET_ACCESS_KEY");
  
  static String aws_access_key_id = "AKIAIHDMNYZE3NLWQO5A";
  static String aws_secret_access_key = "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";
  
  private static AWSCredentials credentials = new BasicAWSCredentials(aws_access_key_id,
      aws_secret_access_key);

  public AWSCredentials getCredentials() {
    return credentials;
  }

  public static void setCredentials(AWSCredentials credentials) {
    Credentials.credentials = credentials;
  }
}
