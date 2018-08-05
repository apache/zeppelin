# KSQL Interpreter for Apache Zeppelin

---
Work in progress!  Code is very primitive right now!  Most probably it will be 
rewritten completely
---

## Prerequisites

This version of KSQL Interpreter was tested to work with KSQL shipped as part of Confluent
Platform 5.0.x.  Earlier versions aren't supported due big changes in the REST API answer
structure.

You need to have an instance of KSQL server running & accessible from machine where
Zeppelin is running.

## Installation 


## Configuration

* `ksql.url` (default: `http://localhost:8088`) - URL of the KSQL REST API;
* `ksql.fetchSize` (default: `10`) - how many entries to fetch by default, if no `LIMIT N`
  is specified in query.

## Using

Supported functionality:

* Show information about available streams & tables: `show streams`, `list streams`, `show
  tables`, `list tables`;
* Show information about topics: `show topics`, `list topics`;
* Show system information: `show properties`, `list properties`;
* Show information about individual tables/streams: `describe [extended] name`;
* Show information about existing queries: `show queries`, `list queries`;
* Selecting data from given table/stream: `select ... from name ... limit N`.  If `limit
  N` isn't provided, it's injected into query with `N` equal to value of configuration
  parameter `ksql.fetchSize` (default: 10);

Not implemented yet:
* DROP STREAM
* DROP TABLE
* CREATE STREAM
* CREATE TABLE
* CREATE STREAM AS SELECT
* CREATE TABLE AS SELECT
* EXPLAIN
* PRINT (`PRINT 'source_topic_name' FROM BEGINNING;`)
* TERMINATE 



