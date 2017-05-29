package org.apache.zeppelin.jupyter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;
import java.io.Reader;
import org.apache.http.nio.entity.NByteArrayEntity;
import org.apache.zeppelin.jupyter.nbformat.Cell;
import org.apache.zeppelin.jupyter.nbformat.CodeCell;
import org.apache.zeppelin.jupyter.nbformat.DisplayData;
import org.apache.zeppelin.jupyter.nbformat.Error;
import org.apache.zeppelin.jupyter.nbformat.ExecuteResult;
import org.apache.zeppelin.jupyter.nbformat.MarkdownCell;
import org.apache.zeppelin.jupyter.nbformat.Nbformat;
import org.apache.zeppelin.jupyter.nbformat.Output;
import org.apache.zeppelin.jupyter.nbformat.RawCell;
import org.apache.zeppelin.jupyter.nbformat.Stream;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.user.AuthenticationInfo;

/**
 *
 */
public class JupyterUtil {

  private final RuntimeTypeAdapterFactory<Cell> cellTypeFactory;
  private final RuntimeTypeAdapterFactory<Output> outputTypeFactory;

  public JupyterUtil() {
    this.cellTypeFactory = RuntimeTypeAdapterFactory.of(Cell.class, "cell_type")
        .registerSubtype(MarkdownCell.class, "markdown").registerSubtype(CodeCell.class, "code")
        .registerSubtype(RawCell.class, "raw");
    this.outputTypeFactory = RuntimeTypeAdapterFactory.of(Output.class, "output_type")
        .registerSubtype(ExecuteResult.class, "execute_result")
        .registerSubtype(DisplayData.class, "display_data")
        .registerSubtype(Stream.class, "stream").registerSubtype(Error.class, "error");
  }

  public Nbformat getNbformat(Reader in) {
    return getNbformat(in, new GsonBuilder());
  }

  public Nbformat getNbformat(Reader in, GsonBuilder gsonBuilder) {
    return getGson(gsonBuilder).fromJson(in, Nbformat.class);
  }

  private Gson getGson(GsonBuilder gsonBuilder) {
    return gsonBuilder.registerTypeAdapterFactory(cellTypeFactory)
        .registerTypeAdapterFactory(outputTypeFactory).create();
  }

  public static void main(String[] args) {

  }
}
