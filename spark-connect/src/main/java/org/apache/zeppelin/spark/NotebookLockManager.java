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

package org.apache.zeppelin.spark;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Shared utility class for managing notebook-level locks.
 * Ensures that only one query executes at a time per notebook,
 * regardless of which interpreter (SparkConnectInterpreter or SparkConnectSqlInterpreter) is used.
 */
public class NotebookLockManager {
  
  // Locks per notebook to ensure one query at a time per notebook
  private static final ConcurrentHashMap<String, ReentrantLock> notebookLocks = 
      new ConcurrentHashMap<>();
  
  /**
   * Get or create a lock for the specified notebook.
   * Uses fair locking to ensure FIFO ordering of query execution.
   * 
   * @param noteId The notebook ID
   * @return The lock for this notebook
   */
  public static ReentrantLock getNotebookLock(String noteId) {
    return notebookLocks.computeIfAbsent(noteId, 
        k -> new ReentrantLock(true)); // Fair lock for FIFO ordering
  }
  
  /**
   * Remove the lock for a notebook (cleanup when notebook is closed).
   * 
   * @param noteId The notebook ID
   */
  public static void removeNotebookLock(String noteId) {
    notebookLocks.remove(noteId);
  }
  
  /**
   * Get the number of active notebook locks (for monitoring/debugging).
   * 
   * @return The number of active locks
   */
  public static int getActiveLockCount() {
    return notebookLocks.size();
  }
}
