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

  angular.module('zeppelinWebApp').service('SparkUIUtils', function($http, baseUrlSrv, ngToast, $timeout, $sce) {
    this.Constants = {
      'X-Frame-Params': 'spark.ui.allowFramingFrom',
    };

    this.showWebViewInIframe = function(url, label) {
      const trustedUri = $sce.trustAsResourceUrl(url);
      const iFrameHtml = '<iframe id="iframe-view" src='+trustedUri+' frameborder="0" '
      +'height='+(window.innerHeight-225)+' width='+(window.innerWidth-100)+' allowfullscreen>'
      +'</iframe>';

      const options = {
        closable: true,
        closeByBackdrop: false,
        closeByKeyboard: false,
        size: 'modal-xl',
        title: label ? label : '',
        message: iFrameHtml,
        buttons: [{
          label: 'Close',
          action: function(dialog) {
            dialog.close();
          },
        }],
      };

      BootstrapDialog.show(options);
    };

    this.getInterpreterSettings = function(scope, callBack) {
      $http.get(baseUrlSrv.getRestApiBase() + '/interpreter/setting')
        .then(function(res) {
          scope.interpreterSettings = res.data.body;
          if(callBack) {
            callBack();
          }
          scope.$broadcast('interpreterSettingChange', scope.interpreterSettings);
        }).catch(function(res) {
          if (res.status === 401) {
            ngToast.danger({
              content: 'You don\'t have permission on this page',
              verticalPosition: 'bottom',
              timeout: '3000',
            });
            setTimeout(function() {
              window.location = baseUrlSrv.getBase();
            }, 3000);
          }
          console.log('Error %o %o', res.status, res.data ? res.data.message : '');
        });
    };

    this.checkXframeSupport = function(scope, interpreterArr, type) {
      const interpreterObj = interpreterArr.find(function(inter) {
        return inter.name.includes('spark');
      });
      if(interpreterObj !== undefined && interpreterObj.properties.hasOwnProperty(this.Constants['X-Frame-Params'])) {
        if(type) {
          const index = interpreterArr.findIndex(function(inter) {
            return inter === interpreterObj;
          });
          scope.interpreterSettings[index].hasXframe = true;
        } else {
          scope.paragraph.hasXframe = true;
        }
      }
    };
  });
