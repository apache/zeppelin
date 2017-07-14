
describe('Controller: Credential', function () {
  beforeEach(angular.mock.module('zeppelinWebApp'))

  let baseUrlSrvMock = { getRestApiBase: () => '' }

  let $scope
  let $controller // controller generator
  let $httpBackend

  beforeEach(inject((_$controller_, _$rootScope_, _$compile_, _$httpBackend_, _ngToast_) => {
    $scope = _$rootScope_.$new()
    $controller = _$controller_
    $httpBackend = _$httpBackend_
  }))

  const credentialResponse = { 'spark.testCredential': { username: 'user1', password: 'password1' }, }
  const interpreterResponse = [
    { 'name': 'spark', 'group': 'spark', },
    { 'name': 'md', 'group': 'md', },
  ] // simplified

  function setupInitialization(credentialRes, interpreterRes) {
    // requests should follow the exact order
    $httpBackend
      .when('GET', '/interpreter/setting')
      .respond(200, { body: interpreterRes, })
    $httpBackend.expectGET('/interpreter/setting')
    $httpBackend
      .when('GET', '/credential')
      .respond(200, { body: { userCredentials: credentialRes, } })
    $httpBackend.expectGET('/credential')

    // should flush after calling this function
  }

  it('should get available interpreters and credentials initially', () => {
    const ctrl = createController()
    expect(ctrl).toBeDefined()

    setupInitialization(credentialResponse, interpreterResponse)
    $httpBackend.flush()

    expect($scope.credentialInfo).toEqual(
      [{ entity: 'spark.testCredential', username: 'user1', password: 'password1'}]
    )
    expect($scope.availableInterpreters).toEqual(
      ['spark.spark', 'md.md']
    )

    $httpBackend.verifyNoOutstandingExpectation()
    $httpBackend.verifyNoOutstandingRequest()
  })

  it('should toggle using toggleAddNewCredentialInfo', () => {
    createController()

    expect($scope.showAddNewCredentialInfo).toBe(false)
    $scope.toggleAddNewCredentialInfo()
    expect($scope.showAddNewCredentialInfo).toBe(true)
    $scope.toggleAddNewCredentialInfo()
    expect($scope.showAddNewCredentialInfo).toBe(false)
  })

  it('should check empty credentials using isInvalidCredential', () => {
    createController()

    $scope.entity = ''
    $scope.username = ''
    expect($scope.isValidCredential()).toBe(false)

    $scope.entity = 'spark1'
    $scope.username = ''
    expect($scope.isValidCredential()).toBe(false)

    $scope.entity = ''
    $scope.username = 'user1'
    expect($scope.isValidCredential()).toBe(false)

    $scope.entity = 'spark'
    $scope.username = 'user1'
    expect($scope.isValidCredential()).toBe(true)
  })

  it('should be able to add credential via addNewCredentialInfo', () => {
    const ctrl = createController()
    expect(ctrl).toBeDefined()
    setupInitialization(credentialResponse, interpreterResponse)

    // when
    const newCredential = { entity: 'spark.sql', username: 'user2', password: 'password2'}

    $httpBackend
      .when('PUT', '/credential', newCredential)
      .respond(200, { })
    $httpBackend.expectPUT('/credential', newCredential)

    $scope.entity = newCredential.entity
    $scope.username = newCredential.username
    $scope.password = newCredential.password
    $scope.addNewCredentialInfo()

    $httpBackend.flush()

    expect($scope.credentialInfo[1]).toEqual(newCredential)

    $httpBackend.verifyNoOutstandingExpectation()
    $httpBackend.verifyNoOutstandingRequest()
  })

  function createController() {
    return $controller('CredentialCtrl', { $scope: $scope, baseUrlSrv: baseUrlSrvMock, })
  }
})
