---
layout: page
title: "ZQL"
description: ""
group: nav-left
---
{% include JB/setup %}

### Introducing ZQL
ZQL is very simple but powerful SQL like language based on HiveQL. ZQL can be run from Zeppelin GUI or CLI.

### ZQL syntax
```
Stmt               : ZQLStmt STMT_TERMINATOR
ZQLStmt            : (QStmt | LibraryStmt | ZQLStmt (PIPE_OPERATOR ZQLStmt)+ | AnnotationStmt)
AnnotationStmt     : @"driver" ANNOTATION_CMD (ARGUMENT)?
LibraryStmt        : LIBRARY_NAME(L_PAR KV_PAIR ("," KV_PAIR)* R_PAR)? (MULTI_LINE_ARGUMENT)?
LibraryStmt_v2     : @"zan" LibraryStmt        // will be supported from future release 
RedirectStmt       : ZQLStmt REDIRECT_OPTERATOR ("table"|"view")? TABLE_NAME
QStmt              : MULTI_LINE_STRING

PIPE_OPERATOR      : |
REDIRECT_OPTERATOR : >                         // from 0.3.1 it is disabled. see ZEPPELIN-99
STMT_TERMINATOR    : ;
L_PAR              : (
R_PAR              : )
LIBRARY_NAME       : [A-Za-z_-0-9]+
TABLE_NAME         : [A-Za-z_-0-9]+
ANNOTATION_CMD     : "set"
KV_PAIR            : KV_KEY "=" KV_VAL
KV_KEY             : [A-Za-z_-0-9]+
KV_VAL             : [^,R_PAR]+

SINGLE_LINE_STRING : .*
ARGUMENT           : SINGLE_LINE_STRING
MULTI_LINE_ARGUMENT: MULTI_LINE_STRING
MULTI_LINE_STRING  : SINGLE_LINE_STRING (\n SINGLE_LINE_STRING)*

```

### Template
ZQLStmt is also erb template. In ZQLStmt ZContext is provided by local variable 'z'.

### ZContext
z.in - Input table name

z.out - Output table name

z.arg - library argument

z.param(paramName) - get parameter value by parameterName

### Examples
Basic query

```
select text from myTable
```

Basic query with pipe

```
select text from myTable | select * from <%= z.in %> limit 10
```

Redirect (default behavior). By default, redirect create view.

```
select text from myTable > ouputView
```

Redirect (specifiying table|view)

```
select text from myTable > table outputTable
```

Library

```
select text from myTable | wordcount
```

Library with parameter

```
select text from myTable | wordcount(limit=5)
```

Library with parameter and argument

```
select text from myTable | wordcount(limit=5) logscale
```

Multiple statement

```
select text from myTable | wordcount > wc_out;               // load | process > export
select * from wc_out limit 10;                               // print first 10 rows of wc_out
```

Annotation

```
@driver set exec;                                            // set current driver to exec
date;                                                        // print date
whoami;                                                      // print who i am. 'driver set' annotation applied 
                                                             // until next 'drvier set' annotation

@driver set hive;                                            // set current driver to hive
select text from myTable | wordcount > wc_out;               // load | process > export

@driver set exec;                                            // set current driver to exec
bash -c 'echo "finished" | mail -s "JOB_FINISHED" hello@world.net';     // send email
```
