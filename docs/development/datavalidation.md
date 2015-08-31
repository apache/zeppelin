---
layout: page
title: "Data Validation Service"
description: "Data Validation Service"
group: development
---

## Data Validation

Data validation is a process of ensuring data in zeppelin is clean, correct and according to the data schema model. Data validation provides certain well-defined rule set for fitness, and consistency checking for zeppelin charts.

#### Where the data validator is used  in zeppelin?

Data validator is used in zeppelin before drawing charts or analyzing data. 

### Why the data validator is used?

In drawing charts you can validate dataset if under validate data model schema, example. Before visualizing the dataset in charts, dataset needs to validated against data model schema for a particular chart type.
This is because different chart types have different data models. eg: Pie charts, Bar charts and Area charts have label and a number. Scatter charts and Bubble charts have two numbers for x axis and y axis at minimum in their data mdoels.

### Why the data validator is important?

When user request to draw any visualization of a dataset, data validation services will run through the dataset and check if the dataset is valid against the data schema. If unsuccess it will give a message which record is mismatched against the data schema. So the user gets a more accurate visualization and correct decision finally. Also researchers and data analytics use it to verify the dataset is clean and the preprocessing is done correctly.

### How Data Validation is done?

Data Validation consists of service, factories and configs.Data Validation is exposed as Angular services. Data validation factory, which is extendable contains functional implementation. Schemas are defined as constants in config. It contains basic data type validation by default 

Developers can introduce new data validation factories for their chart types by extending data validator factory. If a new chart consists of the same data schema existing data validators can be used.

### How to used exisiting Data Validation services
Zeppelin Data Validation is exposed as service in Zeppelin Web application. It can be called and the dataset can be passed as a parameter. 

`dataValidatorSrv.<dataModelValidateName>(data);`

This will return a message as below

```javascript
{
  'error': true / false,
  'msg': 'error msg / notification msg'
}
```

<br />
### How to Add New Data Validation Schema

Data Validation is implemented as factory model. Therefore customized Data Validation factory can be created by extending `DataValidator` (zeppelin-web/src/components/data-validator/data-validator-factory.js)

Data model schema in 'dataModelSchemas' can be configured. 

```javascript
'MapSchema': {
  type: ['string', 'string', 'number', 'number', 'number']
}
```
If beyond data type validation is needed a function for validating the record can be introduced. If Range and constraint validation, Code and Cross-reference validation or Structured validation are needed they can be added to the Data Validation factory.

<br />
### How to Expose New Data Validation Schema in Service
After adding a new data validation factory it needs to be exposed in `dataValidatorSrv` (zeppelin-web/src/components/data-validator/data-validator-service.js)

```javascript
  this.validateMapData = function(data) {
    var mapValidator = mapdataValidator;
    doBasicCheck(mapValidator,data);
    //any custom validation can be called in here
    return buildMsg(mapValidator);
  };
```

### Adding new Data Range Validation

Data Range Validation is important with regard to some datasets. As an example Geographic Information dataset  will contain geographic coordinates, Latitude measurements ranging from 0° to (+/–)90° and Longitude measurements ranging from 0° to (+/–)180°. All the values of Latitude and Longitude must to be inside a particular range. Therefore you can define range in schema and range validation function for factory as below.

Adding range for `MapSchema`

```javascript
'MapSchema': {
  type: ['string', 'string', 'number', 'number', 'number'],
  range: {
    latitude: {
      low: -90,
      high: 90
    },
    longitude: {
      low: -180,
      high: 180
    }
  }
}
```

Validating latitude in `mapdataValidator` factory 

```javascript
//Latitude measurements range from 0° to (+/–)90°.
function latitudeValidator(record, schema) {
var latitude = parseFloat(record);
if(schema.latitude.low < latitude && latitude < schema.latitude.high) {
msg += 'latitudes are ok | ';
} else {
msg += 'Latitude ' + record + ' is not in range | ';
errorStatus = true;
}
}
```

Few other sample validators can be found in zeppelin-web/src/components/data-validator/ directoy.
