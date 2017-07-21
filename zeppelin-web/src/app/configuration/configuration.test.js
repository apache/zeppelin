import template from './configuration.html'

describe('Controller: Configuration', function () {
  beforeEach(angular.mock.module('zeppelinWebApp'))

  let baseUrlSrvMock = { getRestApiBase: () => '' }

  let ctrl // controller instance
  let $scope
  let $compile
  let $controller // controller generator
  let $httpBackend
  let ngToast

  beforeEach(inject((_$controller_, _$rootScope_, _$compile_, _$httpBackend_, _ngToast_) => {
    $scope = _$rootScope_.$new()
    $compile = _$compile_
    $controller = _$controller_
    $httpBackend = _$httpBackend_
    ngToast = _ngToast_
  }))

  afterEach(function () {
    $httpBackend.verifyNoOutstandingExpectation()
    $httpBackend.verifyNoOutstandingRequest()
  })

  it('should get configuration initially', () => {
    const conf = { 'conf1': 'value1' }
    ctrl = $controller('ConfigurationCtrl', { $scope: $scope, baseUrlSrv: baseUrlSrvMock, })
    expect(ctrl).toBeDefined()

    $httpBackend
      .when('GET', '/configurations/all')
      .respond(200, { body: conf, })
    $httpBackend.expectGET('/configurations/all')
    $httpBackend.flush()

    expect($scope.configurations).toEqual(conf) // scope is updated after $httpBackend.flush()
  })

  it('should display ngToast when failed to get configuration properly', () => {
    ctrl = $controller('ConfigurationCtrl', { $scope: $scope, baseUrlSrv: baseUrlSrvMock, })
    spyOn(ngToast, 'danger')

    $httpBackend.when('GET', '/configurations/all').respond(401, {})
    $httpBackend.expectGET('/configurations/all')
    $httpBackend.flush()

    expect(ngToast.danger).toHaveBeenCalled()
  })

  it('should render list of configurations as the sorted order', () => {
    $scope.configurations = {
      'zeppelin.server.port': '8080',
      'zeppelin.server.addr': '0.0.0.0',
    }
    const elem = $compile(template)($scope)
    $scope.$digest()
    const tbody = elem.find('tbody')
    const tds = tbody.find('td')

    // should be sorted
    expect(tds[0].innerText.trim()).toBe('zeppelin.server.addr')
    expect(tds[1].innerText.trim()).toBe('0.0.0.0')
    expect(tds[2].innerText.trim()).toBe('zeppelin.server.port')
    expect(tds[3].innerText.trim()).toBe('8080')
  })
})
