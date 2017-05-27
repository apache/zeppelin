---
layout: page
title: "Transformations in Zeppelin Visualization"
description: "Description for Transformations"
group: development/helium
---
<!--
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
{% include JB/setup %}

# Transformations for Zeppelin Visualization

<div id="toc"></div>

## Overview 

Transformations 

- **renders** setting which allows users to set columns and 
- **transforms** table rows according to the configured columns.

Zeppelin provides 4 types of transformations.

## 1. PassthroughTransformation

`PassthroughTransformation` is the simple transformation which does not convert original tabledata at all.

See [passthrough.js](https://github.com/apache/zeppelin/blob/master/zeppelin-web/src/app/tabledata/passthrough.js)

## 2. ColumnselectorTransformation

`ColumnselectorTransformation` is uses when you need `N` axes but do not need aggregation. 

See [columnselector.js](https://github.com/apache/zeppelin/blob/master/zeppelin-web/src/app/tabledata/columnselector.js)

## 3. PivotTransformation

`PivotTransformation` provides group by and aggregation. Every chart using `PivotTransformation` has 3 axes. `Keys`, `Groups` and `Values`.

See [pivot.js](https://github.com/apache/zeppelin/blob/master/zeppelin-web/src/app/tabledata/pivot.js)

## 4. AdvancedTransformation

`AdvancedTransformation` has more detailed options while providing existing features of `PivotTransformation` and `ColumnselectorTransformation`

- multiple sub charts
- configurable chart axes
- parameter widgets: `input`, `checkbox`, `option`, `textarea`
- parsing parameters automatically based on their types
- expand / fold axis and parameter panels
- multiple transformation methods while supporting lazy converting 
- re-initialize the whole configuration based on spec hash.

### Spec 

`AdvancedTransformation` requires `spec` which includes axis and parameter details for charts.

Let's create 2 sub-charts called `line` and `no-group`. Each sub chart can have different axis and parameter depending on their requirements.

<br/>

```js
class AwesomeVisualization extends Visualization {
  constructor(targetEl, config) {
    super(targetEl, config)
  
    const spec = {
      charts: {
        'line': {
          transform: { method: 'object', },
          sharedAxis: false, /** set if you want to share axes between sub charts, default is `false` */
          axis: {
            'xAxis': { dimension: 'multiple', axisType: 'key', description: 'serial', },
            'yAxis': { dimension: 'multiple', axisType: 'aggregator', description: 'serial', },
            'category': { dimension: 'multiple', axisType: 'group', description: 'categorical', },
          },
          parameter: {
            'xAxisUnit': { valueType: 'string', defaultValue: '', description: 'unit of xAxis', },
            'yAxisUnit': { valueType: 'string', defaultValue: '', description: 'unit of yAxis', },
            'lineWidth': { valueType: 'int', defaultValue: 0, description: 'width of line', },
          },
        },
  
        'no-group': {
          transform: { method: 'object', },
          sharedAxis: false,
          axis: {
            'xAxis': { dimension: 'single', axisType: 'key', },
            'yAxis': { dimension: 'multiple', axisType: 'value', },
          },
          parameter: {
            'xAxisUnit': { valueType: 'string', defaultValue: '', description: 'unit of xAxis', },
            'yAxisUnit': { valueType: 'string', defaultValue: '', description: 'unit of yAxis', },
        },
      },
    }

    this.transformation = new AdvancedTransformation(config, spec)
  }
  
  ...
  
  // `render` will be called whenever `axis` or `parameter` is changed 
  render(data) {
    const { chart, parameter, column, transformer, } = data
   
    if (chart === 'line') {
      const transformed = transformer()
      // draw line chart 
    } else if (chart === 'no-group') {
      const transformed = transformer()
      // draw no-group chart 
    }
  }
}
```

<br/>

### Spec: `axis` 

| Field Name | Available Values (type) | Description |
| --- | --- | --- |
|`dimension` | `single` | Axis can contains only 1 column |
|`dimension` | `multiple` | Axis can contains multiple columns |
|`axisType` | `key` | Column(s) in this axis will be used as `key` like in `PivotTransformation`. These columns will be served in `column.key` |
|`axisType` | `aggregator` | Column(s) in this axis will be used as `value` like in `PivotTransformation`. These columns will be served in `column.aggregator` |
|`axisType` | `group` | Column(s) in this axis will be used as `group` like in `PivotTransformation`. These columns will be served in `column.group` |
|`axisType` | (string) | Any string value can be used here. These columns will be served in `column.custom` |
|`maxAxisCount` (optional) | (int) | The max number of columns that this axis can contain. (unlimited if `undefined`) |
|`minAxisCount` (optional) | (int) | The min number of columns that this axis should contain to draw chart. (`1` in case of single dimension) |
|`description` (optional) | (string) | Description for the axis. |

<br/>

Here is an example.

```js
axis: {
  'xAxis': { dimension: 'multiple', axisType: 'key',  },
  'yAxis': { dimension: 'multiple', axisType: 'aggregator'},
  'category': { dimension: 'multiple', axisType: 'group', maxAxisCount: 2, valueType: 'string', },
},
```

<br/>

### Spec: `sharedAxis` 

If you set `sharedAxis: false` for sub charts, then their axes are persisted in global space (shared). It's useful for when you creating multiple sub charts sharing their axes but have different parameters. For example, 

- `basic-column`, `stacked-column`, `percent-column`
- `pie` and `donut`

<br/>

Here is an example.

```js
    const spec = {
      charts: {
        'column': {
          transform: { method: 'array', },
          sharedAxis: true,
          axis: { ... },
          parameter: { ... },
        },

        'stacked': {
          transform: { method: 'array', },
          sharedAxis: true,
          axis: { ... }
          parameter: { ... },
        },
```

<br/>

### Spec: `parameter` 

| Field Name | Available Values (type) | Description |
| --- | --- | --- |
|`valueType` | `string` | Parameter which has string value |
|`valueType` | `int` | Parameter which has int value |
|`valueType` | `float` | Parameter which has float value |
|`valueType` | `boolean` | Parameter which has boolean value used with `checkbox` widget usually |
|`valueType` | `JSON` | Parameter which has JSON value used with `textarea` widget usually. `defaultValue` should be `""` (empty string). This ||`defaultValue` | (any) | Default value of this parameter. `JSON` type should have `""` (empty string) |
|`description` | (string) | Description of this parameter. This value will be parsed as HTML for pretty output |
|`widget` | `input` |  Use [input](https://developer.mozilla.org/en/docs/Web/HTML/Element/input) widget. This is the default widget (if `widget` is undefined)|
|`widget` | `checkbox` |  Use [checkbox](https://developer.mozilla.org/en-US/docs/Web/HTML/Element/input/checkbox) widget. |
|`widget` | `textarea` |  Use [textarea](https://developer.mozilla.org/en-US/docs/Web/HTML/Element/textarea) widget. |
|`widget` | `option` |  Use [select + option](https://developer.mozilla.org/en-US/docs/Web/HTML/Element/select) widget. This parameter should have `optionValues` field as well. |
|`optionValues` | (Array<string>) |  Available option values used with the `option` widget |

<br/>

Here is an example.

```js
parameter: {
  // string type, input widget
  'xAxisUnit': { valueType: 'string', defaultValue: '', description: 'unit of xAxis', },

  // boolean type, checkbox widget
  'inverted': { widget: 'checkbox', valueType: 'boolean', defaultValue: false, description: 'invert x and y axes', },

  // string type, option widget with `optionValues`
  'graphType': { widget: 'option', valueType: 'string', defaultValue: 'line', description: 'graph type', optionValues: [ 'line', 'smoothedLine', 'step', ], },

  // HTML in `description`
  'dateFormat': { valueType: 'string', defaultValue: '', description: 'format of date (<a href="https://docs.amcharts.com/3/javascriptcharts/AmGraph#dateFormat">doc</a>) (e.g YYYY-MM-DD)', },
 
  // JSON type, textarea widget
  'yAxisGuides': { widget: 'textarea', valueType: 'JSON', defaultValue: '', description: 'guides of yAxis ', },
```

<br/>

### Spec: `transform`

| Field Name | Available Values (type) | Description |
| --- | --- | --- |
|`method` | `object` |  designed for rows requiring object manipulation | 
|`method` | `array` |  designed for rows requiring array manipulation | 
|`method` | `array:2-key` |  designed for xyz charts (e.g bubble chart) | 
|`method` | `drill-down` |  designed for drill-down charts | 
|`method` | `raw` | will return the original `tableData.rows` | 

<br/>

Whatever you specified as `transform.method`, the `transformer` value will be always function for lazy computation. 

```js
// advanced-transformation.util#getTransformer

if (transformSpec.method === 'raw') {
  transformer = () => { return rows; }
} else if (transformSpec.method === 'array') {
  transformer = () => {
    ...
    return { ... }
  }
}
```

Here is actual usage.

```js
class AwesomeVisualization extends Visualization {
  constructor(...) { /** setup your spec */ }
  
  ... 
  
  // `render` will be called whenever `axis` or `parameter` are changed
  render(data) {
    const { chart, parameter, column, transformer, } = data
   
    if (chart === 'line') {
      const transformed = transformer()
      // draw line chart 
    } else if (chart === 'no-group') {
      const transformed = transformer()
      // draw no-group chart 
    }
  }
  
  ...
}
```

