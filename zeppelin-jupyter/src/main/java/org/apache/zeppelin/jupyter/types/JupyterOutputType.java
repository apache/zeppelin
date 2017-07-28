package org.apache.zeppelin.jupyter.types;

/**
 * Jupyter Output Types.
 */
public enum JupyterOutputType {
  TEXT_PLAIN("text/plain"),
  IMAGE_PNG("image/png"),
  LATEX("text/latex"),
  SVG_XML("image/svg+xml"),
  TEXT_HTML("text/html"),
  APPLICATION_JAVASCRIPT("application/javascript")
  ;

  private final String type;
  private JupyterOutputType(final String type) {
    this.type = type;
  }

  public ZeppelinOutputType getZeppelinType() {
    return Convertor.ToZeppelin.getType(type);
  }

  public static JupyterOutputType getByValue(String value) {
    for (JupyterOutputType type : JupyterOutputType.values()) {
      if (type.toString().equals(value)) {
        return type;
      }
    }
    return JupyterOutputType.TEXT_PLAIN;
  }

  @Override
  public String toString() {
    return type;
  }

  private enum Convertor {
    ToZeppelin;

    public ZeppelinOutputType getType(String typeValue) {
      JupyterOutputType type = JupyterOutputType.getByValue(typeValue);
      ZeppelinOutputType outputType;

      if (JupyterOutputType.TEXT_PLAIN == type) {
        outputType = ZeppelinOutputType.TEXT;
      } else {
        outputType = ZeppelinOutputType.HTML;
      }

      return outputType;
    }
  }
}
