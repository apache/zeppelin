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

package org.apache.zeppelin.submarine.commons;

import javafx.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class CommandParser {
  private Logger LOGGER = LoggerFactory.getLogger(CommandParser.class);

  private Map<String, String> configValues = new HashMap<>();

  /**
   * The default character used to denote comments
   */
  public static final transient char COMMENT = '#';

  /**
   * The default character used to denote assignment
   */
  public static final transient char EQUAL_SIGN = '=';

  private String command = "";

  /**
   * Populate this Config using a Scanner
   *
   * @param sc
   */
  public void populate(Scanner sc) {
    while (sc.hasNext()) {
      parseAndAdd(sc.nextLine());
    }
  }

  public void populate(String sc) {
    String[] lines = sc.split("\n");
    for (int n = 0; n < lines.length; n++) {
      String line = lines[n];
      parseAndAdd(line);
    }
  }

  /**
   * Populate this Config using a BufferedReader
   *
   * @param sc
   */
  public void populate(BufferedReader sc) {
    Stream<String> s = sc.lines();

    s.forEach(new Consumer<String>() {
      @Override
      public void accept(String arg0) {
        parseAndAdd(arg0);
      }
    });
  }

  /**
   * Populate Config with file
   * @param f
   */
  public void populate(File f) {
    try {
      BufferedReader br = new BufferedReader(new FileReader(f));
      populate(br);
      br.close();
    } catch (FileNotFoundException e) {
      LOGGER.error(e.getMessage(), e);
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  /**
   * Add a line in the format `key=value` to this Config
   *
   * @param line
   * @throws IllegalArgumentException In the event the line is incorrectly formatted
   */
  private void parseAndAdd(String line) throws IllegalArgumentException {
    Pair<String, String> p = getEntryFromString(line);

    if (p == null)
      return;

    addEntry(p);
  }

  /**
   * Given a line in the format `key=value`, this method discards any comments and
   * returns a Pair<key, value>.
   * @param entry
   * @return
   * @throws IllegalArgumentException
   */
  private Pair<String, String> getEntryFromString(String entry)
      throws IllegalArgumentException {

    // Parse comments, if there are any
    int commentIndex = entry.indexOf(COMMENT);

    if (commentIndex != -1) {
      entry = entry.substring(0, commentIndex);
    }

    if (isBlank(entry)) {
      return null;
    }

    int index = entry.indexOf(EQUAL_SIGN);

    // Throw exception if no `=` found
    if (index == -1) {
      command = entry;
      return null;
    }

    String key = entry.substring(0, index).trim().toUpperCase();
    String value = entry.substring(index + 1, entry.length()).trim();

    return new Pair<>(key, value);
  }

  /**
   * Add an entry with given name and value to the internal map.
   *
   * @param key
   * @param value
   * @return
   */
  private boolean addEntry(String key, String value) {
    if (configValues.containsKey(key)) {
      return false;
    } else {
      configValues.put(key, value);
      return true;
    }
  }

  private boolean isBlank(String e) {
    for (char c : e.toCharArray()) {
      if (!Character.isWhitespace(c))
        return false;
    }

    return true;
  }

  /**
   * Add an entry from a given Pair<key, value> to the internal map.
   *
   * @param val
   * @return
   */
  private boolean addEntry(Pair<String, String> val) {
    return addEntry(val.getKey(), val.getValue());
  }

  public String getCommand() {
    return command;
  }

  /**
   * Get the config value for a specified key
   *
   * @param key
   * @return The retrieved value, of null if not registered
   */
  public String getConfig(String key) {
    return configValues.get(key);
  }

  /**
   * Get the config value for a specified key, or defaults
   *
   * @param key
   * @param def Default value
   * @return The retrieved value, of null if not registered
   */
  public String getConfig(String key, String def) {
    return configValues.getOrDefault(key, def);
  }

  /**
   * Get the integer config value for a specified key
   *
   * @param key
   * @return
   * @throws IllegalArgumentException If specified key is not found
   */
  public int getIntConfig(String key) {
    String s = getConfig(key);

    if (s == null)
      throw new IllegalArgumentException("Key `" + key + "` not found!");

    return Integer.parseInt(s);
  }

  /**
   * Get the integer config value for a specified key, defaulting if not found
   *
   * @param key
   * @param def Default value
   * @return
   * @throws IllegalArgumentException If specified key is not found
   */
  public int getIntConfig(String key, int def) {
    String s = getConfig(key);

    if (s == null)
      return def;

    return Integer.parseInt(s);
  }
}
