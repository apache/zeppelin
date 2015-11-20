## Zeppelin documentation

This readme will walk you through building the Zeppelin documentation, which is included here with the Zeppelin source code.


## Build documentation
See https://help.github.com/articles/using-jekyll-with-pages#installing-jekyll

**tl;dr version:**

    ruby --version >= 1.9.3
    gem install bundler
    bundle install
    
*On OS X 10.9 you may need to do "xcode-select --install"*


## Run website

    bundle exec jekyll serve --watch


## Deploy to ASF svnpubsub infra (commiters only)
 1. generate static website in `./_site`
    ```
    bundle exec jekyll build --safe
    ```

 2. checkout ASF repo
    ```
    svn co https://svn.apache.org/repos/asf/incubator/zeppelin asf-zepplelin
    ```
 3. copy zeppelin/_site to asf-zepplelin/site/docs/[VERSION]
 4. ```svn commit```

## Adding a new page

    rake page name="new-page.md"



## Bumping up version

   * `BASE_PATH` property in _config.yml
   * `ZEPPELIN <small>([VERSION])</small>` in _includes/themes/zeppelin/_navigation.html 

need to be updated
