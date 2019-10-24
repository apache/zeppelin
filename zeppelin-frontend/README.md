<!--
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->
  
# Zeppelin Front-end

Zeppelin notebooks front-end built with Angular.

- Jira issue [ZEPPELIN-4321](https://issues.apache.org/jira/browse/ZEPPELIN-4321)
- Design Document: [Zeppelin Notebook Rework Proposal](https://docs.google.com/document/d/1z_VscS81Xwx_3QaexKB2s0uEMEuWKsPXh9mWFRq0-hY)

![screenshot](/screenshot.png?raw=true "Screenshot")

## Setup

### Prerequisites

- [Node.js](https://nodejs.org) version 10.9.0 or later or use [creationix/nvm](https://github.com/creationix/nvm).
- NPM package manager (which is installed with Node.js by default).
- [Angular CLI](https://angular.io/cli) version 8.3.0 or later.

### Install

Run the `npm install` command to install dependencies in the project directory.

### Start Zeppelin server

[Run Zeppelin server](https://zeppelin.apache.org/contribution/contributions.html#run-zeppelin-server-in-development-mode) on `http://localhost:8080`.

If you are using a custom port instead of the default(http://localhost:8080) or other network address, you can create `.env` file in the project directory and set `SERVER_PROXY`.

*.env*

```
SERVER_PROXY=http://localhost:8080
```

### Development server

Run `npm start` for a dev server. Navigate to `http://localhost:4200/`. The app will automatically reload if you change any of the source files.

### Build

Run `npm build` to build the project. The build artifacts will be stored in the `dist/` directory.

### Running unit tests

Run `ng test` to execute the unit tests via [Karma](https://karma-runner.github.io).

## Implementation Progress

### Pages

| Name | Route | Module     | UI |
| ---  | ----- | ---------- | -- |
| Home | `/`   | HomeModule |  Y |
| Login | `/login`       | LoginModule |  Y |
| Job Manager  | `/jobmanager`  | JobManagerModule |  Y |
| Interpreter Setting   | `/interpreter` | InterpreterModule |  Y |
| Notebook | `/notebook/{id}` | NotebookModule |  Y |
| Notebook Repos | `/notebookRepos` |  |   |
| Credential     | `/credential` |  |   |
| Helium | `/helium` |  |  WIP  |
| Configuration | `/configuration` |  |   |

### Notebook Features

| Feature | Description | Status |
| ------  | ---- | ---- |
| Files System  | Create/ Rename/ Import etc.  | Y |
| Toolbar Actions  | The top toolbar actions   | Y |

### Paragraph Features

| Feature | Description | Status |
| ------  | ---- | ---- |
| Grid layout and resizable | | Y |
| Code Editor | | Y |
| Actions  | The Corresponding actions of the drop-down menu in the setting button | Y |
| Actions(hot-keys)  | Support hot-keys for the actions  | WIP |
| Publishable  | [publish paragraphs](http://zeppelin.apache.org/docs/0.8.0/usage/other_features/publishing_paragraphs.html)  |  |
| Stream  |  |  |

### Result Display

| Type | Status |
| ------  | ---- |
| Dynamic Form  | Y |
| Text  | Y |
| Html  |  Y |
| Table  |  Y |
| Network  |  |

### Table Visualization

| Type | State |
| ------ | ---- |
| Line Chart  | Y |
| Bard Chart  |  Y |
| Pie Chart  |  Y |
| Area Chart  |  Y |
| Scatter Chart  | Y |

### Helium Visualization

| Type | Description | Status |
| ------  | ---- | ---- |
| Prototype | To verify the implementable prototype | Y |
| Publish Dependencies | Just like [zeppelin-vis](https://github.com/apache/zeppelin/tree/master/zeppelin-web/src/app/visualization)  | WIP |
| Example Projects |   | Y |
| Development Documents |   | WIP |

## Contributing

### Dev Mode

Follow the [Setup](#Setup) steps to starting the frontend service. The app will automatically reload if you change any of the source files.

### Technologies

Zeppelin-Frontend-Next is using Angular as the main Framework, before developing we hope highly recommended to have a good knowledge of [Angular](https://angular.io/) and [RxJs](https://github.com/ReactiveX/rxjs).

In addition:

- We use [G2](https://github.com/antvis/g2) [(MIT)](https://github.com/antvis/g2/blob/master/LICENSE) visualization
- We use [Lodash](https://lodash.com/) [(MIT)](https://github.com/lodash/lodash/blob/master/LICENSE) to process complex data
- We use [Monaco Editor](https://github.com/microsoft/monaco-editor) [(MIT)](https://github.com/microsoft/monaco-editor/blob/master/LICENSE.md) to make code editor

### Coding style

- We follow mainly the [Angular Style Guide](https://angular.io/guide/styleguide)
- We use a 2 spaces indentation
- We use single quotes

But don't worry, TSLint and prettier will make you remember it for the most part.
Git hooks will automatically check and fix it when commit.

### Folder Structure

We follow mainly the [Workspace and project file structure](https://angular.io/guide/styleguide) to organize the folder structure and files.

#### Src Folder Structure

`src` folder contains the source code for Zeppelin-Frontend-Next.

```
├── app
│   ├── core
│   │   └── message-listener             # handle WebSocket message
│   ├── interfaces                       # interfaces
│   ├── pages
│   │   ├── login                        # login module
│   │   └── workspace
│   │       ├── home                     # welcome module
│   │       ├── interpreter              # interpreter settings
│   │       ├── job-manager              # job manager module
│   │       └── notebook                 # notebook module
│   │           ├── action-bar           # notebook settings
│   │           ├── interpreter-binding  # interpreter binding
│   │           ├── permissions          # permissions
│   │           └── paragraph            # paragraph module
│   │               ├── code-editor      # code editor module
│   │               ├── control          # paragraph controls
│   │               ├── dynamic-forms    # dynamic forms
│   │               └── result           # display result
│   ├── sdk                              # Zeppelin API Frontend SDK
│   ├── share                            # Share Components
│   ├── services                         # API Service
│   └── visualization
│       ├── area-chart                   # Area Chart Component
│       ├── bar-chart                    # Bar Chart Component
│       ├── line-chart                   # Line Chart Component
│       ├── pie-chart                    # Pie Chart Component
│       ├── scatter-chart                # Scatter Chart Component
│       └── table                        # Data Table Component
├── assets                               # Assets
└── styles
    └── theme                            # Theme Files
        ├── dark
        └── light
```

#### Import Path Rules

We specify path mapping in the `tsconfig.json` file to get a clear import path.

So please follow the rules following:

- Add `public-api.ts` and `index.ts` to the folder where want to export the modules
- `public-api.ts` File only included you wish to export modules
- `index.ts` File only export `./public-api.ts`
- Use relative paths instead of mapped paths when the same level to prevent circular references

### Good Practices

The following guide for this project only. Most of the time you only need to follow Angular's guide.

#### Change Detection Strategy

Use [OnPush](https://angular.io/api/core/ChangeDetectionStrategy#OnPush) as the change detection strategy for components.

#### WebSocket Listen and Send

*Send Message*: Inject the `MessageService` and then use its instance methods.

```ts

import { MessageService } from '@zeppelin/services';

export class SomeComponent {
  
  constructor(public messageService: MessageService) { }
  
  fun() {
    // Do something
    this.messageService.listNoteJobs();
  }
}
```

*Listen to Message* 

Make sure the class extends from `MessageListenersManager` and inject the `MessageService` and ensures that it is public.

After that, you can use the `@MessageListener` decorator to decorate the corresponding message method.

```ts
import { MessageListener, MessageListenersManager } from '@zeppelin/core';
import { MessageService } from '@zeppelin/services';
import { OP, ListNoteJobs } from '@zeppelin/sdk';

export class SomeComponent extends MessageListenersManager {
  
  constructor(public messageService: MessageService) { }
  
  @MessageListener(OP.LIST_NOTE_JOBS)
  fun(data: ListNoteJobs) {
    // Do something
  }
}
```

#### Theming

Use we provide the function to wrap component styles to implement theming. You can find the theme variables in the `src/styles/theme/` folder.

```less
@import "theme-mixin";

.themeMixin({
  // component styles
});
```

#### Imports order

Follow of the following imports order:

```ts
import * from '@angular/*'  // Angular modules
import * from 'rxjs/*'      // Rxjs modules
// BLANK LINE
import * from '*'           // Other third party modules
// BLANK LINE
import * from '@zeppelin/*' // This project modules
// BLANK LINE
import * from './*'         // Same level modules
```