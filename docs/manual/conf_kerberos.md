---
layout: page
title: "Conf Kerberos"
description: ""
---
{% include JB/setup %}


# Setting up Zeppelin with Kerberos

Logical setup w/ Zeppelin, Kerberos Distribution Center (KDC), and Spark on YARN:

<img src="/assets/themes/zeppelin/img/kdc_zeppelin.png">

<b>Configuration Setup</b>

1. On the server that Zeppelin is installed, install Kerberos client modules and configuration, krb5.conf. 
This is to make the server communicate with KDC.

2. Set SPARK_HOME in [ZEPPELIN_HOME]/conf/zeppelin-env.sh to use spark-submit
(Additionally, you might have to set “export HADOOP_CONF_DIR=/etc/hadoop/conf”)

3. Add the two properties below to spark configuration ([SPARK_HOME]/conf/spark-defaults.conf):

	spark.yarn.principal<br>
	spark.yarn.keytab

	(If you do not have access to the above spark-defaults.conf file, optionally, you may add the lines to the Spark Interpreter through the Interpreter tab in the Zeppelin UI.

4. That's it. Play with Zeppelin
