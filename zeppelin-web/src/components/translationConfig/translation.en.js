/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
'use strict';

angular.module('zeppelinWebApp')
.config(function ($translateProvider) {
  $translateProvider.translations('en', {
    'app.home': {
      'WELCOME_MSG': 'Welcome to Zeppelin!',
      'ZEPPELIN_DESC': 'Zeppelin is web-based notebook that enables interactive data analytics.<br/>\
        You can make beautiful data-driven, interactive, collaborative document with SQL, code and even more!<br/><br/>',
      'sections': {
        'community': {
          'TITLE': 'Community',
          'DESC': 'Please feel free to help us to improve Zeppelin, <br>\
            Any contribution are welcome!<br/><br/>',
          'MAILINGLIST_MENU': 'Mailing list',
          'ISSUETRACKING_MENU': 'Issues tracking',
          'GITHUB_MENU': 'Github'
        },
        'help': {
          'TITLE': 'Help',
          'DESC': 'Get started with',
          'ZEPPELINDOC_LINK': 'Zeppelin documentation'
        },
        'notebooks': {
          'TITLE': 'Notebook',
          'IMPORT_MENU': 'Import note',
          'CREATE_MENU': 'Create new note'
        }
      }
    },
    'app.interpreter': {
      'card': {
        'EDIT_BTN': 'edit',
        'RESTART_BTN': 'restart',
        'REMOVE_BTN': 'remove',
        'TABLE_TITLE': 'Properties',
        'TABLE_NAME_COL': 'name',
        'TABLE_ACTION_COL': 'action',
        'TABLE_VALUE_COL': 'value',
        'EMPTY_PROPERTIES': 'Currently there are no properties set for this interpreter'
      },
      'createform': {
        'TITLE': 'Create new interpreter',
        'NAME_LABEL': 'Name',
        'INTERPRETER_LABEL': 'Interpreter',
        'TABLE_DESC_COL': 'description',
        'SAVE_BTN': 'Save',
        'CANCEL_BTN': 'Cancel'
      },
      'editForm': {
        'SAVE_BTN': 'Save',
        'CANCEL_BTN': 'Cancel'
      },
      'header': {
        'TITLE': 'Interpreters',
        'DESC': 'Manage interpreters settings. You can create create / remove settings.\
          Note can bind/unbind these interpreter settings.',
        'CREATE_BTN': 'Create'
      }
    },
    'components.navbar': {
      'MENU_NOTEBOOK': 'Notebook',
      'MENU_INTERPRETER': 'Interpreters',
      'STATUS_CONNECTED': 'Connected',
      'STATUS_DISCONNECTED': 'Disconnected',
      'CREATE_MENU': 'Create new note'
    }
  });
});
