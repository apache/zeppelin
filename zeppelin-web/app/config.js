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

var App = require('app');

App.testMode = (location.port == '3333'); // test mode is automatically enabled if running on brunch server
App.testModeDelayForActions = 10000;
App.apiPrefix = '/api/v1';


// experimental features are automatically enabled if running on brunch server
App.enableExperimental = false;

App.supports = {
  //TODO list experimetal features here
  newServices: false,
};

if (App.enableExperimental) {
  for (var support in App.supports) {
    App.supports[support] = true;
  }
}

// this is to make sure that IE does not cache data when making AJAX calls to the server
$.ajaxSetup({
  cache: false
});

// set x-editable's default mode as inline
$.fn.editable.defaults.mode = 'inline';


Handlebars.registerHelper("ifCond",function(v1,operator,v2,options) {
    switch (operator)
    {
        case "==":
            return (v1==v2)?options.fn(this):options.inverse(this);

        case "!=":
            return (v1!=v2)?options.fn(this):options.inverse(this);

        case "===":
            return (v1===v2)?options.fn(this):options.inverse(this);

        case "!==":
            return (v1!==v2)?options.fn(this):options.inverse(this);

        case "&&":
            return (v1&&v2)?options.fn(this):options.inverse(this);

        case "||":
            return (v1||v2)?options.fn(this):options.inverse(this);

        case "<":
            return (v1<v2)?options.fn(this):options.inverse(this);

        case "<=":
            return (v1<=v2)?options.fn(this):options.inverse(this);

        case ">":
            return (v1>v2)?options.fn(this):options.inverse(this);

        case ">=":
         return (v1>=v2)?options.fn(this):options.inverse(this);

        default:
            return eval(""+v1+operator+v2)?options.fn(this):options.inverse(this);
    }
});
