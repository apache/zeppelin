---
layout: page
title: "Introducing New Chart Library"
description: "Introducing New Chart Library"
group: development
---
{% include JB/setup %}

### Why Charts are important in zeppeline?
Zeppeline is mostly used for data analysis and visualization. Depending on the user requirements and datasets the types of charts needed could differ. So Zeppeline let user to add different chart libraries and chart types.

<br />
### Add New Chart Library
When needed a new JS chart library than D3 (nvd3) which is included in zeppelin, a new JS library for zeppelin-web is added by adding name in zeppelin-web/bower.json

eg: Adding map visualization to Zeppeline using leaflet

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

A new function can be used to handle new charts. Example function `setMapChart()`
