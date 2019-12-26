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

import com.datastax.driver.core.{BatchStatement, SimpleStatement}
import org.scalatest.FlatSpec

class EnhancedSessionTest extends FlatSpec {

  "Query" should "be detected as DDL for create" in {
    assertResult(true){
      EnhancedSession.isDDLStatement("create TABLE if not exists test.test(id int primary key);")
    }
  }

  it should "be detected as DDL for drop" in {
    assertResult(true) {
      EnhancedSession.isDDLStatement("DROP KEYSPACE if exists test;")
    }
  }

  it should "be detected as DDL for alter" in {
    assertResult(true) {
      EnhancedSession.isDDLStatement("ALTER TABLE test.test WITH comment = 'some comment' ;")
    }
  }

  it should "not be detected as DDL for select" in {
    assertResult(false) {
      EnhancedSession.isDDLStatement("select * from test.test;")
    }
  }

  it should "be detected as DDL for create in simple statement" in {
    assertResult(true) {
      EnhancedSession.isDDLStatement(new SimpleStatement("create TABLE if not exists test.test(id int primary key);"))
    }
  }

  it should "be detected as DDL for create in batch statement" in {
    val batch = new BatchStatement
    batch.add(new SimpleStatement("create TABLE if not exists test.test(id int primary key);"))
    batch.add(new SimpleStatement("insert into test.test(id) values(1);"))
    assertResult(true) {
      EnhancedSession.isDDLStatement(batch)
    }
  }

  it should "not be detected as DDL for only inserts in batch statement" in {
    val batch = new BatchStatement
    batch.add(new SimpleStatement("insert into test.test(id) values(1);"))
    batch.add(new SimpleStatement("insert into test.test(id) values(2);"))
    batch.add(new SimpleStatement("insert into test.test(id) values(3);"))
    assertResult(false) {
      EnhancedSession.isDDLStatement(batch)
    }
  }

}
