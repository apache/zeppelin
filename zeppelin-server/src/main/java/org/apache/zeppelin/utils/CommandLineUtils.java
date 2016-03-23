package org.apache.zeppelin.utils;

import org.apache.zeppelin.util.Util;

import java.util.Locale;

/**
 * CommandLine Support Class
 */
public class CommandLineUtils {
  public static void main(String[] args) {
    if (args.length == 0) {
      return;
    }

    String usage = args[0].toLowerCase(Locale.US);
    switch (usage) {
      case "-version":
      case "-v":
        System.out.println(Util.getVersion());
        break;

      default:
    }
  }
}
