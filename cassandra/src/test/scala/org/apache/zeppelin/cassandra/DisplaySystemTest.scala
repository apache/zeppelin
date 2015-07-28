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

import java.io.InputStream

import com.datastax.driver.core._
import org.apache.zeppelin.cassandra.DisplaySystem.{ClusterDisplay, KeyspaceDisplay, UDTDisplay, TableDisplay}

import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import info.archinnov.achilles.junit.AchillesResourceBuilder
import scala.io.Source

class DisplaySystemTest extends FlatSpec
  with BeforeAndAfterAll
  with Matchers
  with MockitoSugar {

  var session: Session = null

  override def beforeAll(): Unit = {
    val resource = AchillesResourceBuilder.
                    noEntityPackages.
                    withKeyspaceName("live_data").
                    withScript("prepare_schema.cql").build
    session = resource.getNativeSession
  }

  "DisplaySystem" should "display table details" in {
    //Given
    val tableMetadata = session.getCluster.getMetadata.getKeyspace("live_data").getTable("complex_table")

    //When
    val actual: String = TableDisplay.format("DESCRIBE TABLE live_data.complex_table;", tableMetadata, true)

    //Then
    val expected: String = readTestResource("/scalate/DescribeTable_live_data_complex_table.html")

    reformatHtml(actual) should be(reformatHtml(expected))
  }


  "DisplaySystem" should "display udt details" in {
    //Given
    val userType: UserType = session.getCluster.getMetadata.getKeyspace("live_data").getUserType("address")

    //When
    val actual: String = UDTDisplay.format("DESCRIBE TYPE live_data.address;", userType, true)

    //Then
    val expected: String = readTestResource("/scalate/DescribeType_live_data_address.html")

    reformatHtml(actual) should be(reformatHtml(expected))
  }

  "DisplaySystem" should "display keyspace details" in {
    //Given
    val ksMeta: KeyspaceMetadata = session.getCluster.getMetadata.getKeyspace("live_data")

    //When
    val actual: String = KeyspaceDisplay.formatKeyspaceOnly(ksMeta, true)

    //Then
    val expected: String = readTestResource("/scalate/DescribeKeyspace_alone.html")

    reformatHtml(actual) should be(reformatHtml(expected))
  }

  "DisplaySystem" should "display keyspace content" in {
    //Given
    val ksMeta: KeyspaceMetadata = session.getCluster.getMetadata.getKeyspace("live_data")

    //When
    val actual: String = KeyspaceDisplay.formatKeyspaceContent("DESCRIBE KEYSPACE live_data;",ksMeta)

    //Then
    val expected: String = readTestResource("/scalate/DescribeKeyspace_live_data.html")

    reformatHtml(actual) should be(reformatHtml(expected))
  }

  "DisplaySystem" should "display cluster details" in {
    //Given
    val meta: Metadata = session.getCluster.getMetadata

    //When
    val actual: String = ClusterDisplay.formatClusterOnly("DESCRIBE CLUSTER;", meta)

    //Then
    val expected: String = readTestResource("/scalate/DescribeCluster.html")

    reformatHtml(actual) should be(reformatHtml(expected))
  }

  "DisplaySystem" should "display cluster content" in {
    //Given
    val meta: Metadata = session.getCluster.getMetadata

    //When
    val actual: String = ClusterDisplay.formatClusterContent("DESCRIBE KEYSPACES;", meta)

    //Then
    val expected: String = readTestResource("/scalate/DescribeKeyspaces.html")

    reformatHtml(actual) should be(reformatHtml(expected))
  }

  "DisplaySystem" should "display all tables" in {
    //Given
    val meta: Metadata = session.getCluster.getMetadata

    //When
    val actual: String = ClusterDisplay.formatAllTables("DESCRIBE TABLES;", meta)

    //Then
    val expected: String = readTestResource("/scalate/DescribeTables.html")

    reformatHtml(actual) should be(reformatHtml(expected))
  }

  def reformatHtml(rawHtml: String): String = {
    rawHtml
      .replaceAll("""\s*\n\s*""","")
      .replaceAll( """>\s+<""", "><")
      .replaceAll( """(?s)data-target="#[a-f0-9-]+(?:_asCQL)?"""", "")
      .replaceAll( """(?s)id="[a-f0-9-]+(?:_asCQL)?"""", "")
      .trim
  }

  def readTestResource(testResource: String): String = {
    val stream : InputStream = getClass.getResourceAsStream(testResource)
    Source.fromInputStream(stream).getLines().mkString("\n")
  }
}
