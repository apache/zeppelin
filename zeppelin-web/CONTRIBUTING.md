# Contributing to Zeppelin-Web

## Dev Mode
When working on Zeppelin's WebApplication, it is recommended to run in dev mode.

For that, start Zeppelin server normally, then use ``./grunt serve`` in _zeppelin-web_ directory.

This will launch a Zeppelin WebApplication on port **9000** that will update on code changes.

## Technologies

Zeppelin WebApplication is using **AngularJS** as main Framework, and **Grunt** and **Bower** as helpers.

So you might want to get familiar with it. 
[Here is a good start](http://www.sitepoint.com/kickstart-your-angularjs-development-with-yeoman-grunt-and-bower/)
(There is obviously plenty more ressources to learn)

## Coding style

* We follow mainly the [Google Javascript Guide](https://google-styleguide.googlecode.com/svn/trunk/javascriptguide.xml)
* We use a 2 spaces indentation
* We use single quotes

But don't worry, JSHint will make you remember it for the most part.

We try not to have **JQuery except in directives**, If you want to include a library,
please search for its **angularJS** directive first.

If you still need to use it, then please use ``angular.element()`` instead of ``$()``

## Folder Structure & Code Organization

* `src` folder: Contains the Source code for Zeppelin WebApplication
* `dist` folder: Contains the compiled code after using **grunt build**

### Src and Code Organization

The `src` folder is organized as such:

<pre>
 src/
 ├──  app/
 │   ├──  name/
 │   │    ├──  name.controller.js
 |   |    ├──  name.html
 |   |    ├──  subComponent1/
 |   |    |    ├──  subComponent1.html
 |   |    |    ├──  subComponent1.css
 │   |    |    └──  subComponent1.controller.js
 │   │    └──  name.css
 │   └──  app.js
 ├──  assets/
 │   ├──  images/
 │   └──  styles/
 |        ├──  looknfeel/
 │        └──  printMode.css
 ├──  components/
 │   ├──  component1/
 |   |    ├──  component1.html
 │   |    └──  component1.controller.js
 │   └──  component2/
 ├──  fonts/
 |    ├──  *.{eot,svg,ttf,woff,otf}
 │    └──  *.css
 ├──  favico.ico
 ├──  index.html
 └──  404.html
</pre>

The code is now organized in a component type of architecture, where everything is logically grouped.

#### File type name convention

In order to understand what is contained inside the .js files without opening it, we use some name conventions:
* .controller.js
* .directive.js
* .service.js

### Component Architecture

When we talk about Component architecture, we think about grouping files together in a logical way.

A component can then be made of multiple files like `.html`, `.css` or any other file type mentioned above.

Related components can be grouped as sub-component as long as they are used in that component only.


#### App folder

Contains the application `app.js` and page related components.
* Home Page
* Interpreter Page
* Notebook Page
etc...

The only resctiction being that a component in the `app` folder is **not used anywhere else**

#### Components folder

The `components` folder is here to contains any reusable component (used more than once)

### Fonts

Fonts files and their css are mixed together in the `fonts` folder

## New files includes

As we do not use yeoman to generate controllers or other type of files with this new structure,
we need to do some includes manually in `index.html` in order to use dev mode and compile correctly.

* Non-bower `.js` files needs to be injected between the tag `<!-- build:js({.tmp,src}) scripts/scripts.js -->`
* Css files needs to be injected between the tag `<!-- build:css(.tmp) styles/main.css -->`

## Add plugins with Bower
```
bower install <plugin> --save
```
The file index.html will automatically update with the new bower_component

<br/>

**Example**: `./bower install angular-nvd3` 

You should find that line in the index.html file
```
<script src="bower_components/angular-nvd3/dist/angular-nvd3.js"></script>
````
