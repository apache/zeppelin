angular.module("app", []).controller("HeliumPkgCtrl", function($scope, $window, $sce) {
  $scope.HeliumPkgs = zeppelinHeliumPackages;
  $scope.npmWebLink = 'https://www.npmjs.com/package'
  $scope.latestPkgInfo = {}
  var pkgsInfo = $scope.HeliumPkgs;
  var latestPkgInfo = []

  for (var idx in pkgsInfo) {
    var eachPkgInfo = pkgsInfo[idx]
    for (var key in eachPkgInfo) {
      // key: pkg's name
      var latestPkg = eachPkgInfo[key]
      for (var ver in latestPkg){
        if (ver == "latest") {
          latestPkgInfo.push(latestPkg[ver])
          latestPkg[ver].icon = $sce.trustAsHtml(latestPkg[ver].icon);

        }
      }
    }
  }
  $scope.latestPkgInfo = latestPkgInfo
});