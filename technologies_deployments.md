<!--
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
{% include JB/setup %}
<div class="tech_deploy">
  <div class="container">
    <h2>Technologies</h2>
    <div class="border row">
      <div class="border col-md-4 col-sm-4" style="height:200px;">
        <div class="panel-content">
          <label class="_default-text" style="margin-top: 20px;">
            <img src="./assets/themes/zeppelin/img/spark_logo.jpg" width="140px">
          </label>
          <label class="_hover-text">
            <div style="min-height:140px; padding: 20px 10px 10px 10px;">
              Zeppelin supports Spark, PySpark, Spark R, Spark SQL with dependency loader.
            </div>
            <a href="/docs/latest/interpreter/spark.html" class="panel-button">USE NOW <span class="glyphicon glyphicon-chevron-right"></span></a>
          </label>                     
        </div>
      </div>
      <div class="border col-md-4 col-sm-4" style="height:200px;">
        <div class="panel-content">
          <label class="_default-text" style="font-size:45px;">SQL</label>
          <label class="_hover-text">
            <div style="min-height:140px;">
              Zeppelin lets you connect any JDBC data sources seamlessly. Postgresql, Mysql, MariaDB, Redshift, Apache Hive and so on.
            </div>
            <a href="/docs/latest/interpreter/jdbc.html" class="panel-button">USE NOW <span class="glyphicon glyphicon-chevron-right"></span></a>
          </label>
        </div>
      </div>
      <div class="border col-md-4 col-sm-4" style="height:200px;">
        <div class="panel-content">
          <label class="_default-text" style="margin-top:75px;">
            <img src="./assets/themes/zeppelin/img/python-logo-generic.svg" width="200px">
          </label>
          <label class="_hover-text">
            <div style="min-height:140px; padding: 10px;">
              Python is supported with Matplotlib, Conda, Pandas SQL and PySpark integrations.
            </div>
            <a href="/docs/latest/interpreter/python.html" class="panel-button">USE NOW <span class="glyphicon glyphicon-chevron-right"></span></a>
          </label>
        </div>
      </div>
    </div>
    <div class="col-md-12 col-sm-12 col-xs-12 text-center">
      <p class="bottom-text">
        See more details in Zeppelin supports 20+ different interpreters.
        <a href="/supported_interpreters.html">LEARN MORE <span class="glyphicon glyphicon-chevron-right" style="font-size:15px;"></span></a>
      </p>
    </div>    
    <hr />
    <div class="border row">
      <h2 style="padding-bottom: 8px;">Deployments</h2>
      <div class="border col-md-6 col-sm-6">
        <div class="panel-content-user">
          <label style="width: 100%;">
            <div style="position:relative;width:100%;text-align:center;">
              <span class="user-icon fa fa-user"></span>
              <span class="title-text">Single User</span>
            </div>
          </label>
          <label class="content-text">
            Local Spark, 6 Built-in visualizations, Display system, Dynamic form, Multiple backends are supported.<br/>
            <a href="/docs/latest/quickstart/install.html" class="user-button">LEARN MORE</a>
          </label>
        </div>
      </div>
      <div class="border col-md-6 col-sm-6">
        <div class="panel-content-user">
          <label style="width: 100%;">
            <div style="position:relative;width:100%;text-align:center;">
              <span class="user-icon fa fa-users"></span>
              <span class="title-text">Multi-User</span>
            </div>
          </label>
          <label class="content-text">
            Zeppelin supports Multi-user Support w/ LDAP. Let's configure Zeppelin for your yarn cluster.<br/>
            <a href="/docs/latest/setup/security/shiro_authentication.html" class="user-button">LEARN MORE</a>
          </label>                 
        </div>
      </div>
    </div>
  </div>
</div>
