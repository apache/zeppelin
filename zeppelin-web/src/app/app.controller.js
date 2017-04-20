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

angular.module('zeppelinWebApp').controller('MainCtrl', MainCtrl)

function MainCtrl ($scope, $rootScope, $window, arrayOrderingSrv) {
  'ngInject'

  $scope.looknfeel = 'default'

  let init = function () {
    $scope.asIframe = (($window.location.href.indexOf('asIframe') > -1) ? true : false)
  }

  init()

  $rootScope.$on('setIframe', function (event, data) {
    if (!event.defaultPrevented) {
      $scope.asIframe = data
      event.preventDefault()
    }
  })

  $rootScope.$on('setLookAndFeel', function (event, data) {
    if (!event.defaultPrevented && data && data !== '' && data !== $scope.looknfeel) {
      $scope.looknfeel = data
      event.preventDefault()
    }
  })

  // Set The lookAndFeel to default on every page
  $rootScope.$on('$routeChangeStart', function (event, next, current) {
    $rootScope.$broadcast('setLookAndFeel', 'default')
  })

  $rootScope.noteName = function (note) {
    if (!_.isEmpty(note)) {
      return arrayOrderingSrv.getNoteName(note)
    }
  }

  BootstrapDialog.defaultOptions.onshown = function () {
    angular.element('#' + this.id).find('.btn:last').focus()
  }

  // Remove BootstrapDialog animation
  BootstrapDialog.configDefaultOptions({animate: false})
}
