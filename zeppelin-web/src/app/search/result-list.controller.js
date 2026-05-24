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
    // Check interpreter prefix first — this is reliable
    if (/^%(\w*\.)?sql/i.test(text)) {
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
    // Fall back to conservative heuristics only if no prefix present.
    // Require SELECT ... FROM pattern to avoid false positives from Python
    // "from ... import" or markdown containing words like "create".
    if (!text.startsWith('%')) {
      if (/\bSELECT\b/i.test(text) && /\bFROM\b/i.test(text)) {
        return 'sql';
      }
      if (/^(import |from \w+ import |def |class )/m.test(text)) {
        return 'python';
      }
    }
    return '';
  }

  // HTML-escape raw text so '<' in source (e.g. WHERE id < 100) is not parsed
  // as a DOM tag, then promote only the Lucene <B>/<\/B> markers to <mark>.
  function highlightToMark(text) {
    return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/&lt;B&gt;/gi, '<mark>')
      .replace(/&lt;\/B&gt;/gi, '</mark>');
  }

  results.$promise.then(function(result) {
    $scope.notes = result.body.map(function(note) {
      if (!/\/paragraph\//.test(note.id)) {
        return note;
      }
      note.id = note.id.replace('paragraph/', '?paragraph=') +
        '&term=' + $routeParams.searchTerm;

      let code = (note.snippet || '').replace(/<B>/g, '').replace(/<\/B>/g, '');

      let tables = (note.tables || '').trim().split(/\s+/).filter(function(t) {
        return t;
      }).join(', ');

      note.codeText = code;
      note.codeHtml = highlightToMark(note.snippet || '');
      note.titleHtml = highlightToMark(note.title || '');
      note.outputText = note.output || '';
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
