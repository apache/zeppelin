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
    <span class="newZeppelin center-block">Apache Zeppelin 0.10</span>
    <div class="border row">
      <div class="border col-md-4 col-sm-4">
        <h4>Zeppelin SDK</h4>
        <div>
          <p>
            Not only you can use Zeppelin as interactive notebook, you can also use it as JobServer via Zeppelin SDK (<a href="./docs/latest/usage/zeppelin_sdk/client_api.html">client api</a> 
& <a href="./docs/latest/usage/zeppelin_sdk/session_api.html">session api</a>)
          </p>
        </div>
      </div>

      <div class="border col-md-4 col-sm-4">
        <h4>Spark Interpreter Improved</h4>
        <div>
          <p>
             Spark interpreter provides comparable Python & R user experience like Jupyter Notebook. For the details, click <a href="./docs/latest/interpreter/spark.html">here</a>.
         </p>
        </div>
      </div>

      <div class="border col-md-4 col-sm-4">
        <h4>Flink Interpreter Improved</h4>
        <div>
            <p>
                Flink interpreter is refactored, supports Scala, Python & SQL. Flink 1.10 and afterwards (Scala 2.11 & 2.12) are all supported. <br/>
                For the details, click <a href="./docs/latest/interpreter/flink.html">here</a>. 
            </p>
        </div>
      </div>

    </div>


    <div class="border row">
      <div class="border col-md-4 col-sm-4">
        <h4>Yarn Interpreter Mode</h4>
        <div>
            <p>
              You can run interpreter in yarn cluster, e.g. you can run <a href="./docs/latest/interpreter/python.html#run-python-interpreter-in-yarn-cluster">Python interpreter in yarn</a> and <a href="./docs/latest/interpreter/r.html#run-r-in-yarn-cluster">R interpreter in yarn</a>.
            </p>
        </div>
      </div>

      <div class="border col-md-4 col-sm-4">
        <h4>Inline Configuration</h4>
        <div class="personal">
            <p>
              Generic <a href="./docs/latest/usage/interpreter/overview.html#generic-confinterpreter">ConfInterpreter</a> provide a way configure interpreter inside each note.
            </p>
        </div>
      </div>

      <div class="border col-md-4 col-sm-4">
        <h4>Interpreter Lifecycle Management</h4>
        <div>
            <p>
              Interpreter lifecycle manager automatically terminate interpreter process on idle timeout. So resources are released when they're not in use. See <a href="./docs/latest/usage/interpreter/overview.html#interpreter-lifecycle-management">here</a> for more details.
            </p>
        </div>
      </div>

    </div>
    </div>

    <div class="col-md-12 col-sm-12 col-xs-12 text-center">
      <p style="text-align:center; margin-top: 32px; font-size: 14px; color: gray; font-weight: 200; font-style: italic; padding-bottom: 0;">See more details in 
        <a href="./releases/zeppelin-release-0.10.0.html">0.10 Release Note</a>
      </p>
    </div>

  </div>
</div>
