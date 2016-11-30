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

var zeppelin = zeppelin || {};

/**
 * HandsonHelper class
 */
zeppelin.HandsonHelper = function(columns, rows, comment) {
  this.columns = columns || [];
  this.rows = rows || [];
  this.comment = comment || '';
};

zeppelin.HandsonHelper.prototype.getHandsonTableConfig = function(columns, columnNames, resultRows) {
  return {
    colHeaders: columnNames,
    data: resultRows,
    rowHeaders: false,
    stretchH: 'all',
    sortIndicator: true,
    columns: columns,
    columnSorting: true,
    contextMenu: false,
    manualColumnResize: true,
    manualRowResize: true,
    readOnly: true,
    readOnlyCellClassName: '',
    fillHandle: false,
    fragmentSelection: true,
    disableVisualSelection: true,
    cells: function(ro, co, pro) {
      var cellProperties = {};
      var colType = columns[co].type;
      cellProperties.renderer = function(instance, td, row, col, prop, value, cellProperties) {
        _cellRenderer(instance, td, row, col, prop, value, cellProperties, colType);
      };
      return cellProperties;
    },
    afterGetColHeader: function(col, TH) {
      var instance = this;
      var menu = _buildDropDownMenu(columns[col].type);
      var button = _buildTypeSwitchButton();

      _addButtonMenuEvent(button, menu);

      Handsontable.Dom.addEvent(menu, 'click', function(event) {
        if (event.target.nodeName === 'LI') {
          _setColumnType(columns, event.target.data.colType, instance, col);
        }
      });
      if (TH.firstChild.lastChild.nodeName === 'BUTTON') {
        TH.firstChild.removeChild(TH.firstChild.lastChild);
      }
      TH.firstChild.appendChild(button);
      TH.style['white-space'] = 'normal';
    }
  };
};

/*
** Private Service Functions
*/

function _addButtonMenuEvent(button, menu) {
  Handsontable.Dom.addEvent(button, 'click', function(event) {
    var changeTypeMenu;
    var position;
    var removeMenu;

    document.body.appendChild(menu);

    event.preventDefault();
    event.stopImmediatePropagation();

    changeTypeMenu = document.querySelectorAll('.changeTypeMenu');

    for (var i = 0, len = changeTypeMenu.length; i < len; i++) {
      changeTypeMenu[i].style.display = 'none';
    }
    menu.style.display = 'block';
    position = button.getBoundingClientRect();

    menu.style.top = (position.top + (window.scrollY || window.pageYOffset)) + 2 + 'px';
    menu.style.left = (position.left) + 'px';

    removeMenu = function(event) {
      if (menu.parentNode) {
        menu.parentNode.removeChild(menu);
      }
    };
    Handsontable.Dom.removeEvent(document, 'click', removeMenu);
    Handsontable.Dom.addEvent(document, 'click', removeMenu);
  });
}

function _buildDropDownMenu(activeCellType) {
  var menu = document.createElement('UL');
  var types = ['text', 'numeric', 'date'];
  var item;

  menu.className = 'changeTypeMenu';

  for (var i = 0, len = types.length; i < len; i++) {
    item = document.createElement('LI');
    if ('innerText' in item) {
      item.innerText = types[i];
    } else {
      item.textContent = types[i];
    }

    item.data = {'colType': types[i]};

    if (activeCellType === types[i]) {
      item.className = 'active';
    }
    menu.appendChild(item);
  }

  return menu;
}

function _buildTypeSwitchButton() {
  var button = document.createElement('BUTTON');

  button.innerHTML = '\u25BC';
  button.className = 'changeType';

  return button;
}

function _isNumeric(value) {
  if (!isNaN(value)) {
    if (value.length !== 0) {
      if (Number(value) <= Number.MAX_SAFE_INTEGER && Number(value) >= Number.MIN_SAFE_INTEGER) {
        return true;
      }
    }
  }
  return false;
}

function _cellRenderer(instance, td, row, col, prop, value, cellProperties, colType) {
  if (colType === 'numeric' && _isNumeric(value)) {
    cellProperties.format = '0,0.[00000]';
    td.style.textAlign = 'left';
    Handsontable.renderers.NumericRenderer.apply(this, arguments);
  } else if (value.length > '%html'.length && '%html ' === value.substring(0, '%html '.length)) {
    td.innerHTML = value.substring('%html'.length);
  } else {
    Handsontable.renderers.TextRenderer.apply(this, arguments);
  }
}

function _dateValidator(value, callback) {
  var d = moment(value);
  return callback(d.isValid());
}

function _numericValidator(value, callback) {
  return callback(_isNumeric(value));
}

function _setColumnType(columns, type, instance, col) {
  columns[col].type = type;
  _setColumnValidator(columns, col);
  instance.updateSettings({columns: columns});
  instance.validateCells(null);
  if (_isColumnSorted(instance, col)) {
    instance.sort(col, instance.sortOrder);
  }
}

function _isColumnSorted(instance, col) {
  return instance.sortingEnabled && instance.sortColumn === col;
}

function _setColumnValidator(columns, col) {
  if (columns[col].type === 'numeric') {
    columns[col].validator = _numericValidator;
  } else if (columns[col].type === 'date') {
    columns[col].validator = _dateValidator;
  } else {
    columns[col].validator = null;
  }
}

