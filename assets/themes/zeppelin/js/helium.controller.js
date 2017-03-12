angular.module("app", ['ui.bootstrap'])
  .controller("HeliumPkgCtrl", function($rootScope, $scope, $window, $sce) {
  $rootScope.keys = Object.keys
  $scope.HeliumPkgs = zeppelinHeliumPackages
  $scope.npmWebLink = 'https://www.npmjs.com/package'
  $scope.intpDefaultIcon = $sce.trustAsHtml('<img src="assets/themes/zeppelin/img/maven_default_icon.png" style="width: 12px"/>');
  $scope.latestPkgInfo = {}
  $scope.allTypePkgs = {}

  const HeliumType = [
    'VISUALIZATION',
    'SPELL',
    'INTERPRETER',
  ]

  $scope.allPackageTypes = HeliumType
  $scope.pkgListByType = 'ALL'

  var init = function () {
    createLatestPkgInfo()
    classifyPkgByType($scope.latestPkgInfo)
    setPagination()
  }

  var createLatestPkgInfo = function() {
    var latestPkgInfo = []
    var pkgsInfo = $scope.HeliumPkgs

    for (var idx in pkgsInfo) {
      var eachPkgInfo = pkgsInfo[idx]
      for (var key in eachPkgInfo) {
        // key: pkg's name
        var latestPkg = eachPkgInfo[key]
        for (var ver in latestPkg){
          if (ver == "latest") {
            latestPkgInfo.push(latestPkg[ver])
            latestPkg[ver].icon = $sce.trustAsHtml(latestPkg[ver].icon)

          }
        }
      }
    }

    $scope.latestPkgInfo = latestPkgInfo
    $scope.numberOfAllPkgs = latestPkgInfo.length
  }

  var classifyPkgByType = function(latestPkgInfo) {
    var vizTypePkgs = []
    var spellTypePkgs = []
    var interpreterTypePkgs = []
    var allTypePkgs = {}

    for (var idx in latestPkgInfo) {
      switch (latestPkgInfo[idx].type) {
        case "VISUALIZATION":
          vizTypePkgs.push(latestPkgInfo[idx])
          break
        case "SPELL":
          spellTypePkgs.push(latestPkgInfo[idx])
          break
        case "INTERPRETER":
          interpreterTypePkgs.push(latestPkgInfo[idx])
          break
      }
    }

    var tmpArr = [
      vizTypePkgs,
      spellTypePkgs,
      interpreterTypePkgs,
    ]
    for (var key in HeliumType) {
      allTypePkgs[HeliumType[key]] = tmpArr[key]
    }

    $scope.allTypePkgs = allTypePkgs
  }

  var setPagination = function() {
    $scope.itemsPerPage = 10
    $scope.currentPage = 1
    $scope.maxSize = 5

    $scope.setPage = function (pageNo) {
      $scope.currentPage = pageNo
    }

    $scope.pageChanged = function() {
      $log.log('Page changed to: ' + $scope.currentPage)
    }
  }

  init()
});
