'use strict';

/**
 * @ngdoc directive
 * @name zeppelinWebApp.directive:delete
 * @description
 * # ngDelete
 */
angular.module('zeppelinWebApp').directive('ngDelete', function() {
  return function(scope, element, attrs) {
    element.bind('keydown keypress', function(event) {
      if (event.which === 27 || event.which === 46) {
        scope.$apply(function() {
          scope.$eval(attrs.ngEnter);
        });
        event.preventDefault();
      }
    });
  };
});
