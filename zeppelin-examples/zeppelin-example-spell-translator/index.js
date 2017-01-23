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

import {
    SpellBase,
    SpellResult,
    DefaultDisplayType,
} from 'zeppelin-spell';

import 'whatwg-fetch';

export default class TranslatorSpell extends SpellBase {
    constructor() {
        super("%translator");
    }

    interpret(paragraphText) {
        /**
         * SpellResult
         * - accepts not only `string` but also `promise`
         * - allows to add multiple output using the `add()` function
         */
        const result = new SpellResult()
            .add('<h4>Translation From English To Korean</h4>', DefaultDisplayType.HTML)
            // or use display system implicitly like
            // .add('%html <h4>Translation From English To Korean</h4>')
            .add(this.translate(paragraphText));
        return result;
    }

    translate(text) {
        return fetch('https://translation.googleapis.com/language/translate/v2', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': 'Bearer YOUR_ACCESS_KEY',
            },
            body: JSON.stringify({
                'q': text,
                'source': 'en',
                'target': 'ko',
                'format': 'text'
            })
        }).then(response => {
            if (response.status === 200) {
                return response.json()
            }
            throw new Error(`https://translation.googleapis.com/language/translate/v2 ${response.status} (${response.statusText})`);
        }).then((json) => {
            const extracted = json.data.translations.map(t => {
                return t.translatedText;
            });
            return extracted.join('\n');
        });
    }
}

