## Zeppelin project website

This readme will walk you through building the Zeppelin website


## Build website
See https://help.github.com/articles/using-jekyll-with-pages#installing-jekyll

**tl;dr version:**

    ruby --version >= 1.9.3
    gem install bundler
    bundle install

### Build FAQ

*On OS X 10.9 you may need to do "xcode-select --install"*

Gem *nokogiri* may confilict with `xz` if you have it installed. See https://github.com/sparklemotion/nokogiri/issues/1483
The workaround is to uninstall `zx` before doing `bundle insall`.



## Run website

    bundle exec jekyll serve --watch


## Deploy to ASF svnpubsub infra (committers only)
 1. generate static website in `./_site`
    ```
    JEKYLL_ENV=production bundle exec jekyll build
    ```

 2. checkout ASF repo
    ```
    svn co https://svn.apache.org/repos/asf/zeppelin asf-zeppelin
    ```
 3. copy `zeppelin/_site/*` to `asf-zeppelin/site`
 4. ```svn commit -m```

## Adding a new page

    rake page name="new-page.md"
