package org.apache.zeppelin.antlr;

import com.google.gson.Gson;
import org.junit.Test;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class SqlSplitVisitorTest {
  @Test
  public void testSplitSql() {
    String text = "select id,name from (\n" +
            "select id,name from table1\n" +
            "union all\n" +
            "select id,name from table2\n" +
            "union all\n" +
            "select id,name from table3\n" +
            "union all\n" +
            "select id,name from table4\n" +
            ")t group by id,name";
    String paragraphId = "paragraph_1683516605896_1391976205";
    List<String> tableSqlList= SqlSplitVisitor.splitSql(text,paragraphId);
    assertEquals(5, tableSqlList.size());
    assertEquals("CREATE DATABASE IF NOT EXISTS test_zeppelin; use test_zeppelin;create table test_zeppelin.tmp_zeppelin_paragraph_1683516605896_1391976205_0 as select id,name from (\n" +
            "select id,name from table1\n" +
            "union all\n" +
            "select id,name from table2\n" +
            "union all\n" +
            "select id,name from table3\n" +
            "union all\n" +
            "select id,name from table4\n" +
            ")t group by id,name", tableSqlList.get(0));
    assertEquals("CREATE DATABASE IF NOT EXISTS test_zeppelin; use test_zeppelin;create table test_zeppelin.tmp_zeppelin_paragraph_1683516605896_1391976205_1 as select id,name from table1", tableSqlList.get(1));
    assertEquals("CREATE DATABASE IF NOT EXISTS test_zeppelin; use test_zeppelin;create table test_zeppelin.tmp_zeppelin_paragraph_1683516605896_1391976205_2 as select id,name from table2", tableSqlList.get(2));
    assertEquals("CREATE DATABASE IF NOT EXISTS test_zeppelin; use test_zeppelin;create table test_zeppelin.tmp_zeppelin_paragraph_1683516605896_1391976205_3 as select id,name from table3", tableSqlList.get(3));
    assertEquals("CREATE DATABASE IF NOT EXISTS test_zeppelin; use test_zeppelin;create table test_zeppelin.tmp_zeppelin_paragraph_1683516605896_1391976205_4 as select id,name from table4", tableSqlList.get(4));
  }
}
