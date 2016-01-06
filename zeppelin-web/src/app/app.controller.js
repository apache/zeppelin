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

angular.module('zeppelinWebApp').controller('MainCtrl', function($scope, $rootScope, $window, webNotification) {
  $rootScope.compiledScope = $scope.$new(true, $rootScope);
  $scope.looknfeel = 'default';
  $scope.windowFocus = true;
  $rootScope.hasNewStatus = false;

  var init = function() {
    $scope.asIframe = (($window.location.href.indexOf('asIframe') > -1) ? true : false);
  };

  init();

  $rootScope.$on('setIframe', function(event, data) {
    if (!event.defaultPrevented) {
      $scope.asIframe = data;
      event.preventDefault();
    }
  });

  $rootScope.$on('setLookAndFeel', function(event, data) {
    if (!event.defaultPrevented && data && data !== '' && data !== $scope.looknfeel) {
      $scope.looknfeel = data;
      event.preventDefault();
    }
  });

  // Set The lookAndFeel to default on every page
  $rootScope.$on('$routeChangeStart', function(event, next, current) {
    $rootScope.$broadcast('setLookAndFeel', 'default');
  });

  BootstrapDialog.defaultOptions.onshown = function() {
    angular.element('#' + this.id).find('.btn:last').focus();
  };

  $rootScope.$on('hasNewStatus', function(event, data) {
    if (!event.defaultPrevented && data && data === true && $rootScope.hasNewStatus === false) {
      $rootScope.hasNewStatus = true;

      // Send desktop notification
      webNotification.showNotification('Zeppelin Notification', {
         body: 'You have a job finished!',
         icon: 'my-icon.ico',
         onClick: function onNotificationClicked() {
           console.log('Notification clicked.');
         },
         autoClose: 4000 //auto close the notification after 2 seconds (you can manually close it via hide function)
      }, function onShow(error, hide) {
         if (error) {
             window.alert('Unable to show notification: ' + error.message);
         } else {
             setTimeout(function hideNotification() {
                 hide();
             }, 5000);
         }
      });

      event.preventDefault();
    }
  });

  $window.onblur = function (){
    $rootScope.windowFocus = false;
  };

  $window.onfocus = function (){
    $rootScope.windowFocus = true;
    if($rootScope.hasNewStatus === true) {
      $rootScope.hasNewStatus = false;
    }
  };
});
