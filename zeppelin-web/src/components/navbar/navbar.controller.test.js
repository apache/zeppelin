describe('Controller: NavCtrl', function() {
  // load the controller's module
  beforeEach(angular.mock.module('zeppelinWebApp'));

  let NavCtrl;
  let scope;

  let websocketMsgSrvMock = {
    isConnected: function() {},
    getNoteList: function() {},
    getHomeNote: function() {},
    listConfigurations: function() {},
  };

  let ticketMock = {};

  // Initialize the controller and a mock scope
  beforeEach(inject(function($controller, $rootScope) {
    scope = $rootScope.$new();

    // spy on listConfigurations before creating the controller to catch calls during init
    spyOn(websocketMsgSrvMock, 'listConfigurations');

    NavCtrl = $controller('NavCtrl', {
      $scope: scope,
      websocketMsgSrv: websocketMsgSrvMock,
    });
  }));

  it('should be defined', function() {
    expect(NavCtrl).toBeDefined();
  });

  it('should call listConfigurations on init to get revision support config', function() {
    // listConfigurations was called once during init
    expect(websocketMsgSrvMock.listConfigurations.calls.count()).toEqual(1);
  });

  it('should call listConfigurations on login success to get revision support config', function() {
    // mock root scope ticket for the loginSuccess event
    scope.$root.ticket = ticketMock;

    scope.$broadcast('loginSuccess');

    // listConfigurations was called twice - once during init and then again after loginSuccess
    expect(websocketMsgSrvMock.listConfigurations.calls.count()).toEqual(2);
  });
});
