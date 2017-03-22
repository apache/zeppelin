/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.zeppelin.completer;

import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import jline.console.completer.Completer;
import jline.internal.Preconditions;

/**
 * Case-insensitive completer for a set of strings.
 */
public class StringsCompleter implements Completer {
  private final SortedSet<String> strings = new TreeSet<String>();


  public StringsCompleter() {
  }

  public StringsCompleter(final Collection<String> strings) {
    Preconditions.checkNotNull(strings);
    getStrings().addAll(strings);
  }

  public Collection<String> getStrings() {
    return strings;
  }

  public int complete(final String buffer, final int cursor, final List<CharSequence> candidates) {
    Preconditions.checkNotNull(candidates);

    if (buffer == null) {
      candidates.addAll(strings);
    }
    else {
      String bufferTmp = buffer.toUpperCase();
      for (String match : strings.tailSet(buffer)) {
        String matchTmp = match.toUpperCase();
        if (!matchTmp.startsWith(bufferTmp)) {
          break;
        }

        candidates.add(match);
      }
    }

    if (candidates.size() == 1) {
      candidates.set(0, candidates.get(0) + " ");
    }

    return candidates.isEmpty() ? -1 : 0;
  }
}
