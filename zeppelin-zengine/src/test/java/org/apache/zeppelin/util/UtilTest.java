/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.util;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import static org.junit.Assert.assertNotNull;

public class UtilTest {

  @Test
  public void getVersionTest() {
    assertNotNull(Util.getVersion());
  }

  @Test
  public void getGitInfoTest() {
    assertNotNull(Util.getGitCommitId());
    assertNotNull(Util.getGitTimestamp());
  }
  
  @Test
  public void convertTitleToFilenameTest() {
    String emptyTitle = "";
    String simpleTitle = "note A";
    String illegalCharTitle = "n<o>t?e \"AB\"";
    String backslashTitle = "dir1\\note \"AB.C\"";
    String dirStructureTitle = "/NoteDirA/Note1";
    assertThat(Util.convertTitleToFilename(emptyTitle)).isEqualTo("note.zpln");
    assertThat(Util.convertTitleToFilename(simpleTitle)).isEqualTo("note A.zpln");
    assertThat(Util.convertTitleToFilename(illegalCharTitle)).isEqualTo("n o t e  AB .zpln");
    assertThat(Util.convertTitleToFilename(backslashTitle)).isEqualTo("note  AB.C .zpln");
    assertThat(Util.convertTitleToFilename(dirStructureTitle)).isEqualTo("Note1.zpln");
  }
}
