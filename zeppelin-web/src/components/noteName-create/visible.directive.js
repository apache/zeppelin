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

angular.module('zeppelinWebApp').directive('modalvisible', function () {
    return {
        restrict: 'A',
        scope: {
	        	preVisibleCallback: '&previsiblecallback',
	        	postVisibleCallback: '&postvisiblecallback',
	        	targetinput: '@targetinput'
        	   },
        link: function(scope, elem, attrs) {
        	// Add some listeners
    		var previsibleMethod = scope.preVisibleCallback;
    		var postVisibleMethod = scope.postVisibleCallback;
    		elem.on('show.bs.modal',function(e) {
    			var relatedTgt = angular.element(e.relatedTarget);
    			var clone = relatedTgt.data('clone');
    			var cloneNote = clone ? true : false;
    			previsibleMethod()(cloneNote);
    		});
    		elem.on('shown.bs.modal', function(e) {
    			if(scope.targetinput) {
    			  angular.element(e.target).find('input#' + scope.targetinput ).select();
    			}
    			postVisibleMethod();
    		});
        }
    };
});
