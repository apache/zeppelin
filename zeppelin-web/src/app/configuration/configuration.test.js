import template from './configuration.html'

describe('Controller: Configuration', function () {
  beforeEach(angular.mock.module('zeppelinWebApp'))

  let ctrl
  let scope
  let compile
  let controllerGen

  beforeEach(inject(($controller, $rootScope, $compile) => {
    scope = $rootScope.$new()
    compile = $compile
    controllerGen = $controller
  }))

  it('ConfigurationCtrl to toBeDefined', () => {
    ctrl = controllerGen('ConfigurationCtrl', { $scope: scope })
    expect(ctrl).toBeDefined()
  })

  it('should render list of configurations as the sorted order', () => {
    scope.configurations = {
      'zeppelin.server.port': '8080',
      'zeppelin.server.addr': '0.0.0.0',
    }
    const elem = compile(template)(scope);
    scope.$digest();
    const tbody = elem.find('tbody')
    const tds = tbody.find('td')

    expect(tds[0].innerText.trim()).toBe('zeppelin.server.addr')
    expect(tds[1].innerText.trim()).toBe('0.0.0.0')
    expect(tds[2].innerText.trim()).toBe('zeppelin.server.port')
    expect(tds[3].innerText.trim()).toBe('8080')
  })
})
