package org.apache.zeppelin.jupyter.nbformat;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 *
 */
public class Metadata {

  @SerializedName("kernelspec")
  private Kernelspec kernelspec;

  @SerializedName("language_info")
  private LanguageInfo languageInfo;

  @SerializedName("orig_nbformat")
  private int origNbformat;

  @SerializedName("title")
  private String title;

  @SerializedName("authors")
  private List<Author> authors;

  public Kernelspec getKernelspec() {
    return kernelspec;
  }

  public LanguageInfo getLanguageInfo() {
    return languageInfo;
  }

  public int getOrigNbformat() {
    return origNbformat;
  }

  public String getTitle() {
    return title;
  }

  public List<Author> getAuthors() {
    return authors;
  }
}
