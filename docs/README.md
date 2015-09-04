## Zeppelin project website

## Build
See https://help.github.com/articles/using-jekyll-with-pages#installing-jekyll

**tl;dr version:**

    ruby --version >= 1.9.3
    gem install bundler
    bundle install
    
*On OS X 10.9 you may need to do "xcode-select --install"*

## On local machine

### Run
    bundle exec jekyll serve --watch

### Deploy
 1. generate static website in ```_site```

    bundle exec jekyll build
 2. checkout asf repo

    svn co https://svn.apache.org/repos/asf/incubator/zeppelin asf-zepplelin

 3. copy zeppelin/_site to asf-zepplelin/site
 4.  svn commit

### Add a new page
    rake page name="new-page.md"
    
