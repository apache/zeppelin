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
package org.apache.zeppelin.resource;

/**
 * Interface for ResourcePool
 */
public interface ResourcePool {
  /**
   * Get unique id of the resource pool
   * @return
   */
  public String id();

  /**
   * Get resource from name
   * @param name Resource name
   * @return null if resource not found
   */
  public Resource get(String name);

  /**
   * Get resource from name
   * @param noteId
   * @param paragraphId
   * @param name Resource name
   * @return null if resource not found
   */
  public Resource get(String noteId, String paragraphId, String name);

  /**
   * Get all resources
   * @return
   */
  public ResourceSet getAll();

  /**
   * Put an object into resource pool
   * @param name
   * @param object
   */
  public void put(String name, Object object);

  /**
   * Put an object into resource pool
   * Given noteId and paragraphId is identifying resource along with name.
   * Object will be automatically removed on related note or paragraph removal.
   *
   * @param noteId
   * @param paragraphId
   * @param name
   * @param object
   */
  public void put(String noteId, String paragraphId, String name, Object object);

  /**
   * Remove object
   * @param name Resource name to remove
   * @return removed Resource. null if resource not found
   */
  public Resource remove(String name);

  /**
   * Remove object
   * @param noteId
   * @param paragraphId
   * @param name Resource name to remove
   * @return removed Resource. null if resource not found
   */
  public Resource remove(String noteId, String paragraphId, String name);
}
