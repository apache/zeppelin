package org.apache.zeppelin.jupyter.nbformat;

import com.google.gson.annotations.SerializedName;

/**
 *
 */
public class LanguageInfo {

  @SerializedName("name")
  private String name;

  @SerializedName("codemirror_mode")
  private Object codemirrorMode;

  @SerializedName("file_extension")
  private String fileExtension;

  @SerializedName("mimetype")
  private String mimetype;

  @SerializedName("pygments_lexer")
  private String pygmentsLexer;

}
