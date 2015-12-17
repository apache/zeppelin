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
        You can make beautiful data-driven, interactive, collaborative document with SQL, code and even more!<br>'
    },
    'components.navbar': {
      'MENU_NOTEBOOK': 'Notebooks',
      'MENU_INTERPRETER': 'Interpreters',
      'STATUS_CONNECTED': 'Connected',
      'STATUS_DISCONNECTED': 'Disconnected'
    }
  });
});
