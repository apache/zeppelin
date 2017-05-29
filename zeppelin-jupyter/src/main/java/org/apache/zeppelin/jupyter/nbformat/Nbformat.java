package org.apache.zeppelin.jupyter.nbformat;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 *
 */
public class Nbformat {

  @SerializedName("metadata")
  private Metadata metadata;

  @SerializedName("nbformat")
  private int nbformat;

  @SerializedName("nbformat_minor")
  private int nbformatMinor;

  @SerializedName("cells")
  private List<Cell> cells;

  public Metadata getMetadata() {
    return metadata;
  }

  public int getNbformat() {
    return nbformat;
  }

  public int getNbformatMinor() {
    return nbformatMinor;
  }

  public List<Cell> getCells() {
    return cells;
  }
}
