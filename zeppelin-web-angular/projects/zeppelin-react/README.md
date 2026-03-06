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

# Zeppelin React

React micro-frontend that runs alongside the Angular host via [Webpack Module Federation](https://webpack.js.org/concepts/module-federation/).

- Design Document: [Micro Frontend Migration (Angular to React) Proposal](https://cwiki.apache.org/confluence/display/ZEPPELIN/Micro+Frontend+Migration%28Angular+to+React%29+Proposal)

## Migration roadmap

| Phase | Scope | Status |
|-------|-------|--------|
| 1 | Webpack 5 + Module Federation setup | Done |
| 1.5 | Published paragraph (pilot) | Done |
| 2 | Notebook and interpreter modules | Planned |

The published paragraph was picked as pilot because it's read-only and has almost no coupling to other modules.

## Architecture

```
Angular host (port 4200)          React remote (port 3001)
┌─────────────────────────┐       ┌─────────────────────────┐
│  paragraph.component.ts │       │  webpack.config.js      │
│  loads remoteEntry.js ──┼──────>│  ModuleFederationPlugin  │
│  calls mount(el, props) │       │  name: 'reactApp'       │
└─────────────────────────┘       │  exposes:               │
                                  │    ./PublishedParagraph  │
                                  └─────────────────────────┘
```

1. Angular loads `remoteEntry.js` from the React dev server or production assets.
2. The script registers `window.reactApp` as a Module Federation container.
3. Angular calls `container.get('./PublishedParagraph')` to get the module.
4. The module exports `mount(element, props)`, which calls `createRoot()` and renders into the DOM element.

Append `?react=true` to any published paragraph URL to activate React mode.

## Setup

Run `npm install` then `npm run dev` to start the dev server on `http://localhost:3001`.

The Angular host must be running on port 4200. From `zeppelin-web-angular/`, `npm start` runs both servers together.

## Build

From `projects/zeppelin-react/`, run `npm run build`. Output goes to `dist/`. In production, Angular loads `remoteEntry.js` from `/assets/react/` (see `environment.prod.ts`).

## Linting

From `projects/zeppelin-react/`, run `npm run lint` to check, `npm run lint:fix` to auto-fix. See `.eslintrc.json` for rules.

## Project structure

```
src/
├── components/
│   ├── common/          # Empty, Loading
│   ├── renderers/       # HTMLRenderer, ImageRenderer, TextRenderer
│   └── visualizations/  # TableVisualization, VisualizationControls
├── pages/
│   └── PublishedParagraph.tsx   # entry component + mount()
├── templates/
│   └── SingleResultRenderer.tsx # routes result types to renderers
├── utils/               # tableUtils, textUtils, exportFile
└── main.ts              # re-exports for Module Federation
```

## Adding a new React module

1. Create a component (e.g. `src/pages/ExampleFeature.tsx`).
2. Export a `mount(element, props)` function that creates a React root and renders the component.
3. Register in `webpack.config.js` under `exposes`:
   ```js
   exposes: {
     './PublishedParagraph': './src/pages/PublishedParagraph',
     './ExampleFeature': './src/pages/ExampleFeature'
   }
   ```
4. Re-export from `main.ts`:
   ```ts
   export { ExampleFeature, mount as mountExampleFeature } from './pages/ExampleFeature';
   ```
5. Load from Angular (same pattern as `paragraph.component.ts`):
   ```ts
   const factory = await container.get('./ExampleFeature');
   const { mount } = factory();
   mount(hostElement, props);
   ```

