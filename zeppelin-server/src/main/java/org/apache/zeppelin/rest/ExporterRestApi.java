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

package org.apache.zeppelin.rest;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.rest.message.*;
import org.apache.zeppelin.search.SearchService;
import org.apache.zeppelin.server.JsonResponse;
import org.apache.zeppelin.socket.NotebookServer;
import org.quartz.CronExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Rest api endpoint for the noteBook.
 */
@Path("/export")
@Produces("application/text")
public class ExporterRestApi {
  private static final Logger LOG = LoggerFactory.getLogger(ExporterRestApi.class);
  private Notebook notebook;

  public ExporterRestApi() {}

  public ExporterRestApi(Notebook notebook) {
    this.notebook = notebook;
  }

  /**
   * Run paragraph job and return the results
   *
   * @return Text with status code
   * @throws IOException, IllegalArgumentException
   */
  @GET
  @Path("job/runThenExportTSV/{notebookId}/paragraph/{paragraphId}-export.tsv")
  public Response runThenExportTSV(@PathParam("notebookId") String notebookId,
                                   @PathParam("paragraphId") String paragraphId) throws
          IOException, IllegalArgumentException {
    LOG.info("running TSV export of {} {} {}", notebookId, paragraphId);

    Note note = notebook.getNote(notebookId);
    if (note == null) {
      return new JsonResponse<>(Status.NOT_FOUND, "note not found.").build();
    }

    Paragraph paragraph = note.getParagraph(paragraphId);
    if (paragraph == null) {
      return new JsonResponse<>(Status.NOT_FOUND, "paragraph not found.").build();
    }

    LOG.info("running job.");
    InterpreterResult result = note.runSynchronously(paragraph.getId());
    LOG.info("Length of result returned by query: {}",
            result.message() == null ? "null result" : result.message().length());

    return Response.ok(result.message()).build();
  }

}
