/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


window.App = require('app');

require('config');

require('models');
require('controllers');
require('views');
require('router');
require('templates');

// to support HtmlUnit
if (typeof console != 'object') {
    console = {
        log: function() {},
        debug: function() {},
        warn: function() {}
    }
}

console.log('TRACE: app.js-> localStorage:Zeppelin.authenticated=' + localStorage.getItem('Zeppelin' + 'authenticated'));
//console.log('TRACE: app.js-> router.authenticated=' + App.get('router.loggedIn'));
console.log("TRACE: app.js-> Ember.TEMPLATES: ", Ember.TEMPLATES);

