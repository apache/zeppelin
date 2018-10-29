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
    <span class="newZeppelin center-block">Apache Zeppelin 0.8</span>
    <div class="border row">
      <div class="border col-md-4 col-sm-4">
        <h4>Python, improved</h4>
        <div>
          <p>
            IPython interpreter provides comparable user experience like Jupyter Notebook. For the details, click <a href="./docs/0.8.0/interpreter/python.html#ipython-support">here</a>.
          </p>
        </div>
      </div>
      <div class="border col-md-4 col-sm-4">
        <h4>Note improvements</h4>
        <p>
          This release includes <a href="./docs/0.8.0/usage/dynamic_form/intro.html#using-form-templates-scope-note">Note level dynamic form</a>, note revision comparator and ability to run paragraph sequentially, instead of simultaneous paragraph execution in previous releases.
        </p>
      </div>
      <div class="border col-md-4 col-sm-4">
        <h4>Inline configuration</h4>
        <div class="personal">
        <p>
          Generic <a href="./docs/0.8.0/usage/interpreter/overview.html#generic-confinterpreter">ConfInterpreter</a> provide a way configure interpreter inside each note.
        </p>
        </div>
      </div>
    </div>
    <div class="border row">
      <div class="border col-md-4 col-sm-4">
        <h4>Tab-key completion</h4>
        <p>
          Press `Tab` for code completion. (previous key combination `Ctrl+.` works as well)
        </p>
      </div>
      <div class="border col-md-4 col-sm-4">
        <h4>Interpreter lifecycle</h4>
        <div>
        <p>
          Interpreter lifecycle manager automatically terminate interpreter process on idle timeout. So resources are released when they're not in use. See <a href="./docs/0.8.0/usage/interpreter/overview.html#interpreter-lifecycle-management">here</a> for more details.
        </p>
        </div>
      </div>
      <div class="border col-md-4 col-sm-4">
        <h4>Helium online registry</h4>
        <p>
          This release includes online registry for Helium that can add custom visualization and more. <a href="./docs/0.8.0/development/helium/overview.html">learn more</a> about it.
        </p>
      </div>
    </div>
    <div class="col-md-12 col-sm-12 col-xs-12 text-center">
      <p style="text-align:center; margin-top: 32px; font-size: 14px; color: gray; font-weight: 200; font-style: italic; padding-bottom: 0;">See more details in 
        <a href="./releases/zeppelin-release-0.8.0.html">0.8 Release Note</a>
      </p>
    </div>
  </div>
</div>
