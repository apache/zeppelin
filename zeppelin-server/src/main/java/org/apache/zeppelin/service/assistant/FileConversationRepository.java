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

package org.apache.zeppelin.service.assistant;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.zeppelin.util.FileUtils;

public final class FileConversationRepository implements ConversationRepository {
  private final File directory;

  public FileConversationRepository(File directory) {
    this.directory = directory;
  }

  @Override
  public List<Conversation> findAll(String noteId) throws IOException {
    File noteDirectory = noteDirectory(noteId);
    if (!noteDirectory.exists()) return Collections.emptyList();
    File[] files = noteDirectory.listFiles((dir, name) -> name.endsWith(".json"));
    if (files == null) throw new IOException();

    List<Conversation> conversations = new ArrayList<>();
    for (File file : files) read(file).ifPresent(conversations::add);
    conversations.sort(Comparator.comparing(c -> Instant.parse(c.getCreatedAt())));
    return Collections.unmodifiableList(conversations);
  }

  @Override
  public Optional<Conversation> find(String noteId, String conversationId) throws IOException {
    return read(file(noteId, conversationId));
  }

  @Override
  public void create(Conversation conversation) throws IOException {
    File file = file(conversation.getNoteId(), conversation.getId());
    if (file.exists()) throw new IllegalStateException();
    commit(file, conversation);
  }

  @Override
  public void update(Conversation entity, Consumer<Conversation> change) throws IOException {
    File file = file(entity.getNoteId(), entity.getId());
    if (!file.exists()) throw new IOException();
    change.accept(entity);
    commit(file, entity);
  }

  @Override
  public void delete(String noteId, String conversationId) throws IOException {
    Files.delete(file(noteId, conversationId).toPath());
  }

  private File noteDirectory(String noteId) throws IOException {
    File noteDirectory = new File(directory, noteId).getCanonicalFile();
    if (!directory.getCanonicalFile().equals(noteDirectory.getParentFile())) throw new IOException();
    return noteDirectory;
  }

  private File file(String noteId, String conversationId) throws IOException {
    File noteDirectory = noteDirectory(noteId);
    File file = new File(noteDirectory, conversationId + ".json").getCanonicalFile();
    if (!noteDirectory.equals(file.getParentFile())) throw new IOException();
    return file;
  }

  private static Optional<Conversation> read(File file) throws IOException {
    try {
      return Optional.of(ConversationJsonCodec.deserialize(Files.readString(file.toPath())));
    } catch (NoSuchFileException e) {
      return Optional.empty();
    }
  }

  private static void commit(File file, Conversation conversation) throws IOException {
    FileUtils.atomicWriteToFile(ConversationJsonCodec.serialize(conversation), file);
  }
}
