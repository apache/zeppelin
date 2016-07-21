## Apache Zeppelin documentation

This readme will walk you through building the Zeppelin documentation, which is included here with the Zeppelin source code.


## Build documentation
See https://help.github.com/articles/using-jekyll-with-pages#installing-jekyll

**Requirements**

```
    ruby --version >= 2.0.0
    gem install bundler
    # go to /docs under your Zeppelin source
    bundle install
```

For the further information about requirements, please see [here](https://help.github.com/articles/setting-up-your-github-pages-site-locally-with-jekyll/#requirements). 

*On OS X 10.9 you may need to do "xcode-select --install"*


## Run website

    bundle exec jekyll serve --watch


## Adding a new page

    rake page name="new-page.md"


## Bumping up version in a new release

   * `ZEPPELIN_VERSION` and `BASE_PATH` property in _config.yml

## Deploy to ASF svnpubsub infra (for committers only)
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
