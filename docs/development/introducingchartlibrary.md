---
layout: page
title: "Introducing New Chart Library"
description: "Introducing New Chart Library"
group: development
---
{% include JB/setup %}

### Why Charts are important in zeppeline?

<br />
### Add New Chart Library
If You need a new JS chart library rather than D3 (nvd3) which is included in zeppelin, add new JS library for zeppelin-web by adding name for zeppelin-web/bower.json

eg:

```
"leaflet": "~0.7.3" for dependencies
```

<br />
### Add New Chart Type

Add chart button paragraph.html (zeppelin-web/src/app/notebook/paragraph/paragraph.html)

```xml
<button type="button" class="btn btn-default btn-sm"
  ng-class="{'active': isGraphMode('mapChart')}"
  ng-click="setGraphMode('mapChart', true)"><i class="fa fa-globe"></i>
</button>
```

Include the chart view in paragraph.html

```html
<div ng-if="getGraphMode()=='mapChart'"
  id="p{{paragraph.id}}_mapChart">
    <leaflet></leaflet>
</div>
```

Update `setGraphMode()` function in paragraph.controller.js

```javascript
if (!type || type === 'mapChart') {
  //setting new chart type
}
```

Data can be retrived by `$scope.paragraph.result` inside function. 

<br />
### Best Practices for setting a new chart.

We can have a new function to handle new charts. Example function `setMapChart()`
