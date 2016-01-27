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

package org.apache.zeppelin.spark;

import org.apache.commons.lang3.StringUtils;
import org.apache.spark.SparkContext;
import org.apache.spark.sql.DataFrame;
import org.apache.spark.sql.SQLContext;
import org.apache.spark.sql.cassandra.CassandraSQLContext;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spark Cassandra SQL interpreter for Zeppelin.
 */
public class SparkCassandraSqlInterpreter extends Interpreter {
    Logger logger = LoggerFactory.getLogger(SparkCassandraSqlInterpreter.class);
    AtomicInteger num = new AtomicInteger(0);

    static {
        Interpreter.register(
                "csql",
                "spark",
                SparkCassandraSqlInterpreter.class.getName(),
                new InterpreterPropertyBuilder()
                        .add("zeppelin.spark.maxResult",
                                SparkInterpreter.getSystemDefault("ZEPPELIN_SPARK_MAXRESULT",
                                        "zeppelin.spark.maxResult", "1000"),
                                "Max number of SparkSQL result to display.")
                        .add("zeppelin.spark.concurrentSQL",
                                SparkInterpreter.getSystemDefault("ZEPPELIN_SPARK_CONCURRENTSQL",
                                        "zeppelin.spark.concurrentSQL", "false"),
                                "Execute multiple SQL concurrently if set true.")
                        .build());
    }

    private String getJobGroup(InterpreterContext context){
        return "zeppelin-" + context.getParagraphId();
    }

    private int maxResult;

    public SparkCassandraSqlInterpreter(Properties property) {
        super(property);
    }

    private java.util.Date myfield = new java.util.Date();

    @Override
    public void open() {
        this.maxResult = Integer.parseInt(getProperty("zeppelin.spark.maxResult"));

        SparkInterpreter interperter = getSparkInterpreter();

        //
        // TODO: is there a way to wire a Cassandra SQL context into the Spark interpreter
        // this will allow us to not hae to hack up SparkInterpreter
        //
        // interperter.interpretInput(input);
        //
        logger.error("Done binding myfield.");
    }

    private SparkInterpreter getSparkInterpreter() {
        InterpreterGroup intpGroup = getInterpreterGroup();
        LazyOpenInterpreter lazy = null;
        SparkInterpreter spark = null;
        synchronized (intpGroup) {
            for (Interpreter intp : getInterpreterGroup()){
                if (intp.getClassName().equals(SparkInterpreter.class.getName())) {
                    Interpreter p = intp;
                    while (p instanceof WrappedInterpreter) {
                        if (p instanceof LazyOpenInterpreter) {
                            lazy = (LazyOpenInterpreter) p;
                        }
                        p = ((WrappedInterpreter) p).getInnerInterpreter();
                    }
                    spark = (SparkInterpreter) p;
                }
            }
        }
        if (lazy != null) {
            lazy.open();
        }
        return spark;
    }

    public boolean concurrentSQL() {
        return Boolean.parseBoolean(getProperty("zeppelin.spark.concurrentSQL"));
    }

