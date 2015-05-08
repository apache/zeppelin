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

angular.module('zeppelinWebApp').directive('resizable', function () {
    var resizableConfig = {
        autoHide: true,
        handles: 'se',
        helper: 'resizable-helper',
        minHeight:100,
        grid: [10000, 10]  // allow only vertical
    };

    return {
        restrict: 'A',
        scope: {
            callback: '&onResize'
        },
        link: function postLink(scope, elem, attrs) {
            attrs.$observe('allowresize', function(isAllowed) {
                if (isAllowed === 'true') {
                    elem.resizable(resizableConfig);
                    elem.on('resizestop', function () {
                        if (scope.callback) { scope.callback(); }
                    });
                }
            });
        }
    };
});
