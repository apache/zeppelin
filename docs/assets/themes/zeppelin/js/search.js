---

---
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
jQuery(function() {
  window.idx = lunr(function () {
    this.field('id');
    this.field('title');
    this.field('content', { boost: 10 });
    this.field('group');
  });

  window.data = $.getJSON('search_data.json');
  window.data.then(function(loaded_data){
    $.each(loaded_data, function(index, value){
      window.idx.add(
        $.extend({ "id": index }, value)
      );
    });
  });

  $("#site_search").keyup(function(event){
    event.preventDefault();
    var query = $("#search_box").val();
    var results = window.idx.search(query);
    display_search_results(results);
  });

  $('html').bind('keypress', function(event){
    // Since keyup() is operated at the above, disable 'Enter Key' press.   
     if(event.keyCode == 13) {
        return false;
     }
  });

  function display_search_results(results) {
    var $search_results = $("#search_results");
    var zeppelin_version = {{site.ZEPPELIN_VERSION | jsonify}};
    var base_url = {{site.JB.BASE_PATH | jsonify}};
    var prod_url = {{site.production_url | jsonify}};

    window.data.then(function(loaded_data) {
      if (results.length) {
        $search_results.empty();
        $search_results.prepend('<p class="">Found '+results.length+' result(s)</p><hr>');

        results.forEach(function(result) {
          var item = loaded_data[result.ref];
          var appendString = '<a href="'+base_url+item.url.trim()+'">'+item.title+'</a><div class="link">'+prod_url+base_url+item.url.trim()+'</div><p>'+item.excerpt+'</p><br/>';

          $search_results.append(appendString);
        });
      } else {
        $search_results.html('<p>Your search did not match any documents.<br/>Make sure that all words are spelled correctly or try more general keywords.</p>');
      }
    });
  }
});
