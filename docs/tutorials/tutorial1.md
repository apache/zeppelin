---
layout: page
title: "Your first analysis"
description: "Tutorial 1"
group: tutorial
---

In this tutorial, you'll know the basic about Zeppelin and it's GUI interface. Please check [install](../install/install.html) for installation.

#### ZQL Job

If you run zeppelin-daemon.sh start, you'll see menu 'ZQL'. You can create/run/save/delete the job here. ZQL Job is one unit of your analysis, and

* Have multiple ZQL Statements.
* Run ZQL Statement line by line.
* Have it's name. The name is user friendly name and it can be change at any time.
* Have it's id. Id is unique across all job. Can not be changed.
* Can be scheduled by cron like scheduler.
* Automatically persist all status. ZQL statements, Name, scheduing information and result data of execution.

You can create new job with 'New' button. You'll see ZQL editor on the screen.

<img class="screenshot" src="/assets/themes/zeppelin/img/tutorial1/new_session.png" />


#### Let's load some data

We'll use data [Bank Marketing Data Set](http://archive.ics.uci.edu/ml/datasets/Bank+Marketing) in this tutorial.

Let's create table first.

```
CREATE TABLE IF NOT EXISTS bank(
    age INT, 
    job STRING,
    marital STRING,
    education STRING, 
    default STRING,
    balance INT,
    housing STRING,
    loan STRING,
    contract STRING,
    day INT,
    month INT,
    duration INT,
    campain INT, 
    pdays INT, 
    previous INT, 
    poutcome STRING, 
    y STRING 
) 
ROW FORMAT DELIMITED 
FIELDS TERMINATED BY "59" 
STORED AS TEXTFILE 
; 
```

And then download and unarchive the data, using shell command statement (starting with !)

```
!curl -s http://archive.ics.uci.edu/ml/machine-learning-databases/00222/bank.zip -o /tmp/bank.zip;
!unzip -q -o /tmp/bank.zip bank-full.csv -d /tmp;
```

Load downloaded data into table using HQL's load

```
LOAD DATA LOCAL INPATH '/tmp/bank-full.csv' OVERWRITE INTO TABLE bank;
```

Let's put all statements into the editor window. and change job name to 'Import bank marketing data' by clicking job name.

<img class="screenshot" src="/assets/themes/zeppelin/img/tutorial1/import_data.png" />

And then press 'Run'. Zeppelin will create table, download and unpack the data, and load data into the table.


#### Your first analysis

Let's create new job with name 'Bank marketing data analysis'. And let's discover average balance by age. using following query.

```
select 
    age, 
    avg(balance) as balance
from bank 
group by age 
order by age;
```

If you run this job, you'll see data in table form. Great! 


#### Install library from ZAN

However, there's better way. Zeppelin Archive Network has visualization library called vis.gchart, let's use it.
Go to ZAN menu, and click 'Update Catalog' link. Few seconds later, you'll see list of available Zeppelin libraries from ZAN repository. Install 'vis.gchart'.
And comeback to ZQL menu and create new job.

```
select 
    age, 
    avg(balance) as balance
from bank 
group by age 
order by age | vis.gchart;
```

If you run your job, you'll see data in table at default. You can change chart type with radio button. It looks like line chart is better for this data.Let's make line chart default.

```
select 
    age, 
    avg(balance) as balance
from bank 
group by age 
order by age | vis.gchart(type=line);
```

If you run the job, you'll see the visualization like

<img class="screenshot" src="/assets/themes/zeppelin/img/tutorial1/first_analysis.png" />


Also you can put some more ZQL statements in the job

```
select 
    age, 
    avg(balance) as balance
from bank 
group by age 
order by age | vis.gchart(type=line, height=200);

select
    job,
    count(*) as count
from bank
group by job | vis.gchart(type=pie, height=200);
```


Visualization will be displayed in order

<img class="screenshot" src="/assets/themes/zeppelin/img/tutorial1/multi_statement.png" />


#### Share analysis

You can share your analysis by email, etc. Click the share button on right bottom of your ZQL editor window.

You'll see job's result without menu, editor, etc. So this link can be shared by email in your organization.

<img class="screenshot" src="/assets/themes/zeppelin/img/tutorial1/share.png" />

In this tutorial, you have used 'vis.gchart' to visualize user data. Such thing is called [Zeppelin Library](../development/writingzeppelinlibrary.html).