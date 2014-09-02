# Zeppelin web application 2
This is a Zeppelin web frontend project.

## Get started 
The first thing you need to do is install Yeoman, Grunt and Bower. We’re going to use the Node Package Manager to do this all at once.
`npm install -g yo grunt-cli bower`

## Get the application deps
`npm install`


## Start the application : `grunt serve`

### Add composents
 * New controller : `yo angular:controller <controlerName>`
 * New directive : `yo angular:directive <directiveName>`
 * New service : `yo angular:service <serviceName>`

 ### Add plugin
 
 `bower install angular-nvd3 -save`
 update the file index.html with the new bower_components 
 
 ex: `bower install angular-nvd3` 
 ```
 <script src="bower_components/angular-nvd3/dist/angular-nvd3.js"></script>
 ````

### Build the application
`grunt build`

## More info
http://www.sitepoint.com/kickstart-your-angularjs-development-with-yeoman-grunt-and-bower/

# NO POM YET , WORK IN PROGRESS