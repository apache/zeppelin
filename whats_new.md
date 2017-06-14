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

<div class="new">
  <div class="container">
    <h2>What's new in</h2>
    <span class="newZeppelin center-block">Apache Zeppelin 0.7</span>
    <div class="border row">
      <div class="border col-md-4 col-sm-4">
        <h4>Pluggable Visualization <br/> via Helium</h4>
        <div class="viz">
          <p>
            Load/unload Javascript 
            <a href="https://www.npmjs.com/" target="_blank">npm packages</a>
            like Zeppelin built-in chart using Helium framework.
            <a class="thumbnail" href="#thumb">
              See more in DEMO <span><img src="./assets/themes/zeppelin/img/helium.gif" /></span>
            </a>
            and
            <a href="./docs/0.7.0/development/writingzeppelinvisualization.html#how-it-works" target="_blank">Zeppelin Visualization: How it works?</a>
          </p>
        </div>
      </div>
      <div class="border col-md-4 col-sm-4">
        <h4>Multi-user Support Improvement</h4>
        <div class="multi">
        <p>
          Separate interpreter running scope 
          <span style="font-weight: 900; font-style: initial;">Per user</span> or 
          <span style="font-weight: 900; font-style: initial;">Per Note</span>.
          <a class="thumbnail text-center" href="#thumb">
            See more in DEMO.
            <span><img src="./assets/themes/zeppelin/img/scope.gif" style="max-width: 55vw" /></span>
          </a> <br/>
          Also running Zeppelin interpreter process as web front end user is available now. 
          <a class="thumbnail text-center" href="#thumb">
            See more in DEMO
            <span style="top: 230px;"><img src="./assets/themes/zeppelin/img/user-impersonation.gif" style="max-width: 55vw;" /></span>
          </a>
          and 
          <a href="./docs/0.7.0/manual/userimpersonation.html" target="_blank">Interpreter User Impersonation</a>.
        </p>
        </div>
      </div>
      <div class="border col-md-4 col-sm-4">
        <h4>New Note Mode - <br/> Personal Mode</h4>
        <div class="personal">
        <p>
          Personalize your analysis result by switching the note to Personal Mode. 
          (Collaboration Mode is default.) 
          <a class="thumbnail text-center personal" href="#thumb">
            See more in DEMO.
            <span><img src="./assets/themes/zeppelin/img/personalize.gif" /></span>
          </a>
        </p>
        </div>
      </div>
    </div>
    <div class="border row">
      <div class="border col-md-4 col-sm-4">
        <h4>Support Spark 2.1</h4>
        <p>
          The latest version of <a href="http://spark.apache.org/releases/spark-release-2-1-0.html" target="_blank">Apache Spark 2.1.0</a> is now available in Zeppelin.
        </p>
      </div>
      <div class="border col-md-4 col-sm-4">
        <h4>Improvement in Python</h4>
        <p>
          Integrated
          <a href="./docs/latest/interpreter/python.html#matplotlib-integration" target="_blank">Matplotlib</a>
          with Python & Pyspark interpreter. And 
          <a href="./docs/latest/interpreter/python.html#conda" target="_blank">Conda</a>
          is now available in Zeppelin. 
        </p>
      </div>
      <div class="border col-md-4 col-sm-4">
        <h4>New Interpreters</h4>
        <p>
        You can use
        <a href="https://beam.apache.org/" target="_blank">Apache Beam</a>, 
        <a href="https://github.com/spotify/scio" target="_blank">Scio</a>, and
        <a href="https://pig.apache.org/" target="_blank">Apache Pig</a> as backend interpreters from this release.
        </p>
      </div>
    </div>
    <div class="col-md-12 col-sm-12 col-xs-12 text-center">
      <p style="text-align:center; margin-top: 32px; font-size: 14px; color: gray; font-weight: 200; font-style: italic; padding-bottom: 0;">See more details in 
        <a href="./releases/zeppelin-release-0.7.0.html">0.7 Release Note</a>
      </p>
    </div>
  </div>
</div>
