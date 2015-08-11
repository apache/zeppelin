---
layout: page
title: "Data Validation Service"
description: "Data Validation Service"
group: development
---

## Data Validation

Data validation is a process of ensuring data in zeppelin is clean, correct and according to the data schema model. Data validation provides certain well-defined rule set for fitness, and consistency checking for zeppelin charts.


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
