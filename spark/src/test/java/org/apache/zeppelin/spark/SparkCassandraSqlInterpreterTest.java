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

import org.apache.zeppelin.spark.utils.CsqlParserUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class SparkCassandraSqlInterpreterTest {

    private SparkCassandraSqlInterpreter sql;

    @Before
    public void setUp() throws Exception {
        Properties p = new Properties();
        sql = new SparkCassandraSqlInterpreter(p);
    }

    @Test
    public void intervalExpansion() {
        String expandedQuery1 =
                CsqlParserUtils.parseAndExpandInterval("select foo from bar where day in interval('2016-01-01', '2016-01-03') and foo = 'bar'"); //CHECKSTYLE:OFF LineLength
        assertEquals(expandedQuery1, "select foo from bar where day in (16801, 16802) and foo = 'bar'");

        //CHECKSTYLE:OFF LineLength
        String expandedQuery2 =
                CsqlParserUtils.parseAndExpandInterval("select foo from bar where day in interval('2016-01-01', '2016-01-02') and foo = 'bar'"); //CHECKSTYLE:OFF LineLength
        assertEquals(expandedQuery2, "select foo from bar where day in (16801) and foo = 'bar'");
    }
}
