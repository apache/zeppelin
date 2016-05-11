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
package org.apache.zeppelin.cassandra

import com.datastax.driver.core._
import org.apache.zeppelin.interpreter.InterpreterException
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import org.apache.zeppelin.cassandra.ParagraphParser._
import org.apache.zeppelin.cassandra.TextBlockHierarchy._

import scala.Option

class ParagraphParserTest extends FlatSpec
  with BeforeAndAfterEach
  with Matchers
  with MockitoSugar {

  val session: Session = mock[Session]
  val preparedStatements:collection.mutable.Map[String,PreparedStatement] = collection.mutable.Map()
  val parser: ParagraphParser = new ParagraphParser()


  "Parser" should "parse mixed statements" in {
    val query: String = """
        SELECT * FROM albums LIMIT 10;

        begin UnLoGgEd BATCH
        INSERT INTO users(id) VALUES(10);
        @bind[test]='a',12.34
        apply Batch;

        SELECT * FROM users LIMIT 10;

        BEGIN BATCH
        Insert INTO users(id) VALUES(11);
        INSERT INTO users(id) VALUES(12);
        APPLY BATCH;

        @bind[toto]='a',12.34

        desc table zeppelin.users;
        describe keyspace zeppelin;
      """.stripMargin

    val parsed = parser.parse(parser.queries,query)

    parsed should matchPattern {
      case parser.Success(List(
        SimpleStm("SELECT * FROM albums LIMIT 10;"),
        BatchStm(BatchStatement.Type.UNLOGGED,
          List(
            SimpleStm("INSERT INTO users(id) VALUES(10);"),
            BoundStm("test","'a',12.34")
          )
        ),
        SimpleStm("SELECT * FROM users LIMIT 10;"),
        BatchStm(BatchStatement.Type.LOGGED,
          List(
            SimpleStm("Insert INTO users(id) VALUES(11);"),
            SimpleStm("INSERT INTO users(id) VALUES(12);")
          )
        ),
        BoundStm("toto","'a',12.34"),
        DescribeTableCmd(Some("zeppelin"),"users"),
        DescribeKeyspaceCmd("zeppelin")
      ),_) =>
    }
  }

  "Parser" should "parse hash single-line comment" in {
    val query :CharSequence="""#This is a comment""".stripMargin

    val parsed = parser.parseAll[Comment](parser.singleLineComment, query)
    parsed should matchPattern {
      case parser.Success(Comment("This is a comment"), _) =>
    }
  }

  "Parser" should "parse double slashes single-line comment" in {
    val query :CharSequence="""//This is another comment""".stripMargin

    val parsed = parser.parseAll[Comment](parser.singleLineComment, query)
    parsed should matchPattern {
      case parser.Success(Comment("This is another comment"), _) =>
    }
  }

  "Parser" should "parse multi-line comment" in {
    val query:String =
      """/*This is a comment
        |line1
        |line2
        |line3
        |*/
      """.stripMargin

    val parsed = parser.parseAll(parser.multiLineComment, query)
    parsed should matchPattern {
      case parser.Success(Comment("This is a comment\nline1\nline2\nline3\n"), _) =>
    }
  }

  "Parser" should "parse consistency level" in {
    val query:String =""" @consistency=ONE""".stripMargin
    val parsed = parser.parseAll(parser.consistency, query)
    parsed should matchPattern {
      case parser.Success(Consistency(ConsistencyLevel.ONE), _) =>
    }
  }

  "Parser" should "fails parsing unknown consistency level" in {
    val query:String =""" @consistency=TEST""".stripMargin
    val ex = intercept[InterpreterException] {
      parser.parseAll(parser.consistency, query)
    }
    ex.getMessage should be(s"Invalid syntax for @consistency. It should comply to the pattern ${CONSISTENCY_LEVEL_PATTERN.toString}")
  }

  "Parser" should "parse serial consistency level" in {
    val query:String =""" @serialConsistency=LOCAL_SERIAL""".stripMargin
    val parsed = parser.parseAll(parser.serialConsistency, query)
    parsed should matchPattern {
      case parser.Success(SerialConsistency(ConsistencyLevel.LOCAL_SERIAL), _) =>
    }
  }

  "Parser" should "fails parsing unknown serial consistency level" in {
    val query:String =""" @serialConsistency=TEST""".stripMargin
    val ex = intercept[InterpreterException] {
      parser.parseAll(parser.serialConsistency, query)
    }
    ex.getMessage should be(s"Invalid syntax for @serialConsistency. It should comply to the pattern ${SERIAL_CONSISTENCY_LEVEL_PATTERN.toString}")
  }

  "Parser" should "parse timestamp" in {
    val query:String =""" @timestamp=111""".stripMargin
    val parsed = parser.parseAll(parser.timestamp, query)
    parsed should matchPattern {
      case parser.Success(Timestamp(111L), _) => }
  }

  "Parser" should "fails parsing invalid timestamp" in {
    val query:String =""" @timestamp=TEST""".stripMargin
    val ex = intercept[InterpreterException] {
      parser.parseAll(parser.timestamp, query)
    }
    ex.getMessage should be(s"Invalid syntax for @timestamp. It should comply to the pattern ${TIMESTAMP_PATTERN.toString}")
  }

  "Parser" should "parse retry policy" in {
    val query:String ="@retryPolicy="+CassandraInterpreter.DOWNGRADING_CONSISTENCY_RETRY
    val parsed = parser.parseAll(parser.retryPolicy, query)
    parsed should matchPattern {case parser.Success(DowngradingRetryPolicy, _) => }
  }

  "Parser" should "fails parsing invalid retry policy" in {
    val query:String =""" @retryPolicy=TEST""".stripMargin
    val ex = intercept[InterpreterException] {
      parser.parseAll(parser.retryPolicy, query)
    }
    ex.getMessage should be(s"Invalid syntax for @retryPolicy. It should comply to the pattern ${RETRY_POLICIES_PATTERN.toString}")
  }

  "Parser" should "parse fetch size" in {
    val query:String ="@fetchSize=100"
    val parsed = parser.parseAll(parser.fetchSize, query)
    parsed should matchPattern { case parser.Success(FetchSize(100), _) =>}
  }

  "Parser" should "parse request timeout" in {
    val query:String ="@requestTimeOut=100"
    val parsed = parser.parseAll(parser.requestTimeOut, query)
    parsed should matchPattern { case parser.Success(RequestTimeOut(100), _) =>}
  }

  "Parser" should "fails parsing invalid fetch size" in {
    val query:String =""" @fetchSize=TEST""".stripMargin
    val ex = intercept[InterpreterException] {
      parser.parseAll(parser.fetchSize, query)
    }
    ex.getMessage should be(s"Invalid syntax for @fetchSize. It should comply to the pattern ${FETCHSIZE_PATTERN.toString}")
  }

  "Parser" should "parse simple statement" in {
    //Given
    val query:String =""" sElecT * FROM users LIMIT ? ;""".stripMargin

    //When
    val parsed = parser.parseAll(parser.genericStatement, query)

    //Then
    parsed should matchPattern { case parser.Success(SimpleStm("sElecT * FROM users LIMIT ? ;"), _) =>}
  }

  "Parser" should "parse prepare" in {
    //Given
    val query:String =""" @prepare[select_users]=SELECT * FROM users LIMIT ? """.stripMargin

    //When
    val parsed = parser.parseAll(parser.prepare, query)

    //Then
    parsed should matchPattern { case parser.Success(PrepareStm("select_users","SELECT * FROM users LIMIT ?"), _) => }
  }

  "Parser" should "fails parsing invalid prepared statement" in {
    val query:String =""" @prepare=SELECT * FROM users LIMIT ?""".stripMargin
    val ex = intercept[InterpreterException] {
      parser.parseAll(parser.prepare, query)
    }
    ex.getMessage should be(s"Invalid syntax for @prepare. It should comply to the pattern: @prepare[prepared_statement_name]=CQL Statement (without semi-colon)")
  }

  "Parser" should "parse remove prepare" in {
    //Given
    val query:String =""" @remove_prepare[select_users  ]""".stripMargin

    //When
    val parsed = parser.parseAll(parser.removePrepare, query)

    //Then
    parsed should matchPattern { case parser.Success(RemovePrepareStm("select_users"), _) => }
  }

  "Parser" should "fails parsing invalid remove prepared statement" in {
    val query:String =""" @remove_prepare[select_users]=SELECT * FROM users LIMIT ?""".stripMargin
    val ex = intercept[InterpreterException] {
      parser.parseAll(parser.removePrepare, query)
    }
    ex.getMessage should be(s"Invalid syntax for @remove_prepare. It should comply to the pattern: @remove_prepare[prepared_statement_name]")
  }

  "Parser" should "parse bind" in {
    //Given
    val query:String =""" @bind[select_users  ]=10,'toto'""".stripMargin

    //When
    val parsed = parser.parseAll(parser.bind, query)

    //Then
    parsed should matchPattern { case parser.Success(BoundStm("select_users","10,'toto'"), _) => }
  }

  "Parser" should "fails parsing invalid bind statement" in {
    val query:String =""" @bind[select_users]=""".stripMargin
    val ex = intercept[InterpreterException] {
      parser.parseAll(parser.bind, query)
    }
    ex.getMessage should be("""Invalid syntax for @bind. It should comply to the pattern: @bind[prepared_statement_name]=10,'jdoe','John DOE',12345,'2015-07-32 12:04:23.234' OR @bind[prepared_statement_name] with no bound value. No semi-colon""")
  }

  "Parser" should "parse batch" in {
    //Given
    val query:String ="""
      bEgin Batch
        Insert INTO users(id) VALUES(10);
        @bind[select_users  ]=10,'toto'
        update users SET name ='John DOE' WHERE id=10;
        dElEtE users WHERE id=11;
      APPLY BATCH;""".stripMargin

    //When
    val parsed = parser.parseAll(parser.batch, query)

    //Then
    parsed should matchPattern {
      case parser.Success(BatchStm(
      BatchStatement.Type.LOGGED,
      List(
        SimpleStm("Insert INTO users(id) VALUES(10);"),
        BoundStm("select_users", "10,'toto'"),
        SimpleStm("update users SET name ='John DOE' WHERE id=10;"),
        SimpleStm("dElEtE users WHERE id=11;")
        )
      ), _) =>
    }
  }

  "Parser" should "fails parsing invalid batch type" in {
    val query:String ="""BEGIN UNKNOWN BATCH""".stripMargin

    val ex = intercept[InterpreterException] {
      parser.extractBatchType(query)
    }
    ex.getMessage should be(s"""Invalid syntax for BEGIN BATCH. It should comply to the pattern: ${BATCH_PATTERN.toString}""")
  }

  "Parser" should "parse query parameter with statement" in {

      val query:String = "@serialConsistency=SERIAL\n" +
        "SELECT * FROM zeppelin.artists LIMIT 1;"

      val parsed = parser.parseAll(parser.queries, query)

    parsed should matchPattern {
      case parser.Success(List(
        SerialConsistency(ConsistencyLevel.SERIAL),
        SimpleStm("SELECT * FROM zeppelin.artists LIMIT 1;")
      ), _) =>
    }
  }

  "Parser" should "parse multi-line single statement" in {

    val query:String = "CREATE TABLE IF NOT EXISTS zeppelin.albums(\n" +
      "    title text PRIMARY KEY,\n" +
      "    artist text,\n" +
      "    year int\n" +
      ");\n"

    val parsed = parser.parseAll(parser.queries, query)

    parsed should matchPattern {
      case parser.Success(List(
        SimpleStm("CREATE TABLE IF NOT EXISTS zeppelin.albums(\n    title text PRIMARY KEY,\n    artist text,\n    year int\n);")
      ), _) =>
    }
  }

  "Parser" should "parse multi-line statements" in {
    val query:String = "CREATE TABLE IF NOT EXISTS zeppelin.albums(\n" +
      "    title text PRIMARY KEY,\n" +
      "    artist text,\n" +
      "    year int\n" +
      ");\n" +
      "@consistency=THREE\n" +
      "@serialConsistency=SERIAL\n" +
      "BEGIN BATCH\n"+
      "   INSERT INTO zeppelin.albums(title,artist,year) VALUES('The Impossible Dream EP','Carter the Unstoppable Sex Machine',1992);"+
      "   INSERT INTO zeppelin.albums(title,artist,year) VALUES('The Way You Are','Tears for Fears',1983);"+
      "   INSERT INTO zeppelin.albums(title,artist,year) VALUES('Primitive','Soulfly',2003);\n"+
      "APPLY BATCH;\n"+
      "@timestamp=10\n" +
      "@retryPolicy=DOWNGRADING_CONSISTENCY\n" +
      "SELECT * FROM zeppelin.albums;"

    val parsed = parser.parseAll(parser.queries, query)

    parsed should matchPattern {
      case parser.Success(List(
      SimpleStm("CREATE TABLE IF NOT EXISTS zeppelin.albums(\n    title text PRIMARY KEY,\n    artist text,\n    year int\n);"),
      Consistency(ConsistencyLevel.THREE),
      SerialConsistency(ConsistencyLevel.SERIAL),
      BatchStm(BatchStatement.Type.LOGGED,
        List(
          SimpleStm("INSERT INTO zeppelin.albums(title,artist,year) VALUES('The Impossible Dream EP','Carter the Unstoppable Sex Machine',1992);"),
          SimpleStm("INSERT INTO zeppelin.albums(title,artist,year) VALUES('The Way You Are','Tears for Fears',1983);"),
          SimpleStm("INSERT INTO zeppelin.albums(title,artist,year) VALUES('Primitive','Soulfly',2003);")
        )
      ),
      Timestamp(10L),
      DowngradingRetryPolicy,
      SimpleStm("SELECT * FROM zeppelin.albums;")
      ), _) =>
    }
  }

  "Parser" should "parse mixed single-line and multi-line statements" in {

    val query:String = "CREATE TABLE IF NOT EXISTS zeppelin.albums(\n" +
      "    title text PRIMARY KEY,\n" +
      "    artist text,\n" +
      "    year int\n" +
      ");\n" +
      "BEGIN BATCH"+
      "   INSERT INTO zeppelin.albums(title,artist,year) VALUES('The Impossible Dream EP','Carter the Unstoppable Sex Machine',1992);"+
      "   INSERT INTO zeppelin.albums(title,artist,year) VALUES('The Way You Are','Tears for Fears',1983);"+
      "   INSERT INTO zeppelin.albums(title,artist,year) VALUES('Primitive','Soulfly',2003);\n"+
      "APPLY BATCH;"+
      "SELECT * FROM zeppelin.albums;"

    val parsed = parser.parseAll(parser.queries, query)

    parsed should matchPattern {
      case parser.Success(List(
      SimpleStm("CREATE TABLE IF NOT EXISTS zeppelin.albums(\n    title text PRIMARY KEY,\n    artist text,\n    year int\n);"),
      BatchStm(BatchStatement.Type.LOGGED,
        List(
          SimpleStm("INSERT INTO zeppelin.albums(title,artist,year) VALUES('The Impossible Dream EP','Carter the Unstoppable Sex Machine',1992);"),
          SimpleStm("INSERT INTO zeppelin.albums(title,artist,year) VALUES('The Way You Are','Tears for Fears',1983);"),
          SimpleStm("INSERT INTO zeppelin.albums(title,artist,year) VALUES('Primitive','Soulfly',2003);")
        )
      ),
      SimpleStm("SELECT * FROM zeppelin.albums;")
      ), _) =>
    }
  }

  "Parser" should "parse a block queries with comments" in {
    val query =
      """
        /*
         This example show how to force a
         timestamp on the query
        */
        #Timestamp in the past
        @timestamp=10
        CREATE TABLE IF NOT EXISTS spark_demo.ts(key int PRIMARY KEY, value text);

        TRUNCATE spark_demo.ts;

        #Force timestamp directly in the first INSERT
        INSERT INTO spark_demo.ts(key,value) VALUES(1,'val1') USING TIMESTAMP 100;

        #Select some data to loose some time
        SELECT * FROM spark_demo.albums LIMIT 100;

        #Use @timestamp value set at the beginning(10)
        INSERT INTO spark_demo.ts(key,value) VALUES(1,'val2');

        #Check the result
        SELECT * FROM spark_demo.ts WHERE key=1;

      """.stripMargin

    val parsed = parser.parseAll(parser.queries, query)

    parsed should matchPattern {
      case parser.Success(List(
        Comment("\n         This example show how to force a\n         timestamp on the query\n        "),
        Comment("Timestamp in the past"),
        Timestamp(10L),
        SimpleStm("CREATE TABLE IF NOT EXISTS spark_demo.ts(key int PRIMARY KEY, value text);"),
        SimpleStm("TRUNCATE spark_demo.ts;"),
        Comment("Force timestamp directly in the first INSERT"),
        SimpleStm("INSERT INTO spark_demo.ts(key,value) VALUES(1,'val1') USING TIMESTAMP 100;"),
        Comment("Select some data to loose some time"),
        SimpleStm("SELECT * FROM spark_demo.albums LIMIT 100;"),
        Comment("Use @timestamp value set at the beginning(10)"),
        SimpleStm("INSERT INTO spark_demo.ts(key,value) VALUES(1,'val2');"),
        Comment("Check the result"),
        SimpleStm("SELECT * FROM spark_demo.ts WHERE key=1;")
        ), _
      ) =>
    }
  }

  "Parser" should "remove prepared statement" in {
    val queries =
      """
        #Removing an unknown statement should has no side effect
        @remove_prepare[unknown_statement]
        @remove_prepare[select_artist_by_name]

        #This should fail because the 'select_artist_by_name' has been removed
        @bind[select_artist_by_name]='The Beatles'
      """.stripMargin

    val parsed = parser.parseAll(parser.queries, queries)

    parsed should matchPattern {
      case parser.Success(List(
      Comment("Removing an unknown statement should has no side effect"),
      RemovePrepareStm("unknown_statement"),
      RemovePrepareStm("select_artist_by_name"),
      Comment("This should fail because the 'select_artist_by_name' has been removed"),
      BoundStm("select_artist_by_name","'The Beatles'")
    ), _) => }
  }

  "Parser" should "parse only parameter" in {
    val queries =
      "@fetchSize=1000"

    val parsed = parser.parseAll(parser.queries, queries)

    parsed should matchPattern {
      case parser.Success(List(FetchSize(1000)), _) =>
    }
  }


  "Parser" should "parse describe cluster" in {
    val queries ="Describe ClUsTeR;"

    val parsed = parser.parseAll(parser.queries, queries)

    parsed should matchPattern {
      case parser.Success(List(DescribeClusterCmd("DESCRIBE CLUSTER;")), _) =>
    }
  }

  "Parser" should "fail parsing describe cluster" in {
    val queries ="Describe ClUsTeR"

    val ex = intercept[InterpreterException] {
      parser.parseAll(parser.queries, queries)
    }
    ex.getMessage should be(s"Invalid syntax for DESCRIBE CLUSTER. It should comply to the pattern: ${DESCRIBE_CLUSTER_PATTERN.toString}")
  }

  "Parser" should "parse describe keyspace" in {
    val queries ="Describe KeYsPaCe toto;"

    val parsed = parser.parseAll(parser.queries, queries)

    parsed should matchPattern {
      case parser.Success(List(DescribeKeyspaceCmd("toto")), _) =>
    }
  }

  "Parser" should "fail parsing describe keyspace" in {
    val queries ="Describe KeYsPaCe toto"

    val ex = intercept[InterpreterException] {
      parser.parseAll(parser.queries, queries)
    }
    ex.getMessage should be(s"Invalid syntax for DESCRIBE KEYSPACE. It should comply to the pattern: ${DESCRIBE_KEYSPACE_PATTERN.toString}")
  }

  "Parser" should "parse describe keyspaces" in {
    val queries ="Describe KeYsPaCeS;"

    val parsed = parser.parseAll(parser.queries, queries)

    parsed should matchPattern {
      case parser.Success(List(DescribeKeyspacesCmd("DESCRIBE KEYSPACES;")), _) =>
    }
  }

  "Parser" should "fail parsing describe keyspaces" in {
    val queries ="Describe KeYsPaCeS"

    val ex = intercept[InterpreterException] {
      parser.parseAll(parser.queries, queries)
    }
    ex.getMessage should be(s"Invalid syntax for DESCRIBE KEYSPACES. It should comply to the pattern: ${DESCRIBE_KEYSPACES_PATTERN.toString}")
  }

  "Parser" should "parse describe table" in {
    val queries ="Describe TaBlE toto;"

    val parsed = parser.parseAll(parser.queries, queries)

    parsed.get should be(List(DescribeTableCmd(None,"toto")))
  }

  "Parser" should "parse describe table with keyspace" in {
    val queries ="Describe TaBlE ks.toto;"

    val parsed = parser.parseAll(parser.queries, queries)

    parsed.get should be(List(DescribeTableCmd(Some("ks"),"toto")))
  }

  "Parser" should "fail parsing describe table" in {
    val queries ="Describe TaBlE toto"

    val ex = intercept[InterpreterException] {
      parser.parseAll(parser.queries, queries)
    }
    ex.getMessage should be(s"Invalid syntax for DESCRIBE TABLE. It should comply to the patterns: " +
      s"${DESCRIBE_TABLE_WITH_KEYSPACE_PATTERN.toString} or ${DESCRIBE_TABLE_PATTERN.toString}")
  }

  "Parser" should "parse describe tables" in {
    val queries ="Describe TaBlEs;"

    val parsed = parser.parseAll(parser.queries, queries)

    parsed should matchPattern {
      case parser.Success(List(DescribeTablesCmd("DESCRIBE TABLES;")), _) =>
    }
  }

  "Parser" should "fail parsing describe tables" in {
    val queries ="Describe TaBlEs"

    val ex = intercept[InterpreterException] {
      parser.parseAll(parser.queries, queries)
    }
    ex.getMessage should be(s"Invalid syntax for DESCRIBE TABLES. It should comply to the pattern: ${DESCRIBE_TABLES_PATTERN.toString}")
  }

  "Parser" should "parse describe type" in {
    val queries ="Describe Type toto;"

    val parsed = parser.parseAll(parser.queries, queries)

    parsed should matchPattern {
      case parser.Success(List(DescribeTypeCmd(None, "toto")), _) =>
    }
  }

  "Parser" should "parse describe type with keyspace" in {
    val queries ="Describe Type ks.toto;"

    val parsed = parser.parseAll(parser.queries, queries)

    parsed should matchPattern {
      case parser.Success(List(DescribeTypeCmd(Some("ks"), "toto")), _) =>
    }
  }

  "Parser" should "fail parsing describe type" in {
    val queries ="Describe Type toto"

    val ex = intercept[InterpreterException] {
      parser.parseAll(parser.queries, queries)
    }
    ex.getMessage should be(s"Invalid syntax for DESCRIBE TYPE. It should comply to the patterns: " +
      s"${DESCRIBE_TYPE_WITH_KEYSPACE_PATTERN.toString} or ${DESCRIBE_TYPE_PATTERN.toString}")
  }

  "Parser" should "parse describe types" in {
    val queries ="Describe types;"

    val parsed = parser.parseAll(parser.queries, queries)

    parsed should matchPattern {
      case parser.Success(List(DescribeTypesCmd("DESCRIBE TYPES;")), _) =>
    }
  }

  "Parser" should "fail parsing describe types" in {
    val queries ="Describe types"

    val ex = intercept[InterpreterException] {
      parser.parseAll(parser.queries, queries)
    }
    ex.getMessage should be(s"Invalid syntax for DESCRIBE TYPES. It should comply to the pattern: ${DESCRIBE_TYPES_PATTERN.toString}")
  }

  "Parser" should "parse describe function" in {
    val queries ="Describe function toto;"

    val parsed = parser.parseAll(parser.queries, queries)

    parsed should matchPattern {
      case parser.Success(List(DescribeFunctionCmd(None,"toto")),_) =>
    }
  }



  "Parser" should "parse describe function with keyspace" in {
    val queries ="Describe function ks.toto;"

    val parsed = parser.parseAll(parser.queries, queries)

    parsed should matchPattern {
      case parser.Success(List(DescribeFunctionCmd(Some("ks"), "toto")), _) =>
    }
  }

  "Parser" should "fail parsing describe function" in {
    val queries ="Describe function toto"

    val ex = intercept[InterpreterException] {
      parser.parseAll(parser.queries, queries)
    }
    ex.getMessage should be(s"Invalid syntax for DESCRIBE FUNCTION. It should comply to the patterns: " +
      s"${DESCRIBE_FUNCTION_WITH_KEYSPACE_PATTERN.toString} or ${DESCRIBE_FUNCTION_PATTERN.toString}")
  }

  "Parser" should "parse describe functions" in {
    val queries ="Describe functions;"

    val parsed = parser.parseAll(parser.queries, queries)

    parsed should matchPattern {
      case parser.Success(List(DescribeFunctionsCmd("DESCRIBE FUNCTIONS;")), _) =>
    }
  }

  "Parser" should "fail parsing describe functions" in {
    val queries ="Describe functions toto"

    val ex = intercept[InterpreterException] {
      parser.parseAll(parser.queries, queries)
    }
    ex.getMessage should be(s"Invalid syntax for DESCRIBE FUNCTIONS. It should comply to the pattern: " +
      s"${DESCRIBE_FUNCTIONS_PATTERN.toString}")
  }


  "Parser" should "parse describe aggregate" in {
    val queries ="Describe aggregate toto;"

    val parsed = parser.parseAll(parser.queries, queries)

    parsed should matchPattern {
      case parser.Success(List(DescribeAggregateCmd(None, "toto")), _) =>
    }
  }

  "Parser" should "parse describe aggregate with keyspace" in {
    val queries ="Describe aggregate ks.toto;"

    val parsed = parser.parseAll(parser.queries, queries)

    parsed should matchPattern {
      case parser.Success(List(DescribeAggregateCmd(Some("ks"),"toto")), _) =>
    }
  }

  "Parser" should "fail parsing describe aggregate" in {
    val queries ="Describe aggregate toto"

    val ex = intercept[InterpreterException] {
      parser.parseAll(parser.queries, queries)
    }
    ex.getMessage should be(s"Invalid syntax for DESCRIBE AGGREGATE. It should comply to the patterns: " +
      s"${DESCRIBE_AGGREGATE_WITH_KEYSPACE_PATTERN.toString} or ${DESCRIBE_AGGREGATE_PATTERN.toString}")
  }

  "Parser" should "parse describe aggregates" in {
    val queries ="Describe aggregates;"

    val parsed = parser.parseAll(parser.queries, queries)

    parsed should matchPattern {
      case parser.Success(List(DescribeAggregatesCmd("DESCRIBE AGGREGATES;")), _) =>
    }
  }

  "Parser" should "fail parsing describe aggregates" in {
    val queries ="Describe aggregates toto"

    val ex = intercept[InterpreterException] {
      parser.parseAll(parser.queries, queries)
    }
    ex.getMessage should be(s"Invalid syntax for DESCRIBE AGGREGATES. It should comply to the pattern: " +
      s"${DESCRIBE_AGGREGATES_PATTERN.toString}")
  }

  "Parser" should "parse describe materialized view" in {
    val queries ="Describe materialized view toto;"

    val parsed = parser.parseAll(parser.queries, queries)

    parsed should matchPattern {
      case parser.Success(List(DescribeMaterializedViewCmd(None, "toto")), _) =>
    }
  }

  "Parser" should "parse describe materialized view with keyspace" in {
    val queries ="Describe materialized view ks.toto;"

    val parsed = parser.parseAll(parser.queries, queries)

    parsed should matchPattern {
      case parser.Success(List(DescribeMaterializedViewCmd(Some("ks"), "toto")), _) =>
    }
  }

  "Parser" should "fail parsing describe materialized view" in {
    val queries ="Describe materialized view toto"

    val ex = intercept[InterpreterException] {
      parser.parseAll(parser.queries, queries)
    }
    ex.getMessage should be(s"Invalid syntax for DESCRIBE MATERIALIZED VIEW. It should comply to the patterns: " +
      s"${DESCRIBE_MATERIALIZED_VIEW_WITH_KEYSPACE_PATTERN.toString} or ${DESCRIBE_MATERIALIZED_VIEW_PATTERN.toString}")
  }

  "Parser" should "parse describe materialized views" in {
    val queries ="Describe materialized views;"

    val parsed = parser.parseAll(parser.queries, queries)

    parsed should matchPattern {
      case parser.Success(List(DescribeMaterializedViewsCmd("DESCRIBE MATERIALIZED VIEWS;")), _) =>
    }
  }

  "Parser" should "fail parsing describe materialized views" in {
    val queries ="Describe materialized views toto"

    val ex = intercept[InterpreterException] {
      parser.parseAll(parser.queries, queries)
    }
    ex.getMessage should be(s"Invalid syntax for DESCRIBE MATERIALIZED VIEWS. It should comply to the pattern: " +
      s"${DESCRIBE_MATERIALIZED_VIEWS_PATTERN.toString}")
  }

  "Parser" should "parse help" in {
    val queries ="hElp;"

    val parsed = parser.parseAll(parser.queries, queries)

    parsed should matchPattern {
      case parser.Success(List(HelpCmd("HELP;")), _) =>
    }
  }

  "Parser" should "fail parsing help" in {
    val queries ="HELP"

    val ex = intercept[InterpreterException] {
      parser.parseAll(parser.queries, queries)
    }
    ex.getMessage should be(s"Invalid syntax for HELP. It should comply to the patterns: " +
      s"${HELP_PATTERN.toString}")
  }

  "Parser" should "parse CREATE FUNCTION" in {
    val query = "CREATE FUNCTION keyspace.udf xxx AS 'return true;';"

    val parsed = parser.parseAll(parser.queries, query)

    parsed should matchPattern {
      case parser.Success(List(SimpleStm(query)), _) =>
    }

  }

  "Parser" should "parse CREATE OR REPLACE FUNCTION" in {
    val query = "CREATE or Replace FUNCTION keyspace.udf xxx AS 'return true;';"

    val parsed = parser.parseAll(parser.queries, query)

    parsed should matchPattern {
      case parser.Success(List(SimpleStm(query)), _) =>
    }

  }

  "Parser" should "parse CREATE FUNCTION IF NOT Exists" in {
    val query = "CREATE FUNCTION IF NOT EXISTS keyspace.udf xxx AS 'return true;';"

    val parsed = parser.parseAll(parser.queries, query)

    parsed should matchPattern {
      case parser.Success(List(SimpleStm(query)), _) =>
    }
  }

  "Parser" should "parse CREATE FUNCTION multiline with simple quote" in {
    val query =
      """CREATE FUNCTION IF NOT EXISTS keyspace.udf(input text) xxx
        | CALLED ON NULL INPUT
        | RETURN text
        | LANGUAGE java
        | AS '
        |  return input.toLowerCase("abc");
        | ';""".stripMargin

    val parsed = parser.parseAll(parser.queries, query)

    parsed should matchPattern {
      case parser.Success(List(SimpleStm(query)), _) =>
    }
  }

  "Parser" should "parse CREATE FUNCTION multiline with double dollar" in {
    val query =
      """CREATE FUNCTION IF NOT EXISTS keyspace.udf(input text) xxx
        | CALLED ON NULL INPUT
        | RETURN text
        | LANGUAGE java
        | AS $$
        |  return input.toLowerCase("abc");
        | $$;""".stripMargin

    val parsed = parser.parseAll(parser.queries, query)

    parsed should matchPattern {
      case parser.Success(List(SimpleStm(query)), _) =>
    }
  }

  "Parser" should "parse CREATE FUNCTION multiline with SELECT" in {

    val udf =
      """CREATE FUNCTION IF NOT EXISTS keyspace.udf(input text) xxx
        | CALLED ON NULL INPUT
        | RETURN text
        | LANGUAGE java
        | AS '
        |  return input.toLowerCase("abc");
        | ';""".stripMargin

    val select = "SELECT udf(val) from table WHERe id=1;"
    val queries =
      s"""$udf
        |$select
      """.stripMargin

    val parsed = parser.parseAll(parser.queries, queries)

    parsed should matchPattern {
      case parser.Success(List(SimpleStm(udf), SimpleStm(select)), _) =>
    }
  }

  "Parser" should "parse CREATE multiple FUNCTIONS" in {

    val udf1 =
      """CREATE FUNCTION IF NOT EXISTS keyspace.udf(input text) xxx
        | CALLED ON NULL INPUT
        | RETURN text
        | LANGUAGE java
        | AS '
        |  return input.toLowerCase("abc");
        | ';""".stripMargin

    val select = "SELECT * FROM keyspace.table;"

    val udf2 = """CREATE FUNCTION IF NOT EXISTS keyspace.maxOf(val1 int, val2 int)
      | CALLED ON NULL INPUT
      | RETURN text
      | LANGUAGE java
      | AS '
      |  return Math.max(val1,val2);
      | ';""".stripMargin

    val queries =
      s"""$udf1
         |
         |$select
         |
         |$udf2
      """.stripMargin

    val parsed = parser.parseAll(parser.queries, queries)

    parsed.get.size should be(3)

    parsed should matchPattern {
      case parser.Success(List(SimpleStm(udf1), SimpleStm(select), SimpleStm(udf2)), _) =>
    }
  }

  "Parser" should "parse CREATE Materialized View" in {
    val query =
      """CREATE MATERIALIZED VIEW xxx
        | AS SELECT * FROM myTable
        | WHERE partition IS NOT NULL
        | PRIMARY KEY(col, partition);""".stripMargin

    val parsed = parser.parseAll(parser.queries, query)

    parsed should matchPattern {
      case parser.Success(List(SimpleStm(query)), _) =>
    }
  }

  "Parser" should "parse ALTER KEYSPACE" in {
    val query = "ALTER KEYSPACE toto WITH replication = " +
      "{'class': 'SimpleStrategy', 'replication_factor': 1};"

    val parsed = parser.parseAll(parser.queries, query)

    parsed should matchPattern {
      case parser.Success(List(SimpleStm(query)), _) =>
    }
  }
}
