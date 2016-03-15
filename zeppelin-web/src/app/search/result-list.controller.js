/* jshint loopfunc: true */
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
'use strict';

angular
  .module('zeppelinWebApp')
  .controller('SearchResultCtrl', function($scope, $routeParams, searchService) {

  var results = searchService.search({'q': $routeParams.searchTerm}).query();

  results.$promise.then(function(result) {
    $scope.notes = result.body.map(function(note) {
      // redirect to notebook when search result is a notebook itself,
      // not a paragraph
      if (!/\/paragraph\//.test(note.id)) {
        return note;
      }

      note.id = note.id.replace('paragraph/', '?paragraph=') +
        '&term=' +
        $routeParams.searchTerm;

      return note;
    });
  });

  $scope.page = 0;
  $scope.allResults = false;

  $scope.highlightSearchResults = function(note) {
    return function(_editor) {
      function getEditorMode(text) {
        var editorModes = {
          'ace/mode/scala': /^%spark/,
          'ace/mode/sql': /^%(\w*\.)?\wql/,
          'ace/mode/markdown': /^%md/,
          'ace/mode/sh': /^%sh/
        };

        return Object.keys(editorModes).reduce(function(res, mode) {
          return editorModes[mode].test(text)? mode : res;
        }, 'ace/mode/scala');
      }

      var Range = ace.require('ace/range').Range;

      _editor.setOption('highlightActiveLine', false);
      _editor.$blockScrolling = Infinity;
      _editor.setReadOnly(true);
      _editor.renderer.setShowGutter(false);
      _editor.setTheme('ace/theme/chrome');
      _editor.getSession().setMode(getEditorMode(note.text));

      function getIndeces(term) {
        return function(str) {
          var indeces = [];
          var i = -1;
          while((i = str.indexOf(term, i + 1)) >= 0) {
            indeces.push(i);
          }
          return indeces;
        };
      }

      var lines = note.snippet
        .split('\n')
        .map(function(line, row) {
          var match = line.match(/<B>(.+?)<\/B>/);

        // return early if nothing to highlight
          if (!match) {
            return line;
          }

          var term = match[1];
          var __line = line
            .replace(/<B>/g, '')
            .replace(/<\/B>/g, '');

          var indeces = getIndeces(term)(__line);

          indeces.forEach(function(start) {
            var end = start + term.length;
            _editor
              .getSession()
              .addMarker(
                new Range(row, start, row, end),
                'search-results-highlight',
                'line'
              );
          });

          return __line;
        });

      // resize editor based on content length
      _editor.setOption(
        'maxLines',
        lines.reduce(function(len, line) {return len + line.length;}, 0)
      );

      _editor.getSession().setValue(lines.join('\n'));

    };
  };

});
