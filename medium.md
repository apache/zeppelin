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

<div ng-app="app">
  <div ng-controller="MediumCtrl">
    <div class="blog">
      <div class="box container">
        <h2>Apache Zeppelin Stories</h2>
        <div class="blogContentBox">
          <div class="row blogList"
           ng-repeat="post in postInfoArray | orderBy: 'post' | limitTo: 5">
          <div class="col-md-12">
            <div class="blogHead">
              <div class="blogTitle">
                <a ng-href="{% raw %}{{post.link}}{% endraw %}"
                   target="_blank">
                   {% raw %}{{post.title}}{% endraw %}
                </a>
              </div>
            </div>
            <div class="blogDescription">
              {% raw %}{{post.description}}{% endraw %}
            </div>
            <div class="blogPublished">
              <i class="fa fa-calendar" aria-hidden="true"></i> &nbsp;
              {% raw %}{{post.created}}{% endraw %}
            </div>
          </div>
        </div>
      </div>
      <div class="col-md-12 col-sm-12 col-xs-12 text-center twitterBtn">
        <p style="text-align:center; margin-top: 32px; font-size: 12px; color: gray; font-weight: 200; font-style: italic; padding-bottom: 0;">See more posts or</p>
        <a href="https://medium.com/apache-zeppelin-stories" target="_blank" class="btn btn-primary btn-lg round" role="button">
          Share your story at &nbsp;
          <i class="fa fa-medium fa-lg" aria-hidden="true"></i>
        </a>
      </div>
    </div>
  </div>
  <hr>
</div>