'use strict';

describe('DataValidatorSrv ', function() {
  beforeEach(module('zeppelinWebApp'));

  var chartDataValidatorSrv;
  beforeEach(inject(function(dataValidatorSrv) {
    chartDataValidatorSrv = dataValidatorSrv;
  }));

  describe('validateMapData function', function() {

    var mockDataOne = {
      'msg': 'mock data',
      'code': 'SUCCESS',
      'rows': [
        ['us', 'El paso', '31.758', '-106.4869', '1147']
      ]
    };
    //test use case
    //testing with correct validated data set then change data and test again
    it('testing with validate GIS data and range check', function() {
      expect(chartDataValidatorSrv.validateMapData(mockDataOne).error)
        .toBe(
          false);
      expect(chartDataValidatorSrv.validateMapData(mockDataOne).msg)
        .toBe(
          'data is exisiting | latitudes are ok | longitude are ok | ');
      //change number to string and test
      mockDataOne.rows[0][3] = 'text';
      expect(chartDataValidatorSrv.validateMapData(mockDataOne).error)
        .toBe(
          true);
      expect(chartDataValidatorSrv.validateMapData(mockDataOne).msg)
        .toContain('data record text is not matching for schema');
      //testing for Longitude range
      mockDataOne.rows[0][3] = 250;
      expect(chartDataValidatorSrv.validateMapData(mockDataOne).error)
        .toBe(
          true);
      expect(chartDataValidatorSrv.validateMapData(mockDataOne).msg)
        .toBe('data is exisiting | latitudes are ok | Longitude250 is not in range | ');
    });

  });

});