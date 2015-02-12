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
