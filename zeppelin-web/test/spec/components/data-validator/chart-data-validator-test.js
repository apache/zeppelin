'use strict';

describe('DataValidatorSrv ', function() {
  beforeEach(module('zeppelinWebApp'));

  var chartDataValidatorSrv;
  beforeEach(inject(function(dataValidatorSrv) {
    chartDataValidatorSrv = dataValidatorSrv;
  }));

  describe('validateChartData function', function() {

    var mockDataOne = {
      'schema': Object,
      'rows': Object
    };
    it('testing with empty data', function() {
      expect(chartDataValidatorSrv.validateChartData({}).error).toBe(
        true);
      expect(chartDataValidatorSrv.validateChartData({}).msg).toBe(
        'data rows does not exisiting | ');
    });
    it('testing with data contain empty rows objects', function() {
      expect(chartDataValidatorSrv.validateChartData({}).error).toBe(
        true);
      expect(chartDataValidatorSrv.validateChartData({}).msg).toBe(
        'data rows does not exisiting | ');
    });
    //mock correct data set model
    var mockRecord = {
      'value(sum)': {
        'count': 1,
        'value': '4'
      }
    };
    var mockRow = {
      '19': mockRecord
    };
    mockDataOne.rows = mockRow;
    it('testing with validated data', function() {
      expect(chartDataValidatorSrv.validateChartData(mockDataOne).error)
        .toBe(false);
      expect(chartDataValidatorSrv.validateChartData(mockDataOne).msg)
        .toBe('');
    });
    //mock incorrect data set model
    var incorrectMockRecord = {
      'value(sum)': {
        'count': 1,
        'value': 'wrong'
      }
    };
    var incorrectMockRow = {
      '23': incorrectMockRecord
    };
    var mockDataTwo = {
      'schema': Object,
      'rows': incorrectMockRow
    };
    it('testing with invalidated data', function() {
      expect(chartDataValidatorSrv.validateChartData(mockDataTwo).error)
        .toBe(true);
      expect(chartDataValidatorSrv.validateChartData(mockDataTwo).msg)
        .toBe('data record wrong is not matching for schema | ');
    });
  });
});