<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# AGENTS.md

> Scoped guidance for work under `docs/`. This file complements the
> repository-root `AGENTS.md`.

## Scope And Ownership

- `docs/` is the source for Apache Zeppelin's versioned product documentation.
- The main `zeppelin.apache.org` website is maintained in
  `apache/zeppelin-site`; its homepage does not need to use the same generator
  as these versioned docs.
- Markdown, layouts, includes, and assets in this directory are built here.
  The generated site is written to `docs/_site/`.
- `docs/_site/` is generated and gitignored. Never edit or commit it.

## Build Model

The current build is:

```text
docs sources + docs/_config.yml
  -> Jekyll from docs/Gemfile.lock
  -> docs/_site/
  -> zeppelin-site/docs/<version>/ during a separate publication step
```

- `Gemfile` declares Jekyll and its documentation build dependencies.
- `Gemfile.lock` pins the actual Ruby dependency versions. The Docker commands
  use `bundle exec` so the pinned Jekyll version is used.
- `_config.yml` supplies `ZEPPELIN_VERSION` and `JB.BASE_PATH`.
- `_includes/JB/setup` applies `JB.BASE_PATH` only for a safe build. Therefore
  a publication build must include `--safe`.
- `Rakefile` contains legacy Jekyll-Bootstrap helpers. It is not the primary
  build entry point; use the Docker commands below.
- The Maven build does not generate this site.
- Docker is the supported build environment. Do not install or run Ruby,
  Bundler, or Jekyll directly on the host.

## Preview And Build

Preview with Docker:

```bash
cd docs
docker run --rm -it \
  -v "$PWD:/docs" \
  -w /docs \
  -p '4000:4000' \
  ruby:4.0.6 \
  bash -lc "bundle install && bundle exec jekyll serve --watch --host 0.0.0.0"
```

Open `http://localhost:4000`. The preview intentionally runs without
`--safe`, so links are rooted at `/` instead of the production version path.

Build the publication artifact with Docker:

```bash
cd docs
docker run --rm \
  -v "$PWD:/docs" \
  -w /docs \
  ruby:4.0.6 \
  bash -lc "bundle install && bundle exec jekyll build --safe"
```

The output must be under `_site/`, and generated links and assets must use the
`JB.BASE_PATH` configured in `_config.yml`.

When `Gemfile` changes, update `Gemfile.lock` inside Docker:

```bash
cd docs
docker run --rm \
  -v "$PWD:/docs" \
  -w /docs \
  ruby:4.0.6 \
  bundle lock --update
```

Run the publication build after updating the lockfile.

## Authoring Conventions

- Preserve the ASF license header in every new source file.
- Follow the front matter used by nearby pages:

  ```yaml
  ---
  layout: page
  title: "Page title"
  description: "Short description"
  group: section/subsection
  ---
  ```

- Include `{% include JB/setup %}` before page content when following the
  existing page layout.
- Prefix internal site links and assets with `{{BASE_PATH}}` when an absolute
  site path is needed. Production docs are hosted below `/docs/<version>/`,
  not at the domain root.
- Update `_includes/themes/zeppelin/_navigation.html` when a page must appear
  in the global documentation navigation.
- Keep filenames, headings, and link targets stable unless the task explicitly
  includes redirects or link migration.
- Check the corresponding source code or configuration template when
  documenting runtime behavior. Do not infer current behavior from an older
  documentation page.

## Version Handling

- `ZEPPELIN_VERSION` and `JB.BASE_PATH` in `_config.yml` must identify the same
  version.
- `dev/change_zeppelin_version.sh` updates both values as part of a repository
  version change. Do not change them for an ordinary documentation edit.
- Before producing release docs, verify that `JB.BASE_PATH` is exactly
  `/docs/<release-version>`.

## Publication Boundary

- Building this directory does not publish the website.
- The generated `_site/` tree is copied into
  `apache/zeppelin-site/docs/<version>/` by separate release/site work.
- The `zeppelin-site` repository owns the homepage, ASF staging/publishing,
  and the mapping or redirect for `/docs/latest/`.
- Do not modify `zeppelin-site`, historical documentation snapshots, or
  publication branches unless the user explicitly includes that work.

## ASF Website Policy

- Follow the ASF project website policy at
  `https://privacy.apache.org/policies/website-policy.html` and the Infra CSP
  guidance at `https://infra.apache.org/csp.html`.
- Do not add Google Analytics or any other third-party analytics, tracker,
  tracking pixel, advertising tag, or external monitoring script.
- Do not load JavaScript, CSS, fonts, images, or other assets from non-ASF
  domains. Host an asset in this repository when its license permits, or use a
  normal external link instead of embedding it.
- Third-party embeds require the consent and DPA handling described by the ASF
  policy. Prefer a direct link unless the task explicitly includes an approved
  consent flow.
- The production layout uses the ASF-hosted Matomo instance provisioned for
  Apache Zeppelin as site ID `69`. Do not replace it with another analytics
  service or change its endpoint without Privacy team approval.

## Verification

For every documentation change:

1. Run the Docker publication build above from `docs/`.
2. Confirm `_site/index.html` and the generated file for each changed page
   exist.
3. Check generated navigation, links, images, and code blocks for the affected
   pages.
4. Confirm generated URLs use the configured `/docs/<version>/` prefix.
5. Check the generated site for external trackers and embedded resources:

   ```bash
   docker run --rm \
     -v "$PWD:/docs:ro" \
     -w /docs \
     ruby:4.0.6 \
     ruby check_external_resources.rb _site
   ```

6. Run `git status --short` and keep `_site/` and incidental dependency changes
   out of the commit.

For navigation, layout, CSS, or JavaScript changes, also run the preview server
and inspect the affected pages at desktop and narrow viewport widths.