    Pattern extractInterval =
            Pattern.compile(".*(interval\\(')([\\d]{4}-[\\d]{2}-[\\d]{2})([^\\d]+)([\\d]{4}-[\\d]{2}-[\\d]{2}).*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL); //CHECKSTYLE:OFF

    SimpleDateFormat dateParser = new SimpleDateFormat("yyyy-MM-dd");

    /**
     * Give a query like:
     *   select foo from bar where day in interval('2016-01-01', '2016-01-03')
     * Expand the result to:
     *   select foo from bar where day in ('123', '124', '124')
     * @return
     */
    protected String expandAndReplaceInterval(String query) {
        Matcher matcher = extractInterval.matcher(query);

        if (matcher.matches()) {
            try {
                Date start = dateParser.parse(matcher.group(2));
                Date end = dateParser.parse(matcher.group(4));

                if (end.before(start)) throw new RuntimeException("Start can't be after end!");
                if (end.equals(start)) throw new RuntimeException("End is exclusive and should not equal start!"); //CHECKSTYLE:OFF LineLength

                List<Long> dates = new ArrayList<Long>();

                int intervalStart = query.indexOf("interval");
                int intervalEnd = query.indexOf(")", intervalStart);

                while (start.before(end)){
                    dates.add( TimeUnit.MILLISECONDS.toDays(start.getTime()));
                    start = new Date(start.getTime() + TimeUnit.DAYS.toMillis(1));
                }
                String newInClause = "(" + StringUtils.join(dates, ", ") + ")";

                return query.substring(0, intervalStart) + newInClause + query.substring(intervalEnd + 1);
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }

        return query;
    }

    Pattern extractIntoTableNamePattern =
            Pattern.compile("(.*)(into)([ ]+)([a-zA-Z_]+).*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * Given a query like:
     *  SELECT * from mytable INTO myothertable
     *
     * Return "myothertable" or null
     *
     * @param query
     * @return
     */
    private String extractSqlInto(String query) {
        Matcher m = extractIntoTableNamePattern.matcher(query);
        if (m.matches()) {
            return m.group(4);
        } else {
            return null;
        }
    }

    /**
     * Given a query like:
     *  SELECT * from mytable INTO myothertable
     *
     * Return "SELECT * from mytable"
     *
     * @param query
     * @return
     */
    private String removeSqlInto(String query) {
        Matcher m = extractIntoTableNamePattern.matcher(query);
        if (m.matches()) {
            return m.group(1);
        } else {
            return query;
        }
    }

    /**
     * Extract snippets of code that being used to do something
     * like register a UDF but looking for any use of a Cassandra SQL context.
     */
    private Boolean contextSparkSqlContext(String snippet) {
        return snippet.contains("csqlc.");
    }

    @Override
    public void close() {}

    @Override
    public InterpreterResult interpret(String st, InterpreterContext context) {

        CassandraSQLContext sqlc = null;

//    if (sparkInterpreter.getSparkVersion().isUnsupportedVersion()) {
//      return new InterpreterResult(Code.ERROR, "Spark "
//          + sparkInterpreter.getSparkVersion().toString() + " is not supported");
//    }

        sqlc = getSparkInterpreter().getCassandraSQLContext();

        SparkContext sc = sqlc.sparkContext();
        if (concurrentSQL()) {
            sc.setLocalProperty("spark.scheduler.pool", "fair");
        } else {
            sc.setLocalProperty("spark.scheduler.pool", null);
        }

        sc.setJobGroup(getJobGroup(context), "Zeppelin", false);


        DataFrame rddResult = null;
        for (String snippet : st.split(";")) {

            // Ignore trailing semicolons
            if (snippet.replaceAll("[\n\r ]", "").equals("")){
                logger.info("Skipping empty bloc.");
                continue;
            }

            if (contextSparkSqlContext(snippet)){
                InterpreterResult sparkResults = getSparkInterpreter().interpret(snippet, context);
                if (sparkResults.code() != Code.SUCCESS) return sparkResults;

            } else { // Assume this is SQL
                String intervalExpanded = expandAndReplaceInterval(snippet);
                logger.info("Expanded sql: " + intervalExpanded);

                String cleanedSql = removeSqlInto(intervalExpanded);
                logger.info("Cleaned sql: " + cleanedSql);
                rddResult = sqlc.sql(cleanedSql);

                String intoTable = extractSqlInto(snippet);
                if (intoTable != null) {
                    intoTable = intoTable.trim();
                    logger.info("Registering results to tempTable: " + intoTable);
                    rddResult.registerTempTable(intoTable);
                }
            }
        }

        String msg = "";
        if (rddResult != null) {
            msg = ZeppelinContext.showDF(sc, context, rddResult, maxResult);
        }
        sc.clearJobGroup();
        return new InterpreterResult(Code.SUCCESS, msg);
    }

    @Override
    public void cancel(InterpreterContext context) {
        SQLContext sqlc = getSparkInterpreter().getSQLContext();
        SparkContext sc = sqlc.sparkContext();

        sc.cancelJobGroup(getJobGroup(context));
    }

    @Override
    public FormType getFormType() {
        return FormType.SIMPLE;
    }


    @Override
    public int getProgress(InterpreterContext context) {
        SparkInterpreter sparkInterpreter = getSparkInterpreter();
        return sparkInterpreter.getProgress(context);
    }

    @Override
    public Scheduler getScheduler() {
        if (concurrentSQL()) {
            int maxConcurrency = 10;
            return SchedulerFactory.singleton().createOrGetParallelScheduler(
                    SparkCassandraSqlInterpreter.class.getName() + this.hashCode(), maxConcurrency);
        } else {
            // getSparkInterpreter() calls open() inside.
            // That means if SparkInterpreter is not opened, it'll wait until SparkInterpreter open.
            // In this moment UI displays 'READY' or 'FINISHED' instead of 'PENDING' or 'RUNNING'.
            // It's because of scheduler is not created yet, and scheduler is created by this function.
            // Therefore, we can still use getSparkInterpreter() here, but it's better and safe
            // to getSparkInterpreter without opening it.
            for (Interpreter intp : getInterpreterGroup()) {
                if (intp.getClassName().equals(SparkInterpreter.class.getName())) {
                    Interpreter p = intp;
                    return p.getScheduler();
                } else {
                    continue;
                }
            }
            throw new InterpreterException("Can't find SparkInterpreter");
        }
    }

    @Override
    public List<String> completion(String buf, int cursor) {
        return null;
    }
}
