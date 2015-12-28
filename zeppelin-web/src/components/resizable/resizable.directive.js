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

angular.module('zeppelinWebApp').directive('resizable', function() {
  var colStep = window.innerWidth / 12;

  var resizableConfig = {
    autoHide: true,
    handles: 'se',
    helper: 'resizable-helper',
    minHeight: 100,
    grid: [colStep, 10000],  // allow only vertical
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
        resize = JSON.parse(resize);
        if (resize.allowresize === 'true') {
          elem.off('resizestop');
          var conf = angular.copy(resizableConfig);
          if (resize.graphType === 'TABLE') {
            conf.grid = [colStep, 10];
          }
          elem.resizable(conf);
          elem.on('resizestop', function() {
            if (scope.callback) {
              scope.callback({width: Math.ceil(elem.width() / colStep)});
            }
          });
        }
      });
    }
  };
});
