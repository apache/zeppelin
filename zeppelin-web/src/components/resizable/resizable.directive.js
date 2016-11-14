/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
'use strict';
(function() {

  angular.module('zeppelinWebApp').directive('resizable', resizable);

  function resizable() {
    var resizableConfig = {
      autoHide: true,
      handles: 'se',
      helper: 'resizable-helper',
      stop: function() {
        angular.element(this).css({'width': '100%', 'height': '100%'});
      }
    };

    return {
      restrict: 'A',
      scope: {
        callback: '&onResize'
      },
      link: function postLink(scope, elem, attrs) {
        attrs.$observe('resize', function(resize) {
          var resetResize = function(elem, resize) {
            var colStep = window.innerWidth / 12;
            elem.off('resizestop');
            var conf = angular.copy(resizableConfig);
            if (resize.graphType === 'TABLE' || resize.graphType === 'TEXT') {
              conf.grid = [colStep, 10];
              conf.minHeight = 100;
            } else {
              conf.grid = [colStep, 10000];
              conf.minHeight = 0;
            }
            conf.maxWidth = window.innerWidth;

            elem.resizable(conf);
            elem.on('resizestop', function() {
              if (scope.callback) {
                var height = elem.height();
                if (height < 50) {
                  height = 300;
                }
                scope.callback({width: Math.ceil(elem.width() / colStep), height: height});
              }
            });
          };

          resize = JSON.parse(resize);
          if (resize.allowresize === 'true') {
            resetResize(elem, resize);
            angular.element(window).resize(function() {
              resetResize(elem, resize);
            });
          }
        });
      }
    };
  }

})();
