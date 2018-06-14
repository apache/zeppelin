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

  angular.module('zeppelinWebApp').directive('iframeWrapper', function($q, $http, $compile, $sce) {
    return {
      restrict: 'EA',
      scope: {
        src: '=src',
        error: '@error',
      },
      link: function(scope, element, attrs) {
        scope.trustedUri = $sce.trustAsResourceUrl(scope.src);

        const iFrameHtml = '<iframe id="iframe-view" ng-src="{{trustedUri}}" frameborder="0" height='+(window.innerHeight-150)+' width='+(window.innerWidth-100)+' allowfullscreen></iframe>';
        const markup = $compile(iFrameHtml)(scope);
        element.empty();
        element.append(markup);
      },
    };
  });
