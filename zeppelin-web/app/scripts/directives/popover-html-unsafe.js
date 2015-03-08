'use strict';

angular.module('zeppelinWebApp')
  .directive('popoverHtmlUnsafePopup', function() {
    return {
      restrict: 'EA',
      replace: true,
      scope: { title: '@', content: '@', placement: '@', animation: '&', isOpen: '&' },
      templateUrl: 'views/popover-html-unsafe-popup.html'
    };
  })
  
  .directive('popoverHtmlUnsafe', ['$tooltip', function($tooltip) {
    return $tooltip('popoverHtmlUnsafe', 'popover', 'click');
  }]);
