'use strict';

angular.module('zeppelinWebApp').directive('resizable', function () {
    var resizableConfig = {
        autoHide: true,
        handles:"se",
        minHeight:100,
        grid: [10000, 1]  // allow only vertical
    };

    return {
        restrict: 'A',
        scope: {
            callback: '&onResize'
        },
        link: function postLink(scope, elem, attrs) {
            elem.resizable(resizableConfig);
            elem.on('resizestop', function (evt, ui) {
                if (scope.callback) { scope.callback(); }
            });
        }
    };
});
