# Zeppelin web application 2
This is a Zeppelin web frontend project.

## Get started 
The first thing you need to do is install Yeoman. We’re going to use the Node Package Manager to do this all at once.
 * `npm install -g yo`
 * `npm install -g generator-angular`

### Get the application deps
* `npm install`

## Run the application in dev mode
``./grunt serve``


## Add composents the the web app
 * New controller : `yo angular:controller <controlerName>`
 * New directive : `yo angular:directive <directiveName>`
 * New service : `yo angular:service <serviceName>`

 ### Add plugin
 
 `./bower install <plugin> -save`
 update the file index.html with the new bower_components 
 
 ex: `./bower install angular-nvd3` 
 ```
 <script src="bower_components/angular-nvd3/dist/angular-nvd3.js"></script>
 ````

## Build the application
`./grunt build`

## Deployment 
`mvn package`, will create the war file.

#### More info
http://www.sitepoint.com/kickstart-your-angularjs-development-with-yeoman-grunt-and-bower/
