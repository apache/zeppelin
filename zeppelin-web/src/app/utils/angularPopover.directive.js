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

angular.module('zeppelinWebApp').directive('angularPopover', function($window) {
  return {
    restrict: 'A',
    transclude: true,
    scope: true,
    template: '<div class="angular-popover-container">'
                +'<div class="angular-popover hide-popover-element">'
                +'<div ng-if="isTemplateUrl()" ng-include="getContentPopover()" class="angular-popover-template"></div>'
                +'<div ng-if="!isTemplateUrl()" class="angular-popover-template"></div>'
                +'</div>'
                +'<div class="angular-popover-triangle hide-popover-element" ng-class="getTriangleClass()"></div>'
                +'</div><ng-transclude></ng-transclude>',
    link: function(scope, element, attrs) {
      const elementPositionProperty = $window.getComputedStyle(element[0]).position;
      if(elementPositionProperty === 'static') {
        element[0].style.position = 'relative';
      }
      const popoverContainer = element[0].querySelector('.angular-popover-container');
      let popover = null;
      let parentHeight = 0;
      let parentWidth = 0;
      let popoverHeight = 0;
      let popoverWidth = 0;
      let triangle = null;
      const triangleDivSide = 15;
      const triangleRectDivSide = 30;
      const triangleHeight = Math.sqrt(triangleDivSide * triangleDivSide / 2);
      const mode = attrs.mode || 'click';
      const closeOnClick = attrs.closeOnClick === undefined
      ? (mode === 'click' ? true : false) : (attrs.closeOnClick === 'true');
      const closeOnMouseleave = attrs.closeOnMouseleave === undefined
      ? (mode === 'mouseover' ? true : false) : (attrs.closeOnMouseleave === 'true');
      scope.getContentPopover = function() {
        return attrs.templateUrl;
      };
      scope.isTemplateUrl = function() {
        if(attrs.templateUrl) {
          return true;
        }
        return false;
      };
      scope.getTriangleClass = function() {
        return 'angular-popover-triangle-' + attrs.direction;
      };

      if(closeOnMouseleave) {
        element[0].addEventListener('mouseleave', function() {
          popover.classList.add('hide-popover-element');
          triangle.classList.add('hide-popover-element');
        });
      }

      if(mode !== 'click' && closeOnClick) {
        element[0].addEventListener('click', function() {
          popover.classList.add('hide-popover-element');
          triangle.classList.add('hide-popover-element');
        });
      }

      element[0].addEventListener(mode, function() {
        parentHeight = element[0].offsetHeight;

        popoverContainer.style.top = parentHeight + 'px';
        parentWidth = element[0].offsetWidth;
        popover = element[0].querySelector('.angular-popover');
        triangle = element[0].querySelector('.angular-popover-triangle');

        if(mode === 'click' && closeOnClick) {
          popover.classList.toggle('hide-popover-element');
          triangle.classList.toggle('hide-popover-element');
        } else if(mode === 'click' && !closeOnClick) {
          popover.classList.remove('hide-popover-element');
          triangle.classList.remove('hide-popover-element');
        } else if(popover.classList.contains('hide-popover-element')) {
          popover.classList.remove('hide-popover-element');
          triangle.classList.remove('hide-popover-element');
        }

        if(!popover.classList.contains('hide-popover-element')) {
          createPopover();
        }
      });

      const createPopover = function() {
        if(attrs.template) {
          const templateElement = element[0].querySelector('.angular-popover-template');
          templateElement.innerHTML = attrs.template;
        }

        if(attrs.backgroundColor) {
          popover.style['background-color'] = attrs.backgroundColor;
        }

        if(attrs.textColor) {
          popover.style.color = attrs.textColor;
        }

        if(attrs.padding) {
          popover.style.padding = attrs.padding;
        }

        popoverHeight = popover.offsetHeight;
        popoverWidth = popover.offsetWidth;

        switch(attrs.direction) {
          case 'top':
            popover.style.top = (-parentHeight - popoverHeight - triangleHeight) + 'px';
            popover.style.left = ((parentWidth - popoverWidth)/2) + 'px';
            triangle.style.top = (-parentHeight - triangleHeight) + 'px';
            triangle.style.left = ((parentWidth - triangleRectDivSide)/2) + 'px';
            break;

          case 'bottom':
            popover.style.top = triangleHeight + 'px';
            popover.style.left = ((parentWidth - popoverWidth)/2) + 'px';
            triangle.style.top = -(triangleRectDivSide - triangleHeight) + 'px';
            triangle.style.left = ((parentWidth - triangleRectDivSide)/2) + 'px';
            break;

          case 'right':
            popover.style.top = ((parentHeight - popoverHeight)/2 - parentHeight) + 'px';
            popover.style.left = parentWidth + triangleHeight + 'px';
            triangle.style.top = ((parentHeight - triangleRectDivSide)/2 - parentHeight) + 'px';
            triangle.style.left = (parentWidth - (triangleRectDivSide - triangleHeight)) + 'px';
            break;

          case 'left':
            popover.style.top = ((parentHeight - popoverHeight)/2 - parentHeight) + 'px';
            popover.style.right = triangleHeight + 'px';
            triangle.style.top = ((parentHeight - triangleRectDivSide)/2 - parentHeight) + 'px';
            triangle.style.left = -triangleHeight + 'px';
            break;
        }
      };
    },
  };
});
