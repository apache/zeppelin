'use strict';

/**
 * @ngdoc directive
 * @name zeppelinWeb2App.directive:delete
 * @description
 * # ngDelete
 */
angular.module('zeppelinWeb2App').directive('ngDelete', function() {
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
