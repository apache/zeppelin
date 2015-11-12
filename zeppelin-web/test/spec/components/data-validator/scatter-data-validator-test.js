'use strict';

describe('DataValidatorSrv ', function() {
  beforeEach(module('zeppelinWebApp'));

  var validateScatterDataSrv;
  beforeEach(inject(function(dataValidatorSrv) {
    validateScatterDataSrv = dataValidatorSrv;
  }));

  describe('validateScatterData function', function() {

    var mockDataOne = {
      'code': 'SUCCESS',
      'rows': Object
    };
    it('testing with empty data', function() {
      expect(validateScatterDataSrv.validateScatterData({}).error).toBe(
        true);
      expect(validateScatterDataSrv.validateScatterData({}).msg).toBe(
        'data rows does not exisiting | data does not exisiting | '
      );
    });
    it('testing with data contain empty rows objects', function() {
      //mockDataOne.rows = [[]];
      expect(validateScatterDataSrv.validateScatterData(mockDataOne)
        .error).toBe(
        true);
      expect(validateScatterDataSrv.validateScatterData(mockDataOne)
        .msg).toContain(
        'data is exisiting | ');
      expect(validateScatterDataSrv.validateScatterData(mockDataOne)
        .msg).toContain(
        'data record does not exisiting | ');
    });

    it('testing with valid data ', function() {
      mockDataOne.rows = [
        [12, 34]
      ];
      expect(validateScatterDataSrv.validateScatterData(mockDataOne)
        .error).toBe(
        false);
      expect(validateScatterDataSrv.validateScatterData(mockDataOne)
        .msg).toBe(
        'data is exisiting | ');
    });

    it('testing with string data model', function() {
      mockDataOne.rows = [
        ['test', '34']
      ];
      expect(validateScatterDataSrv.validateScatterData(mockDataOne)
        .error).toBe(
        true);
      expect(validateScatterDataSrv.validateScatterData(mockDataOne)
        .msg).toBe(
        'data record test is not matching for schema | data record does not mapping to data schema| data is exisiting | '
      );
    });

  });
});