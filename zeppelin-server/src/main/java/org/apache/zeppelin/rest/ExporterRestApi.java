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

import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.server.JsonResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;

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
   * Run paragraph job and return the results as a CSV file
   *
   * @return Text with status code
   * @throws IOException, IllegalArgumentException
   */
  @GET
  @Produces("text/tab-separated-values")
  @Path("job/runThenExportCSV/{notebookId}/paragraph/{paragraphId}-export.csv")
  public Response runThenExportTSV(@PathParam("notebookId") String notebookId,
                                   @PathParam("paragraphId") String paragraphId) throws
          IOException, IllegalArgumentException {
    LOG.info("running CSV export of {} {}", notebookId, paragraphId);

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

    return Response.ok(TsvToCSV.toCSV(result.message())).build();
  }

  /**
   * Run paragraph job and return the results as a Tableau WDC document
   *
   * @return Text with status code
   * @throws IOException, IllegalArgumentException
   */
  @GET
  @Produces("text/html")
  @Path("job/runThenExportWDC/{notebookId}/paragraph/{paragraphId}-export.html")
  public Response runThenExportWDC(@PathParam("notebookId") String notebookId,
                                   @PathParam("paragraphId") String paragraphId) throws
          IOException, IllegalArgumentException {
    LOG.info("running WDC export of {} {}", notebookId, paragraphId);

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

    String exportName = paragraph.getTitle() != null ? paragraph.getTitle() : paragraph.getId();
    String wdcHtml = Tableau.buildWDCResult(result.message(), exportName);
    return Response.ok(wdcHtml).build();
  }
}
