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

/* Custom JavaScript code in the MarkDown docs */

function maybeScrollToHash() {
  var hash = window.location.hash
  
  if (hash && $(hash).length) {
    var newTop = $(hash).offset().top - 57;
    $(window).scrollTop(newTop);
  }
}

$(function() {
  // Display anchor links when hovering over headers. For documentation of the
  // configuration options, see the AnchorJS documentation.
  anchors.options = {
    placement: 'left'
  };
  anchors.add();

  $(window).bind('hashchange', function() {
    maybeScrollToHash();
  });

  $('[data-toggle="tooltip"]').tooltip();

  anchors.remove('.zeppelin-title');

});

$(document).click(function (event) {
  // custom navigation click event
  var clickover = $(event.target);
  var _opened = $(".navbar-collapse").hasClass("navbar-collapse in");
  if (_opened === true && !clickover.hasClass("navbar-toggle")) {
    $("button.navbar-toggle").click();
  }
});

// fix hover class on mobile TouchEvents
document.addEventListener("touchstart", function() {}, false);