# Apache Zeppelin documentation

This README will walk you through building the documentation of Apache Zeppelin. The documentation is included here with Apache Zeppelin source code. The online documentation at [https://zeppelin.apache.org/docs/<ZEPPELIN_VERSION>](https://zeppelin.apache.org/docs/latest/) is also generated from the files found in here.

## Build documentation
Zeppelin uses [Jekyll](https://jekyllrb.com/) to generate the static versioned documentation published on the Apache Zeppelin website.

**Requirements**

- [Docker](https://docs.docker.com/get-docker/)

Ruby, Bundler, and Jekyll run only inside the Docker container. No host Ruby
installation is required.

## Preview documentation

From `$ZEPPELIN_HOME/docs`, run:

```bash
docker run --rm -it \
  --user "$(id -u):$(id -g)" \
  -e HOME=/usr/local/bundle \
  -e BUNDLE_FROZEN=true \
  -v "$PWD:/docs" \
  -w /docs \
  -p '4000:4000' \
  ruby:4.0.6 \
  bash -lc "bundle install && bundle exec jekyll serve --watch --host 0.0.0.0"
```

Jekyll starts at `http://localhost:4000` and watches the `docs/` sources for
updates. The container runs with the current user's UID and GID so generated
files are not owned by `root` on the host.

## Contribute to Zeppelin documentation
If you wish to help us and contribute to Zeppelin Documentation, please look at [Zeppelin Documentation's contribution guideline](https://zeppelin.apache.org/contribution/contributions.html).

## For committers only
### Bumping up version in a new release

- Update `ZEPPELIN_VERSION` and `JB.BASE_PATH` in `_config.yml`.

### Build versioned documentation

From `$ZEPPELIN_HOME/docs`, run:

```bash
docker run --rm \
  --user "$(id -u):$(id -g)" \
  -e HOME=/usr/local/bundle \
  -e BUNDLE_FROZEN=true \
  -v "$PWD:/docs" \
  -w /docs \
  ruby:4.0.6 \
  bash -lc "bundle install && bundle exec jekyll build --safe"
```

Check the generated site for external resources and trackers:

```bash
docker run --rm \
  -v "$PWD:/docs:ro" \
  -w /docs \
  ruby:4.0.6 \
  ruby check_external_resources.rb _site
```

The generated site is written to `_site/`. Copy it to
`zeppelin-site/docs/<version>/` as part of the separate website publication
workflow.
