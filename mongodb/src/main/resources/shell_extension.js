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

var tableLimit = TABLE_LIMIT_PLACEHOLDER;

function flattenObject(obj, flattenArray) {
    var toReturn = {};
    
    for (var i in obj) {
        if (!obj.hasOwnProperty(i)) continue;
        
        //if ((typeof obj[i]) == 'object') {
        if (toString.call( obj[i] ) === '[object Object]' ||
            toString.call( obj[i] ) === '[object BSON]' ||
          (flattenArray && toString.call( obj[i] ) === '[object Array]')) {
            var flatObject = flattenObject(obj[i]);
            for (var x in flatObject) {
                if (!flatObject.hasOwnProperty(x)) continue;
                
                toReturn[i + '.' + x] = flatObject[x];
            }
        } else if (toString.call( obj[i] ) === '[object Array]') {
            toReturn[i] = tojson(obj[i], null, true);
        } else {
            toReturn[i] = obj[i];
        }
    }
    return toReturn;
}

function printTable(dbquery, fields, flattenArray) {
    
    var iterator = dbquery;
    
    if (toString.call( dbquery ) === '[object Array]') {
        iterator = (function() {
            var index = 0,
                data = dbquery,
                length = data.length;

            return {
                next: function() {
                    if (!this.hasNext()) {
                        return null;
                    }
                    return data[index++];
                },
                hasNext: function() {
                    return index < length;
                }
            }
        }());
    }

    // Flatten all the documents and get all the fields to build a table with all fields
    var docs = [];
    var createFieldSet = fields == null || fields.length == 0;
    var fieldSet = fields ? [].concat(fields) : []; //new Set(fields);
    
    while (iterator.hasNext()) {
        var doc = iterator.next();
        doc = flattenObject(doc, flattenArray);
        docs.push(doc);
        if (createFieldSet) {
            for (var i in doc) {
                if (doc.hasOwnProperty(i) && fieldSet.indexOf(i) === -1) {
                    fieldSet.push(i);
                }
            }
        }
    }
    
    fields = fieldSet;

    var header = "%table ";
    fields.forEach(function (field) { header += field + "\t" })
    print(header.substring(0, header.length - 1));
    
    docs.forEach(function (doc) {
        var row = "";
        fields.forEach(function (field) { row += doc[field] + "\t" })
        print(row.substring(0, row.length - 1));
    });
}

DBQuery.prototype.table = function (fields, flattenArray) {
    if (this._limit > tableLimit) {
        this.limit(tableLimit);
    }
    printTable(this, fields, flattenArray);
};

DBCommandCursor.prototype.table = DBQuery.prototype.table;

var userName = "USER_NAME_PLACEHOLDER";
var password = "PASSWORD_PLACEHOLDER";
var authDB = "AUTH_DB_PLACEHOLDER";
var targetDB = "TARGET_DB_PLACEHOLDER";

if (userName){
    authDB = authDB || "admin";
    db = db.getSiblingDB(authDB);
    db.auth(userName,password);
}

db = db.getSiblingDB(targetDB);

