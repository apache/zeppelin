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
package org.apache.zeppelin.springxd;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link AbstractSpringXdResourceManager}
 */
public class AbstractSpringXdResourceManagerTest {

  private String noteId;
  private String paragraphId;
  private String resourceName;
  private String resourceDefininition;
  private AbstractSpringXdResourceManager drm;

  @Before
  public void before() {
    noteId = "noteId";
    paragraphId = "paragraphId";
    resourceName = "resourceName";
    resourceDefininition = "resourceDefininition";

    drm = spy(AbstractSpringXdResourceManager.class);
  }

  @Test
  public void testDeployResource() {

    doNothing().when(drm).doCreateResource(eq(resourceName), eq(resourceDefininition));

    drm.deployResource(noteId, paragraphId, resourceName, resourceDefininition);

    verify(drm).doCreateResource(eq(resourceName), eq(resourceDefininition));

    List<String> deployedResources = drm.getDeployedResourceBy(noteId, paragraphId);

    assertEquals(1, deployedResources.size());
    assertEquals(Arrays.asList(resourceName), deployedResources);
  }

  @Test
  public void testDeployResourceBlankResourceName() {
    testDeployResourceBlankResources("", resourceDefininition);
    testDeployResourceBlankResources(null, resourceDefininition);
    testDeployResourceBlankResources(resourceName, "");
    testDeployResourceBlankResources(resourceName, null);
    testDeployResourceBlankResources("", "");
    testDeployResourceBlankResources(null, null);
  }

  private void testDeployResourceBlankResources(String name, String definition) {

    drm.deployResource(noteId, paragraphId, name, definition);
    verify(drm, never()).doCreateResource(anyString(), anyString());

    List<String> deployedResources = drm.getDeployedResourceBy(noteId, paragraphId);
    assertEquals(0, deployedResources.size());
  }

  @Test
  public void testDestroyResourceByNoteParagraph() {
    deployResource(noteId, paragraphId, resourceName, resourceDefininition);
    assertEquals(1, drm.getDeployedResourceBy(noteId, paragraphId).size());

    doNothing().when(drm).doDestroyRsource(eq(resourceName));

    drm.destroyDeployedResourceBy(noteId, paragraphId);
    assertEquals(0, drm.getDeployedResourceBy(noteId, paragraphId).size());

    verify(drm).doDestroyRsource(eq(resourceName));
  }

  @Test
  public void testDestroyResourceByNoteMultipleParagraphs() {

    deployResource(noteId, paragraphId, resourceName, resourceDefininition);
    assertEquals(1, drm.getDeployedResourceBy(noteId, paragraphId).size());

    deployResource(noteId, paragraphId + "1", resourceName + "1", resourceDefininition + "1");
    assertEquals(1, drm.getDeployedResourceBy(noteId, paragraphId + "1").size());

    doNothing().when(drm).doDestroyRsource(eq(resourceName));

    drm.destroyDeployedResourceBy(noteId, paragraphId);

    assertEquals(0, drm.getDeployedResourceBy(noteId, paragraphId).size());
    assertEquals(1, drm.getDeployedResourceBy(noteId, paragraphId + "1").size());

    verify(drm).doDestroyRsource(eq(resourceName));
  }

  @Test
  public void testDestroyResourceByNote() {

    deployResource(noteId, paragraphId, resourceName, resourceDefininition);
    assertEquals(1, drm.getDeployedResourceBy(noteId, paragraphId).size());

    deployResource(noteId, paragraphId + "1", resourceName + "1", resourceDefininition + "1");
    assertEquals(1, drm.getDeployedResourceBy(noteId, paragraphId + "1").size());

    doNothing().when(drm).doDestroyRsource(eq(resourceName));
    doNothing().when(drm).doDestroyRsource(eq(resourceName + "1"));

    drm.destroyDeployedResourceBy(noteId);

    assertEquals(0, drm.getDeployedResourceBy(noteId, paragraphId).size());
    assertEquals(0, drm.getDeployedResourceBy(noteId, paragraphId + "1").size());

    verify(drm).doDestroyRsource(eq(resourceName));
    verify(drm).doDestroyRsource(eq(resourceName + "1"));
  }

  @Test
  public void testDestroyResourceByNoteMultipleNotes() {

    deployResource(noteId, paragraphId, resourceName, resourceDefininition);
    assertEquals(1, drm.getDeployedResourceBy(noteId, paragraphId).size());

    deployResource(noteId + "1", paragraphId, resourceName + "1", resourceDefininition);
    assertEquals(1, drm.getDeployedResourceBy(noteId + "1", paragraphId).size());

    doNothing().when(drm).doDestroyRsource(eq(resourceName));

    drm.destroyDeployedResourceBy(noteId);

    assertEquals(0, drm.getDeployedResourceBy(noteId, paragraphId).size());
    assertEquals(1, drm.getDeployedResourceBy(noteId + "1", paragraphId).size());

    verify(drm).doDestroyRsource(eq(resourceName));
  }
  
  @Test
  public void testDestroyAllResources() {

    deployResource(noteId, paragraphId, resourceName, resourceDefininition);
    assertEquals(1, drm.getDeployedResourceBy(noteId, paragraphId).size());

    deployResource(noteId + "1", paragraphId, resourceName + "1", resourceDefininition);
    assertEquals(1, drm.getDeployedResourceBy(noteId + "1", paragraphId).size());

    doNothing().when(drm).doDestroyRsource(eq(resourceName));
    doNothing().when(drm).doDestroyRsource(eq(resourceName + "1"));

    drm.destroyAllNotebookDeployedResources();

    assertEquals(0, drm.getDeployedResourceBy(noteId, paragraphId).size());
    assertEquals(0, drm.getDeployedResourceBy(noteId + "1", paragraphId).size());

    verify(drm).doDestroyRsource(eq(resourceName));
    verify(drm).doDestroyRsource(eq(resourceName + "1"));
  }

  private void deployResource(String noteId, String paragraphId, String resourceName,
      String resourceDefinition) {
    
    doNothing().when(drm).doCreateResource(eq(resourceName), eq(resourceDefininition));

    drm.deployResource(noteId, paragraphId, resourceName, resourceDefininition);

    verify(drm).doCreateResource(eq(resourceName), eq(resourceDefininition));
  }
}
