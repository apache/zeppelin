# Apache Zeppelin documentation

This README will walk you through building the documentation of Apache Zeppelin. The documentation is included here with Apache Zeppelin source code. The online documentation at [https://zeppelin.apache.org/docs/<ZEPPELIN_VERSION>](https://zeppelin.apache.org/docs/latest/) is also generated from the files found in here.

## Build documentation
Zeppelin is using [Jekyll](https://jekyllrb.com/) which is a static site generator and [Github Pages](https://pages.github.com/) as a site publisher. For the more details, see [help.github.com/articles/about-github-pages-and-jekyll/](https://help.github.com/articles/about-github-pages-and-jekyll/).

**Requirements**

```
# ruby --version >= 2.0.0
# Install Bundler using gem
gem install bundler

cd $ZEPPELIN_HOME/docs
# Install all dependencies declared in the Gemfile
bundle install
```

For the further information about requirements, please see [here](https://help.github.com/articles/setting-up-your-github-pages-site-locally-with-jekyll/#requirements).

On OS X 10.9, you may need to do

```
xcode-select --install
```

## Run website locally
If you don't want to encounter uglily rendered pages, run the documentation site in your local first.

In `$ZEPPELIN_HOME/docs`,

```
bundle exec jekyll serve --watch
```

Using the above command, Jekyll will start a web server at `http://localhost:4000` and watch the `/docs` directory to update.



## Contribute to Zeppelin documentation
If you wish to help us and contribute to Zeppelin Documentation, please look at [Zeppelin Documentation's contribution guideline](https://zeppelin.apache.org/contribution/contributions.html).


## For committers only
### Bumping up version in a new release

   * `ZEPPELIN_VERSION` and `BASE_PATH` property in _config.yml

### Deploy to ASF svnpubsub infra
 1. generate static website in `./_site`

    ```
    # go to /docs under Zeppelin source
    bundle exec jekyll build --safe
    ```

 2. checkout ASF repo
    ```
    svn co https://svn.apache.org/repos/asf/zeppelin asf-zeppelin
    ```
 3. copy `zeppelin/docs/_site` to `asf-zeppelin/site/docs/[VERSION]`
 4. ```svn commit```
