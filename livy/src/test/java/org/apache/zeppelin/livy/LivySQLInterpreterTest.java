package org.apache.zeppelin.livy;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Properties;
import static org.junit.Assert.*;

/**
 * Unit test for LivySQLInterpreter
 */
public class LivySQLInterpreterTest {

  private LivySparkSQLInterpreter sqlInterpreter;

  @Before
  public void setUp() {
    Properties properties = new Properties();
    properties.setProperty("zeppelin.livy.session.create_timeout", "120");
    properties.setProperty("zeppelin.livy.spark.sql.maxResult", "3");
    sqlInterpreter = new LivySparkSQLInterpreter(properties);
  }

  @Test
  public void testParseSQLOutput() {
    // Empty sql output
    //    +---+---+
    //    |  a|  b|
    //    +---+---+
    //    +---+---+
    List<String> rows = sqlInterpreter.parseSQLOutput("+---+---+\n" +
                                  "|  a|  b|\n" +
                                  "+---+---+\n" +
                                  "+---+---+");
    assertEquals(1, rows.size());
    assertEquals("a\tb", rows.get(0));


    //  sql output with 2 rows
    //    +---+---+
    //    |  a|  b|
    //    +---+---+
    //    |  1| 1a|
    //    |  2| 2b|
    //    +---+---+
    rows = sqlInterpreter.parseSQLOutput("+---+---+\n" +
        "|  a|  b|\n" +
        "+---+---+\n" +
        "|  1| 1a|\n" +
        "|  2| 2b|\n" +
        "+---+---+");
    assertEquals(3, rows.size());
    assertEquals("a\tb", rows.get(0));
    assertEquals("1\t1a", rows.get(1));
    assertEquals("2\t2b", rows.get(2));


    //  sql output with 3 rows and showing "only showing top 3 rows"
    //    +---+---+
    //    |  a|  b|
    //    +---+---+
    //    |  1| 1a|
    //    |  2| 2b|
    //    |  3| 3c|
    //    +---+---+
    rows = sqlInterpreter.parseSQLOutput("+---+---+\n" +
        "|  a|  b|\n" +
        "+---+---+\n" +
        "|  1| 1a|\n" +
        "|  2| 2b|\n" +
        "|  3| 3c|\n" +
        "+---+---+");
    assertEquals(4, rows.size());
    assertEquals("a\tb", rows.get(0));
    assertEquals("1\t1a", rows.get(1));
    assertEquals("2\t2b", rows.get(2));
    assertEquals("3\t3c", rows.get(3));
  }
}
