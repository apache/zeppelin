# Overview
Markdown parsers for Apache Zeppelin. Markdown is a plain text formatting syntax designed so that it can be converted to HTML. Apache Zeppelin uses `flexmark`, `pegdown` and `markdown4j`.
Since both `pegdown` and `markdown4j` are deprecated but it support for backward compatibility.

# Architecture
Current interpreter implementation creates the instance of parser based on the configuration parameter provided, default is `flexmark` through `Markdown` and render the text into html.

### Technical overview
When interpreter is starting it check which parser has configured in  `markdown.parser.type`, based on this value, it creates the instance of that parser. For each subsequent request,
respective parser get the markdown text, parses it and renders into the html.

### Flexmark Parser Overview

CommonMark/Markdown Java parser with source level AST.

### Flexmark Requirement

 * maven dependency to add in pom.xml
 
 ```
<flexmark.all.version>0.62.2</flexmark.all.version>

 <dependency>
       <groupId>com.vladsch.flexmark</groupId>
       <artifactId>flexmark-all</artifactId>
       <version>${flexmark.all.version}</version>
 </dependency>
 ```

### Flexmark Technical overview for Custom Extension
To support, YUML and websequnce diagram, need to build the image URL from the respective block and render it into HTML, So it requires 
to implement some custom classes. `UMLExtension` is base class which has factory for other classes like `UMLBlockQuoteParser` and `UMLNodeRenderer`. 
`UMLBlockQuoteParser` which parses the UML block and creates block quote node `UMLBlockQuote`. 
`UMLNodeRenderer` which builds the URL using this block quote node `UMLBlockQuote` and render it as image into HTML. 
