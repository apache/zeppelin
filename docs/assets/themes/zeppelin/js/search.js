---

---
jQuery(function() {
  window.idx = lunr(function () {
    this.field('id');
    this.field('title');
    this.field('content', { boost: 10 });
    this.field('group');
  });

  window.data = $.getJSON('/search_data.json');
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

  function display_search_results(results) {
    var $search_results = $("#search_results");
    var zeppelin_version = {{site.ZEPPELIN_VERSION | jsonify}};

    window.data.then(function(loaded_data) {
      if (results.length) {
        $search_results.empty();
        $search_results.prepend('<p class="">Found '+results.length+' result(s)</p><hr>');

        results.forEach(function(result) {
          var item = loaded_data[result.ref];
          var appendString = '<a href="'+item.url+'">'+item.title+'</a><div class="link">'+'https://zeppelin.apache.org/docs/'+zeppelin_version+item.url+'</div><p>'+item.excerpt+'</p><br/>';

          $search_results.append(appendString);
        });
      } else {
        $search_results.html('<p>No results found.</p>');
      }
    });
  }
});
