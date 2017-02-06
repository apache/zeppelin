angular.module("app", []).controller("MediumCtrl", function($scope, $window, $sce) {
  $scope.mediumPost = mediumPost

  var postInfo = $scope.mediumPost[0].items
  var postInfoArray = []

  function unicodeToChar(text) {
   return text.replace(/\&#x[\dA-F]{4}/gi, 
    function (match) {
      return String.fromCharCode(parseInt(match.replace(/\\u/g, ''), 16));
    })
  }
  
  for (var idx in postInfo) {
    var eachPosts = postInfo[idx]

    // 1. remove HTML tag from description value
    var regExString = /(<([^>]+)>)/ig
    postInfo[idx].description = postInfo[idx].description.replace(regExString, '')
    // 2. remove 'Continue reading on Apache Zeppelin Stories »'
    postInfo[idx].description = postInfo[idx].description.replace(/Continue reading on Apache Zeppelin Stories »/g, '')
    // 3. replace unicode char -> string
    postInfo[idx].description = unicodeToChar(postInfo[idx].description)

    // parse strigified date to 'MMMM Do, YYYY' format (e.g October 4th, 2016)
    postInfo[idx].created = new Date(postInfo[idx].created)
    postInfo[idx].created = moment(postInfo[idx].created).format("MMMM Do, YYYY")
    
    postInfoArray.push(postInfo[idx])
  }

  $scope.postInfoArray = postInfoArray
});