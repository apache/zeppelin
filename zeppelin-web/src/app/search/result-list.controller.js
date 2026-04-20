/*
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

angular.module('zeppelinWebApp').controller('SearchResultCtrl', SearchResultCtrl);

function SearchResultCtrl($scope, $routeParams, searchService) {
  'ngInject';

  $scope.isResult = true;
  $scope.searchTerm = $routeParams.searchTerm;
  let results = searchService.search({'q': $routeParams.searchTerm}).query();

  function detectLang(text) {
    if (!text) {
      return '';
    }
    if (/select|insert|create|from|where/i.test(text)) {
      return 'sql';
    }
    if (/^%(\w*\.)?py/i.test(text)) {
      return 'python';
    }
    if (/^%md/i.test(text)) {
      return 'md';
    }
    if (/^%sh/i.test(text)) {
      return 'sh';
    }
    if (/import |def |class /i.test(text)) {
      return 'python';
    }
    return '';
  }

  results.$promise.then(function(result) {
    $scope.notes = result.body.map(function(note) {
      if (!/\/paragraph\//.test(note.id)) {
        return note;
      }
      note.id = note.id.replace('paragraph/', '?paragraph=') +
        '&term=' + $routeParams.searchTerm;

      // Parse header into tables and output
      let tables = '';
      let output = '';
      if (note.header) {
        note.header.split('\n').forEach(function(line) {
          if (line.indexOf('📊') === 0) {
            tables += (tables ? ', ' : '') + line.substring(2).trim();
          } else if (line.trim()) {
            output += (output ? '\n' : '') + line;
          }
        });
      }

      // Strip <B> tags from snippet
      let code = (note.snippet || '').replace(/<B>/g, '').replace(/<\/B>/g, '');

      note.codeText = code;
      note.outputText = output;
      note.tablesText = tables;
      note.langBadge = detectLang(code);

      return note;
    });

    $scope.isResult = $scope.notes.length > 0;

    $scope.$on('$routeChangeStart', function(event, next, current) {
      if (next.originalPath !== '/search/:searchTerm') {
        searchService.searchTerm = '';
      }
    });
  });
}
