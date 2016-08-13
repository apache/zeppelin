# Contributing to Apache Zeppelin Documentation

## Folder Structure
`docs/` folder is organized as below:

```
docs/
 ├── _includes/themes/zeppelin 
 │   ├── _navigation.html
 │   └── default.html
 ├── _layouts
 ├── _plugins
 ├── assets/themes/zeppelin -> {ASSET_PATH}
 │    ├── bootstrap
 │    ├── css
 │    ├── img
 │    └── js
 ├── development/ *.md
 ├── displaysystem/ *.md
 ├── install/ *.md
 ├── interpreter/ *.md
 ├── manual/ *.md
 ├── quickstart/ *.md
 ├── rest-api/ *.md
 ├── security/ *.md
 ├── storage/ *.md
 ├── Gemfile
 ├── Gemfile.lock
 ├── _config.yml
 ├── index.md
 └── ...
```

 - `_navigation.html`: the dropdown menu in navbar
 - `default.html` & `_layouts/`: define default HTML layout
 - `_plugins/`: custom plugin `*.rb` files can be placed in this folder. See [jekyll/plugins](https://jekyllrb.com/docs/plugins/) for the further information.
 - `{ASSET_PATH}/css/style.css`: extra css components can be defined
 - `{ASSET_PATH}/img/docs-img/`: image files used for document pages can be placed in this folder
 - `{ASSET_PATH}/js/`: extra `.js` files can be placed
 - `Gemfile`: defines bundle dependencies. They will be installed by `bundle install`.
 - `Gemfile.lock`: when you run `bundle install`, bundler will persist all gems name and their version to this file. For the more details, see [Bundle "The Gemfile Lock"](http://bundler.io/v1.10/man/bundle-install.1.html#THE-GEMFILE-LOCK)
 - `documentation_group`: `development/`, `displaysystem/`, `install/`, `interpreter/`...
 - `_config.yml`: defines configuration options for docs website. See [jekyll/configuration](https://jekyllrb.com/docs/configuration/) for the other available config variables.
 - `index.md`: the main page of `http://zeppelin.apache.org/docs/<ZEPPELIN_VERSION>/`

 
## Markdown
Zeppelin documentation pages are written with [Markdown](http://daringfireball.net/projects/markdown/). It is possible to use [GitHub flavored syntax](https://help.github.com/categories/writing-on-github/) and intermix plain HTML.

## Front matter
Every page contains [YAML front matter](https://jekyllrb.com/docs/frontmatter/) block in their header. Don't forget to wrap the front matter list with triple-dashed lines(`---`) like below.
The document page should start this triple-dashed lines. Or you will face 404 error, since Jekyll can't find the page.

```
---
layout: page
title: "Apache Zeppelin Tutorial"
description: "This tutorial page contains a short walk-through tutorial that uses Apache Spark backend. Please note that this tutorial is valid for Spark 1.3 and higher."
group: quickstart
---
```

 - `layout`: the default layout is `page` which is defined in `_layout/page.html`.
 - `title`: the title for the document. Please note that if it needs to include `Zeppelin`, it should be `Apache Zeppelin`, not `Zeppelin`.
 - `description`: a short description for the document. One or two sentences would be enough. This description also will be shown as an extract sentence when people search pages.
 - `group`: a category of the document page

## Headings
All documents are structured with headings. From these headings, you can automatically generate a **Table of Contents**. There is a simple rule for Zeppelin docs headings.

```
#    Level-1 heading <- used only for the main title
##   Level-2 heading <- start with this
###  Level-3 heading 
#### Level-4 heading <- won't be converted in TOC from this level
```

## Table of contents(TOC)

```
<div id="toc"></div>
```

Add this line below  `# main title` in order to generate a **Table of Contents**. Headings until `### (Level-3 heading)` are included to TOC.


Default setting options for TOC are definded in [here](https://github.com/apache/zeppelin/blob/master/docs/assets/themes/zeppelin/js/toc.js#L4).


## Adding new pages
If you're going to create new pages, there are some spots you need to add the location of the page.

 - **Dropdown menu in navbar**: add your docs location to [_navigation.html](https://github.com/apache/zeppelin/blob/master/docs/_includes/themes/zeppelin/_navigation.html)
 - **Main index**: add your docs below [What is the next?](http://zeppelin.apache.org/docs/latest/#what-is-the-next) section in [index.md](https://github.com/apache/zeppelin/blob/master/docs/index.md) with a short description. No need to do this if the page is for **Interpreters**.
