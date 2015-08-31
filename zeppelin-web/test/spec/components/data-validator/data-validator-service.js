'use strict';

describe('Service : DataValidatorSrv', function() {
  beforeEach(module('zeppelinWebApp'));

  var chartDataValidatorSrv;
  beforeEach(inject(function(dataValidatorSrv) {
    chartDataValidatorSrv = dataValidatorSrv;
  }));

  describe('Testing API', function() {

    it('to be define', function() {
      expect(chartDataValidatorSrv).toBeDefined();
    });

    it('Services to be define', function() {
      expect(chartDataValidatorSrv.validateMapData).toBeDefined();
      expect(chartDataValidatorSrv.validateScatterData).toBeDefined();
      expect(chartDataValidatorSrv.validateChartData).toBeDefined();
    });
    //mock data for testing
    var mockData = {
      'schema': Object,
      'rows': Object
    };
    it('testing return message format', function() {
      expect(chartDataValidatorSrv.validateChartData(mockData).msg)
        .toBeDefined();
      expect(chartDataValidatorSrv.validateChartData(mockData).error)
        .toBeDefined();
    });

  });
});