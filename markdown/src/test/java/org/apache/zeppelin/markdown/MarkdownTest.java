package org.apache.zeppelin.markdown;


import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MarkdownTest {

  Markdown md;

  @Before
  public void setUp() {
    Properties props = new Properties();
    props.put(Markdown.MARKDOWN_PARSER_TYPE, Markdown.PARSER_TYPE_MARKDOWN4J);
    md = new Markdown(props);
    md.open();
  }

  @After
  public void tearDown() {
    md.close();
  }

  @Test
  public void sanitizeInput() {
    String input = "This is "
        + "<script>alert(1);</script> "
        + "<div onclick='alert(2)'>this is div</div> "
        + "text";
    String output = md.sanitizeInput(input);
    assertFalse(output.contains("<script>"));
    assertFalse(output.contains("onclick"));
    assertTrue(output.contains("this is div"));
  }
}
