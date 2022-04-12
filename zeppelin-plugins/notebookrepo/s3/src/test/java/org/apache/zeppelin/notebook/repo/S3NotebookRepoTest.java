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

package org.apache.zeppelin.notebook.repo;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.gaul.s3proxy.junit.S3ProxyRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class S3NotebookRepoTest {

  private AuthenticationInfo anonymous = AuthenticationInfo.ANONYMOUS;
  private S3NotebookRepo notebookRepo;

  @Rule
  public S3ProxyRule s3Proxy = S3ProxyRule.builder()
          .withCredentials("access", "secret")
          .build();


  @Before
  public void setUp() throws IOException {
    String bucket = "test-bucket";
    notebookRepo = new S3NotebookRepo();
    ZeppelinConfiguration conf = ZeppelinConfiguration.create();
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_S3_ENDPOINT.getVarName(),
            s3Proxy.getUri().toString());
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_S3_BUCKET.getVarName(),
            bucket);
    System.setProperty("aws.accessKeyId", s3Proxy.getAccessKey());
    System.setProperty("aws.secretKey", s3Proxy.getSecretKey());

    notebookRepo.init(conf);

    // create bucket for notebook
    AmazonS3 s3Client = AmazonS3ClientBuilder
            .standard()
            .withCredentials(
                    new AWSStaticCredentialsProvider(
                            new BasicAWSCredentials(s3Proxy.getAccessKey(),
                                    s3Proxy.getSecretKey())))
            .withEndpointConfiguration(
                    new AwsClientBuilder.EndpointConfiguration(s3Proxy.getUri().toString(),
                            Regions.US_EAST_1.getName()))
            .build();
    s3Client.createBucket(bucket);
  }

  @After
  public void tearDown() {
    if (notebookRepo != null) {
      notebookRepo.close();
    }
  }

  @Test
  public void testNotebookRepo() throws IOException {
    Map<String, NoteInfo> notesInfo = notebookRepo.list(anonymous);
    assertEquals(0, notesInfo.size());

    // create Note note1
    Note note1 = new Note();
    note1.setPath("/spark/note_1");
    notebookRepo.save(note1, anonymous);

    notesInfo = notebookRepo.list(anonymous);
    assertEquals(1, notesInfo.size());
    assertEquals("/spark/note_1", notesInfo.get(note1.getId()).getPath());

    // Get note1
    Note noteFromRepo = notebookRepo.get(note1.getId(), note1.getPath(), anonymous);
    assertEquals(note1.getName(), noteFromRepo.getName());

    // Get non-existed note
    try {
      notebookRepo.get("invalid_id", "/invalid_path", anonymous);
      fail("Should fail to get non-existed note1");
    } catch (IOException e) {
      assertEquals("Fail to get note: /invalid_path from S3", e.getMessage());
    }

    // create another Note note2
    Note note2 = new Note();
    note2.setPath("/spark/note_2");
    notebookRepo.save(note2, anonymous);

    notesInfo = notebookRepo.list(anonymous);
    assertEquals(2, notesInfo.size());
    assertEquals("/spark/note_1", notesInfo.get(note1.getId()).getPath());
    assertEquals("/spark/note_2", notesInfo.get(note2.getId()).getPath());

    // move note1
    notebookRepo.move(note1.getId(), note1.getPath(), "/spark2/note_1", anonymous);

    notesInfo = notebookRepo.list(anonymous);
    assertEquals(2, notesInfo.size());
    assertEquals("/spark2/note_1", notesInfo.get(note1.getId()).getPath());
    assertEquals("/spark/note_2", notesInfo.get(note2.getId()).getPath());

    // move folder
    notebookRepo.move("/spark2", "/spark3", anonymous);

    notesInfo = notebookRepo.list(anonymous);
    assertEquals(2, notesInfo.size());
    assertEquals("/spark3/note_1", notesInfo.get(note1.getId()).getPath());
    assertEquals("/spark/note_2", notesInfo.get(note2.getId()).getPath());

    // delete note
    notebookRepo.remove(note1.getId(), notesInfo.get(note1.getId()).getPath(), anonymous);
    notesInfo = notebookRepo.list(anonymous);
    assertEquals(1, notesInfo.size());
    assertEquals("/spark/note_2", notesInfo.get(note2.getId()).getPath());

    // delete folder
    notebookRepo.remove("/spark", anonymous);
    notesInfo = notebookRepo.list(anonymous);
    assertEquals(0, notesInfo.size());
  }
}
