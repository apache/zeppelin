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

## React mount infrastructure (Angular side)

The Angular host's `src/app/share/react-mount/` exports two pieces:

- `ReactRemoteLoaderService` — loads `remoteEntry.js` once per page,
  caches per-module promises, evicts on error. The load is bounded by
  `environment.reactRemoteLoadTimeoutMs`, so a remote that stalls instead
  of failing still reaches the host's `onError` and its fallback.
- `ReactMountDirective` — owns the host element, mounts outside the
  Angular zone, forwards `[reactProps]` changes through
  `handle.update(...)`, and unmounts on destroy. Re-checks `destroyed`
  after the async load so a navigation during load does not leak a mount.

Both are exported from `ShareModule`. Any notebook or interpreter
template can use the directive without additional wiring. The selector
is kebab-case (`zeppelin-react-mount`) per project ESLint convention.

## Migration roadmap

| Phase | Scope | Status |
|-------|-------|--------|
| 1 | Webpack 5 + Module Federation setup | Done |
| 1.5 | Published paragraph (pilot) | Done |
| 2 | Notebook and interpreter modules | Planned |

The published paragraph was picked as pilot because it's read-only and has almost no coupling to other modules.

## Architecture

```
Angular host (port 4200)              React remote (port 3001)
┌───────────────────────────────┐     ┌─────────────────────────┐
│  [zeppelin-react-mount]       │     │  webpack.config.js      │
│  ReactRemoteLoaderService ────┼────>│  ModuleFederationPlugin │
│  loads remoteEntry.js         │     │  name: 'reactApp'       │
│  calls mount(el, props)       │     │  exposes:               │
└───────────────────────────────┘     │    ./PublishedParagraph │
                                      │    ./ParagraphFooter    │
                                      │    ./ConfigurationTable │
                                      └─────────────────────────┘
```

1. `ReactRemoteLoaderService` loads `remoteEntry.js` from the React dev server or production assets, once per page.
2. The script registers `window.reactApp` as a Module Federation container.
3. The service calls `container.get('<exposed key>')` and caches the module promise.
4. The `[zeppelin-react-mount]` directive calls `mount(element, props)` outside the Angular zone and keeps the returned handle for later `update()` and `unmount()` calls.
5. If the remote fails to load, the directive reports the error to the host, which renders its Angular fallback instead.

Host components do not touch `window.reactApp` themselves; they bind props to the directive and supply an `onError` callback.

## Feature flags

Each React surface is behind a URL query flag, resolved by `ReactFeatureService`:

| URL | Result |
| --- | --- |
| `?react=true` | enabled |
| `?react` | enabled |
| `?react=false` | disabled |
| flag absent | disabled |

Append `?react=true` to any published paragraph URL, `?reactFooter=true` to a notebook URL, or `?reactConfiguration=true` to the configuration URL to activate React mode.

## Setup

Run `npm install` then `npm run dev` to start the dev server on `http://localhost:3001`.

The Angular host must be running on port 4200. From `zeppelin-web-angular/`, `npm start` runs both servers together.

## Build

From `projects/zeppelin-react/`, run `npm run build`. Output goes to `dist/`. In production, Angular loads `remoteEntry.js` from `/assets/react/` (see `environment.prod.ts`).

## Linting

From `projects/zeppelin-react/`, run `npm run lint` to check, `npm run lint:fix` to auto-fix. See `eslint.config.js` for rules.

## Project structure

```
src/
├── components/
│   ├── common/          # Empty, Loading
│   ├── paragraph/       # ParagraphFooter
│   ├── renderers/       # HTMLRenderer, ImageRenderer, TextRenderer
│   └── visualizations/  # TableVisualization, VisualizationControls
├── pages/
│   ├── PublishedParagraph.tsx   # entry component + mount()
│   └── ConfigurationTable.tsx   # /configuration table + mount()
├── templates/
│   └── SingleResultRenderer.tsx # routes result types to renderers
├── theme/               # host theme detection, antd + chart.js theming
├── utils/               # tableUtils, textUtils, exportFile
└── main.ts              # re-exports for Module Federation
```

## Adding a new React module

The Angular host loads each exposed module through the
`ReactMountDirective` (see `src/app/share/react-mount/`). The contract:

```ts
export interface ReactMountHandle {
  update: (props: Props & { onError?: (e: unknown) => void }) => void;
  unmount: () => void;
}

export function mount(element: HTMLElement, props: Props): ReactMountHandle;
```

1. Create a component (e.g. `src/components/<area>/ExampleFeature.tsx`).
2. Wrap its render tree in `<ReactErrorBoundary onError={props.onError}>`.
3. Wrap it in `<ZeppelinThemeProvider>` as well (see `src/theme/`), otherwise
   antd builds its styles from the default light algorithm and the module only
   looks right in dark mode while the shell's global `.ant-*` rules happen to
   cover the components in use. Pass surface specific tokens through its
   `token` prop, and read `useHostThemeMode()` when you draw outside antd, as
   a canvas chart does.
4. Export a `mount(element, props)` function that:
   - Creates a single `Root` via `createRoot(element)`.
   - Calls `root.render(<Wrapped {...props}/>)` on initial mount AND on
     every `update(newProps)` call. React's reconciler preserves state.
   - Returns `{ update, unmount }`. `unmount` calls `root.unmount()`.
5. Register in `webpack.config.js` under `exposes`:
   ```js
   exposes: {
     './PublishedParagraph': './src/pages/PublishedParagraph',
     './ParagraphFooter': './src/components/paragraph/ParagraphFooter',
     './ConfigurationTable': './src/pages/ConfigurationTable',
     './ExampleFeature': './src/components/<area>/ExampleFeature'
   }
   ```
6. Re-export from `main.ts`:
   ```ts
   export {
     ExampleFeature,
     mount as mountExampleFeature
   } from './components/<area>/ExampleFeature';
   ```
7. Use from Angular by adding the directive to your template:
   ```html
   <div
     zeppelin-react-mount="./ExampleFeature"
     [reactProps]="exampleFeatureProps"
   ></div>
   ```
   `exampleFeatureProps` should be a getter on the host component (not
   an inline object literal) so identity is stable when nothing changed.

Every exposed module must return the handle contract from `mount`. The directive assigns the return value straight to its handle, so returning a bare unmount function makes the next prop change throw.

