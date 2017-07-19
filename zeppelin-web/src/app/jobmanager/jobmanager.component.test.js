describe('JobManagerComponent', () => {
  let $scope
  let $controller

  beforeEach(angular.mock.module('zeppelinWebApp'))
  beforeEach(angular.mock.inject((_$rootScope_, _$controller_) => {
    $scope = _$rootScope_.$new()
    $controller = _$controller_
  }))

  it('should set jobs using `setJobs`', () => {
    let ctrl = $controller('JobManagerCtrl', { $scope: $scope, })
    expect(ctrl).toBeDefined()

    const mockJobs = [
      { noteId: 'TN01', interpreter: 'spark', },
      { noteId: 'TN02', interpreter: 'spark', },
    ]

    $scope.setJobs(mockJobs)
    expect($scope.defaultInterpreters).toEqual([
      { name: 'ALL', value: '*', },
      { name: 'spark', value: 'spark', },
    ])
  })
})
