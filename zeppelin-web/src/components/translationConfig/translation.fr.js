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

    'app': {
      'home': {
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
              'TITLE': 'Notebook',
              'IMPORT_MENU': 'Importer une note',
              'CREATE_MENU': 'Créer une note'
            }
          }
      },
      'interpreter': {
        'card': {
          'EDIT_BTN': 'modifier',
          'RESTART_BTN': 'redémarrer',
          'REMOVE_BTN': 'supprimer',
          'TABLE_TITLE': 'Propriétés',
          'TABLE_NAME_COL': 'nom',
          'TABLE_ACTION_COL': 'action',
          'TABLE_VALUE_COL': 'valeur',
          'EMPTY_PROPERTIES': 'Il n\'y a actuellement aucune configuration pour cet interpreter'
        },
        'createform': {
          'TITLE': 'Créer un nouvel Interpreter',
          'NAME_LABEL': 'Nom',
          'INTERPRETER_LABEL': 'Interpreter',
          'TABLE_DESC_COL': 'description',
          'SAVE_BTN': 'Sauvegarder',
          'CANCEL_BTN': 'Annuler'
        },
        'editForm': {
          'SAVE_BTN': 'Sauvegarder',
          'CANCEL_BTN': 'Annuler'
        },
        'header': {
          'TITLE': 'Interpreters',
          'DESC': 'Gérez les configurations de vos interpreters. Vous pouvez ajouter / retirer\
            des propriétés, les Interpreters peuvent ensuite être utilisés dans les Notes.',
          'CREATE_BTN': 'Créer'
        }
      },
      'notebook': {
        'actionBar': {
          'TOOLTIP_RUN': 'Exécuter tous les paragraphes',
          'TOOLTIP_CODE_HIDE': 'Afficher/cacher le code',
          'TOOLTIP_OUTPUT_HIDE': 'Afficher/cacher le résultat',
          'TOOLTIP_OUTPUT_CLEAR': 'Effacer le résultat',
          'TOOLTIP_DELETE': 'Supprimer le notebook',
          'TOOLTIP_CLONE': 'Cloner le notebook',
          'TOOLTIP_EXPORT': 'Exporter le notebook',
          'TOOLTIP_SCHEDULER': 'Exécution programmée',
          'TOOLTIP_SHORTCUT': 'Liste de raccourcis',
          'TOOLTIP_SETTINGS': 'Configuration du Notebook'
        }
      }
    },
    'components': {
      'navbar': {
        'MENU_NOTEBOOK': 'Notebook',
        'MENU_INTERPRETER': 'Interpreters',
        'STATUS_CONNECTED': 'Connecté',
        'STATUS_DISCONNECTED': 'Déconnecté',
        'CREATE_MENU': 'Créer une note'
      }
    }
  });
});
