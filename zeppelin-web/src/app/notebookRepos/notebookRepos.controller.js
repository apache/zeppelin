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
(function() {

  angular.module('zeppelinWebApp').controller('NotebookReposCtrl', NotebookReposCtrl);

  NotebookReposCtrl.$inject = ['$http', 'baseUrlSrv', 'ngToast'];

  function NotebookReposCtrl($http, baseUrlSrv, ngToast) {
    var vm = this;
    vm.notebookRepos = [];
    vm.showDropdownSelected = showDropdownSelected;
    vm.saveNotebookRepo = saveNotebookRepo;

    _init();

    // Public functions

    function saveNotebookRepo(valueform, repo, data) {
      console.log('data %o', data);
      $http.put(baseUrlSrv.getRestApiBase() + '/notebook-repositories', {
        'name': repo.className,
        'settings': data
      }).success(function(data) {
        var index = _.findIndex(vm.notebookRepos, {'className': repo.className});
        if (index >= 0) {
          vm.notebookRepos[index] = data.body;
          console.log('repos %o, data %o', vm.notebookRepos, data.body);
        }
        valueform.$show();
      }).error(function() {
        ngToast.danger({
          content: 'We couldn\'t save that NotebookRepo\'s settings',
          verticalPosition: 'bottom',
          timeout: '3000'
        });
        valueform.$show();
      });

      return 'manual';
    }

    function showDropdownSelected(setting) {
      var index = _.findIndex(setting.value, {'value': setting.selected});
      if (index < 0) {
        return 'No value';
      } else {
        return setting.value[index].name;
      }
    }

    // Private functions

    function _getInterpreterSettings() {
      $http.get(baseUrlSrv.getRestApiBase() + '/notebook-repositories')
      .success(function(data, status, headers, config) {
        vm.notebookRepos = data.body;
        console.log('ya notebookRepos %o', vm.notebookRepos);
      }).error(function(data, status, headers, config) {
        if (status === 401) {
          ngToast.danger({
            content: 'You don\'t have permission on this page',
            verticalPosition: 'bottom',
            timeout: '3000'
          });
          setTimeout(function() {
            window.location.replace('/');
          }, 3000);
        }
        console.log('Error %o %o', status, data.message);
      });
    }

    function _init() {
      _getInterpreterSettings();
    };
  }

})();
