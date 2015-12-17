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
  $translateProvider.translations('fr', {
    'app.home': {
      'WELCOME_MSG': 'Bienvenue sur Zeppelin!',
      'ZEPPELIN_DESC': 'Zeppelin est une application web de gestion de notebook, permettant l\'analyse de données\
        de façon interactive.<br/>Vous pouvez créer de superbes documents interactifs et collaboratifs\
        pour vos données, en utilisant des requêtes SQL, du code et bien plus!<br/><br/>',
        'sections': {
          'community': {
            'TITLE': 'Communauté',
            'DESC': 'N\'hésitez pas à nous aider à améliorer Zeppelin, <br/>\
              Toute contribution est la bienvenue!<br/><br/>',
            'MAILINGLIST_MENU': 'Mailing list',
            'ISSUETRACKING_MENU': 'Issues tracking',
            'GITHUB_MENU': 'Github'
          },
          'help': {
            'TITLE': 'Aide',
            'DESC': 'Commencez par visiter la',
            'ZEPPELINDOC_LINK': 'documentation de Zeppelin'
          },
          'notebooks': {
            'TITLE': 'Notebooks',
            'IMPORT_MENU': 'Importer une note',
            'CREATE_MENU': 'Créer une note'
          }
        }
    },
    'components.navbar': {
      'MENU_NOTEBOOK': 'Notebooks',
      'MENU_INTERPRETER': 'Interpreters',
      'STATUS_CONNECTED': 'Connecté',
      'STATUS_DISCONNECTED': 'Déconnecté',
      'CREATE_MENU': 'Créer une note'
    }
  });
});
