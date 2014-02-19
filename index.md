---
layout: page
title: Zeppelin
description: TODO add
tagline: Less Development, More analysis!
---
{% include JB/setup %}


## What is Zeppelin?
Analytical environment on top of Hive (and Hive like systems).

Zeppelin provides

 * Web based user interface for Hive. History, Job management.
 * Support multiple different system similar to hive through pluggable driver. Currently Hive, Shark.
 * Pluggable visualization
 * Pluggable algorithm
 * Online archive of visualization, algorithm. [ZAN](./zan.html) (Zeppelin Archive Network)
 * Cron like scheduler embedded
 * Report generation (Share)

Can be used for
  
 * Lightweigt web interface for Hive and similar systems
 * Visualize data on Hadoop using Hive
 * Sharing visualization through http link
 * Share queries, algorithms trough online archive
 * Schedule queries for automate the job
 * Create custom visualizations using d3, google chart and any html/javascript

Checkout [screenshots](./screenshots.html).

  <div class="col-md-offset-9 table-container">
    <div class="text-center table-stack">
      <div class="zeppelin-color"><b>Zeppelin Stack</b></div><p></p>
      <table>
		<tbody>
          <tr>
		    <td rowspan="3"><div class="rotate270">Zeppelin</div></td>
		    <td colspan="1">CLI</td>
		    <td colspan="1">GUI</td>
		    <td colspan="1">ZAN</td>
		  </tr>
		  <tr>
		    <td colspan="3">ZQL</td>
		  </tr>
		  <tr>
		    <td colspan="3">Zengine</td>
		  </tr>
		  <tr>
		    <td  style="background-color:#FFFFFF"></td>
		    <td  colspan="2" class="gray">Hive</td>
		    <td  class="gray">...</td>
		  </tr>
		  <tr>
		    <td style="background-color:#FFFFFF"></td>
		    <td colspan="3" class="gray">Hadoop</td>
		  </tr>
        </tbody>
      </table>
    </div>
  </div>

### Zeppelin stack
 * **[Zengine](./zengine.html)**  is an framework for Java to simplify data analytics on Hadoop.
   Zeppelin generate Hive query.
 * **[ZQL](./zql.html)** is extension of HiveQL. Designed for easy data analysis.
 * **[ZAN](./zan.html)** is Zeppelin Archive Network, think npm for sharing libraries.


## Who uses it?
 * **NFLabs** - Zeppelin automates regular analytical query execution via embedded scheduler. Also our data analyist take care of on-demand analysis request from customer using Zeppelin.





## Lates News


#### Zeppelin 0.3.0 Released! ####
 
[Check here](./download.html)

 
<ul class="posts">
  {% for post in site.posts %}
    <li><span>{{ post.date | date_to_string }}</span> &raquo; <a href="{{ BASE_PATH }}{{ post.url }}">{{ post.title }}</a></li>
  {% endfor %}
</ul>

## To-Do

This project still is in early stages. If you'd like to be added as a contributor, [please fork](http://github.com/NFLabs/zeppelin)!



