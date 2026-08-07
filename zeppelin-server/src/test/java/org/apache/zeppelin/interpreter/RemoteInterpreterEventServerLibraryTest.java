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

package org.apache.zeppelin.interpreter;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.interpreter.thrift.LibraryMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RemoteInterpreterEventServerLibraryTest {

  private static final String INTERPRETER = "spark";

  @TempDir
  Path temporaryDirectory;

  private Path dependencyRepository;
  private InterpreterSettingManager interpreterSettingManager;
  private RemoteInterpreterEventServer server;

  @BeforeEach
  void setUp() throws IOException {
    dependencyRepository = Files.createDirectory(temporaryDirectory.resolve("dependencies"));
    Files.createDirectory(dependencyRepository.resolve(INTERPRETER));

    ZeppelinConfiguration zConf = ZeppelinConfiguration.load();
    zConf.setProperty(
        ConfVars.ZEPPELIN_DEP_LOCALREPO.getVarName(), dependencyRepository.toString());

    interpreterSettingManager = mock(InterpreterSettingManager.class);
    InterpreterSetting interpreterSetting = mock(InterpreterSetting.class);
    when(interpreterSetting.getId()).thenReturn(INTERPRETER);
    when(interpreterSettingManager.getInterpreterSettingByName(INTERPRETER))
        .thenReturn(interpreterSetting);
    server = new RemoteInterpreterEventServer(zConf, interpreterSettingManager);
  }

  @Test
  void readsRegisteredInterpreterJarAndListsItsMetadata() throws Exception {
    byte[] expected = {1, 2, 3, 4};
    Files.write(dependencyRepository.resolve(INTERPRETER).resolve("library.jar"), expected);

    ByteBuffer library = server.getLibrary(INTERPRETER, "library.jar");
    assertArrayEquals(expected, library.array());

    List<LibraryMetadata> metadata = server.getAllLibraryMetadatas(INTERPRETER);
    assertEquals(1, metadata.size());
    assertEquals("library.jar", metadata.get(0).getName());
  }

  @Test
  void resolvesRepositoryFromRegisteredSettingId() throws Exception {
    String settingName = "spark-display-name";
    String settingId = "spark-setting-id";
    Path settingRepository = Files.createDirectory(dependencyRepository.resolve(settingId));
    byte[] expected = {5, 6, 7};
    Files.write(settingRepository.resolve("library.jar"), expected);
    InterpreterSetting interpreterSetting = mock(InterpreterSetting.class);
    when(interpreterSetting.getId()).thenReturn(settingId);
    when(interpreterSettingManager.getInterpreterSettingByName(settingName))
        .thenReturn(interpreterSetting);

    ByteBuffer library = server.getLibrary(settingName, "library.jar");
    assertArrayEquals(expected, library.array());
  }

  @Test
  void rejectsTraversalAbsoluteAndMultiSegmentInterpreterPaths() throws Exception {
    Path outsideDirectory = Files.createDirectory(temporaryDirectory.resolve("outside"));
    Files.write(outsideDirectory.resolve("library.jar"), new byte[] {9});

    assertNull(server.getLibrary("../../../../etc", "passwd"));

    List<String> invalidInterpreters = List.of(
        "../outside",
        outsideDirectory.toString(),
        "spark/child",
        "spark\\child",
        ".",
        "..");
    for (String invalidInterpreter : invalidInterpreters) {
      assertNull(server.getLibrary(invalidInterpreter, "library.jar"), invalidInterpreter);
      assertTrue(
          server.getAllLibraryMetadatas(invalidInterpreter).isEmpty(), invalidInterpreter);
    }
  }

  @Test
  void rejectsTraversalAbsoluteAndMultiSegmentLibraryPaths() throws Exception {
    Path outsideLibrary = Files.write(
        temporaryDirectory.resolve("outside.jar"), new byte[] {9});

    List<String> invalidLibraries = List.of(
        "../outside.jar",
        outsideLibrary.toString(),
        "nested/library.jar",
        "nested\\library.jar",
        ".",
        "..");
    for (String invalidLibrary : invalidLibraries) {
      assertNull(server.getLibrary(INTERPRETER, invalidLibrary), invalidLibrary);
    }
  }

  @Test
  void rejectsUnregisteredInterpreterNonJarAndDirectory() throws Exception {
    Path interpreterRepository = dependencyRepository.resolve(INTERPRETER);
    Files.write(interpreterRepository.resolve("library.txt"), new byte[] {1});
    Files.createDirectory(interpreterRepository.resolve("directory.jar"));
    Path unregisteredRepository = Files.createDirectory(
        dependencyRepository.resolve("unregistered"));
    Files.write(unregisteredRepository.resolve("library.jar"), new byte[] {1});

    assertNull(server.getLibrary("unregistered", "library.jar"));
    assertTrue(server.getAllLibraryMetadatas("unregistered").isEmpty());
    assertNull(server.getLibrary(INTERPRETER, "library.txt"));
    assertNull(server.getLibrary(INTERPRETER, "directory.jar"));
    assertTrue(server.getAllLibraryMetadatas(INTERPRETER).isEmpty());
  }

  @Test
  void rejectsLibraryAndInterpreterSymlinkEscapes() throws Exception {
    Path outsideDirectory = Files.createDirectory(temporaryDirectory.resolve("outside"));
    Path outsideLibrary = Files.write(outsideDirectory.resolve("outside.jar"), new byte[] {9});
    Files.createSymbolicLink(
        dependencyRepository.resolve(INTERPRETER).resolve("library.jar"), outsideLibrary);

    assertNull(server.getLibrary(INTERPRETER, "library.jar"));
    assertTrue(server.getAllLibraryMetadatas(INTERPRETER).isEmpty());

    String linkedInterpreter = "linked-interpreter";
    Files.createSymbolicLink(dependencyRepository.resolve(linkedInterpreter), outsideDirectory);
    InterpreterSetting interpreterSetting = mock(InterpreterSetting.class);
    when(interpreterSetting.getId()).thenReturn(linkedInterpreter);
    when(interpreterSettingManager.getInterpreterSettingByName(linkedInterpreter))
        .thenReturn(interpreterSetting);
    assertNull(server.getLibrary(linkedInterpreter, "outside.jar"));
    assertTrue(server.getAllLibraryMetadatas(linkedInterpreter).isEmpty());
  }
}
