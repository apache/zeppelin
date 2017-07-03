---
layout: page
title: "Writing a new Helium Spell"
description: "Spell is a kind of interpreter that runs on browser not on backend. So, technically it's the frontend interpreter. "
group: development/helium
---
<!--
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
{% include JB/setup %}

# Writing a new Spell

<div id="toc"></div>

## What is Apache Zeppelin Spell

Spell is a kind of interpreter that runs on browser not on backend. So, technically it's the frontend interpreter.
It can provide many benefits.

- Spell is pluggable frontend interpreter. So it can be installed and removed easily using helium registry.
- Every spell is written in javascript. It means you can use existing javascript libraries whatever you want.
- Spell runs on browser like display system (`%html`, `%table`). In other words, every spell can be used as display system as well.

## How it works

Helium Spell works like [Helium Visualization](./writing_visualization_basic.html).

- Every helium packages are loaded from central (online) registry or local registry
- You can see loaded packages in `/helium` page.
- When you enable a spell, it's built from server and sent to client
- Finally it will be loaded into browser.

## How to use spell

### 1. Enabling

Find a spell what you want to use in `/helium` package and click `Enable` button.

<img class="img-responsive" style="width:70%" src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/writing_spell_registered.png" />

### 2. Using

Spell works like an interpreter. Use the `MAGIC` value to execute spell in a note. (you might need to refresh after enabling)
For example, Use `%echo` for the Echo Spell.

<img class="img-responsive" style="width:70%" src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/writing_spell_using.gif" />


## Write a new Spell

Making a new spell is similar to [Helium Visualization#write-new-visualization](./writing_visualization_basic.html#write-new-visualization).

- Add framework dependency called zeppelin-spell into `package.json`
- Write code using framework
- Publish your spell to [npm]("https://www.npmjs.com/")

### 1. Create a npm package

Create a [package.json](https://docs.npmjs.com/files/package.json) in new directory for spell.

- You have to add a framework called `zeppelin-spell` as a dependency to create spell ([zeppelin-spell](https://github.com/apache/zeppelin/tree/master/zeppelin-web/src/app/spell))
- Also, you can add any dependencies you want to utilise.

Here's an example

```json
{
  "name": "zeppelin-echo-spell",
  "description": "Zeppelin Echo Spell (example)",
  "version": "1.0.0",
  "main": "index",
  "author": "",
  "license": "Apache-2.0",
  "dependencies": {
    "zeppelin-spell": "*"
  },
  "helium": {
    "icon" : "<i class='fa fa-repeat'></i>",
    "spell": {
      "magic": "%echo",
      "usage": "%echo <TEXT>"
    }
  }
}
```

### 2. Write spell using framework

Here are some examples you can refer

- [Echo Spell](https://github.com/apache/zeppelin/blob/master/zeppelin-examples/zeppelin-example-spell-echo/index.js)
- [Markdown Spell: Using library](https://github.com/apache/zeppelin/blob/master/zeppelin-examples/zeppelin-example-spell-markdown/index.js)
- [Flowchart Spell: Using DOM](https://github.com/apache/zeppelin/blob/master/zeppelin-examples/zeppelin-example-spell-flowchart/index.js)
- [Google Translation API Spell: Using API (returning promise)](https://github.com/apache/zeppelin/blob/master/zeppelin-examples/zeppelin-example-spell-translator/index.js)

Now, you need to write code to create spell which processing text.

```js
import {
    SpellBase,
    SpellResult,
    DefaultDisplayType,
} from 'zeppelin-spell';

export default class EchoSpell extends SpellBase {
    constructor() {
        /** pass magic to super class's constructor parameter */
        super("%echo");
    }

    interpret(paragraphText) {
        const processed = paragraphText + '!';

        /**
         * should return `SpellResult` which including `data` and `type`
         * default type is `TEXT` if you don't specify.  
         */
        return new SpellResult(processed);
    }
}
```

Here is another example. Let's say we want to create markdown spell. First of all, we should add a dependency for markdown in package.json

```json
// package.json
 "dependencies": {
    "markdown": "0.5.0",
    "zeppelin-spell": "*"
  },
```

And here is spell code.

```js
import {
    SpellBase,
    SpellResult,
    DefaultDisplayType,
} from 'zeppelin-spell';

import md from 'markdown';

const markdown = md.markdown;

export default class MarkdownSpell extends SpellBase {
    constructor() {
        super("%markdown");
    }

    interpret(paragraphText) {
        const parsed = markdown.toHTML(paragraphText);

        /**
         * specify `DefaultDisplayType.HTML` since `parsed` will contain DOM
         * otherwise it will be rendered as `DefaultDisplayType.TEXT` (default)
         */
        return new SpellResult(parsed, DefaultDisplayType.HTML);
    }
}
```

- You might want to manipulate DOM directly (e.g google d3.js), then refer [Flowchart Spell](https://github.com/apache/zeppelin/blob/master/zeppelin-examples/zeppelin-example-spell-flowchart/index.js)
- You might want to return promise not string (e.g API call), then refer [Google Translation API Spell](https://github.com/apache/zeppelin/blob/master/zeppelin-examples/zeppelin-example-spell-translator/index.js)

### 3. Create __Helium package file__ for local deployment

You don't want to publish your package every time you make a change in your spell. Zeppelin provides local deploy.
The only thing you need to do is creating a __Helium Package file__ (JSON) for local deploy.
It's automatically created when you publish to npm repository but in local case, you should make it by yourself.

```json
{
  "type" : "SPELL",
  "name" : "zeppelin-echo-spell",
  "version": "1.0.0",
  "description" : "Return just what receive (example)",
  "artifact" : "./zeppelin-examples/zeppelin-example-spell-echo",
  "license" : "Apache-2.0",
  "spell": {
    "magic": "%echo",
    "usage": "%echo <TEXT>"
  }
}
```

- Place this file in your local registry directory (default `$ZEPPELIN_HOME/helium`).
- `type` should be `SPELL`
- Make sure that `artifact` should be same as your spell directory.
- You can get information about other fields in [Helium Visualization#3-create-helium-package-file-and-locally-deploy](./writing_visualization_basic.html#3-create-helium-package-file-and-locally-deploy).

### 4. Run in dev mode

```bash
cd zeppelin-web
yarn run dev:helium
```

You can browse localhost:9000. Every time refresh your browser, Zeppelin will rebuild your spell and reload changes.

### 5. Publish your spell to the npm repository

See [Publishing npm packages](https://docs.npmjs.com/getting-started/publishing-npm-packages)
