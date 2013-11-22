---
layout: page
title: Zeppelin
desctiption: TODO add
tagline: Less Development, More analysis!
---
{% include JB/setup %}

## What is Zeppelin?

### Zeppelin stack

 * **Zengine**  is an framework for Java to simplify data analytics on Hadoop. Zeppelin generate Hive query.
 * **ZQL** is extension of HiveQL. Designed for easy data analysis.
 * **ZAN** is Zeppelin Archive Network, think npm for sharing libraries.


## Who uses it?
...

## Lates News

#### Zeppelin 0.1.2 Developer preview ####
 
Some important changes with nice visualizer. [Check here](http://...)

 
<ul class="posts">
  {% for post in site.posts %}
    <li><span>{{ post.date | date_to_string }}</span> &raquo; <a href="{{ BASE_PATH }}{{ post.url }}">{{ post.title }}</a></li>
  {% endfor %}
</ul>

## To-Do

This project still is in early stages. If you'd like to be added as a contributor, [please fork](http://github.com/NFLabs/zeppelin)!



