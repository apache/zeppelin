package org.apache.zeppelin.jupyter.types;

/**
 * Jupyter Output Types.
 */
public enum JupyterOutputType {
  TEXT_PLAIN("text/plain"),
  IMAGE_PNG("image/png"),
  LATEX("text/latex")
  ;

  private final String type;
  private JupyterOutputType(final String type) {
    this.type = type;
  }

  public ZeppelinOutputType getZeppelinType() {
    return Convertor.ToZeppelin.getType(type);
  }

  @Override
  public String toString() {
    return type;
  }

  private enum Convertor {
    ToZeppelin;

    public ZeppelinOutputType getType(String typeValue) {
      JupyterOutputType type = JupyterOutputType.valueOf(typeValue);
      ZeppelinOutputType outputType;

      if (JupyterOutputType.TEXT_PLAIN == type) {
        outputType = ZeppelinOutputType.TEXT;
      } else if (JupyterOutputType.IMAGE_PNG == type) {
        outputType = ZeppelinOutputType.HTML;
      } else if (JupyterOutputType.LATEX == type) {
        outputType = ZeppelinOutputType.HTML;
      } else {
        outputType = ZeppelinOutputType.TEXT;
      }

      return outputType;
    }
  }
}
