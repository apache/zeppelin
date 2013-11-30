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
{% highlight antlr %}
ZQLStmt            : HiveQLStmt | LibraryStmt | ZQLStmt (PipeOperator ZQLStmt)*
HiveQLStmt         : HIVE_QUERY
LibraryStmt        : LIBRARY_NAME(paramName1=paramValue1, paramName2=paramValue2, ...)? (ARGUMENT)?
RedirectStmt       : ZQLStmt RedirectOperator (table|view)? TABLE_NAME
ExecStmt           : !SHELL_COMMAND
PipeOperator       : |
RedirectOperator   : >
{% endhighlight %}

### Template
ZQLStmt is also erb template. In ZQLStmt ZContext is provided by local variable 'z'.

### ZContext
z.in - Input table name

z.out - Output table name

z.arg - library argument

z.param(paramName) - get parameter value by parameterName

### Examples
Basic query
{% highlight sql %}
select text from myTable
{% endhighlight %}

Basic query with pipe
{% highlight sql %}
select text from myTable | select * from <%= z.in %> limit 10
{% endhighlight %}

Redirect (default behavior). By default, redirect create view.
{% highlight sql %}
select text from myTable > ouputView
{% endhighlight %}

Redirect (specifiying table|view)

{% highlight sql %}
select text from myTable > table outputTable
{% endhighlight %}

Library
{% highlight sql %}
select text from myTable | wordcount
{% endhighlight %}

Library with parameter
{% highlight sql %}
select text from myTable | wordcount(limit=5)
{% endhighlight %}

Library with parameter and argument
{% highlight sql %}
select text from myTable | wordcount(limit=5) logscale
{% endhighlight %}

Multiple statement
{% highlight sql %}
select text from myTable | wordcount > wc_out;               // load | process > export
select * from wc_out limit 10;                               // print first 10 rows of wc_out
{% endhighlight %}

Shell command
{% highlight sql %}
!date                                                        // print date
select text from myTable | wordcount > wc_out;               // load | process > export
!echo "finished" | mail -s "JOB_FINISHED" hello@world.net    // send email
{% endhighlight %}