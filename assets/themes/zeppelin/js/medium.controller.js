angular.module("app").controller("MediumCtrl", function($scope, $window, $sce) {
  $scope.mediumPost = mediumPost

  var postInfo = $scope.mediumPost[0].items
  var postInfoArray = []

  var init = function () {
    createPostInfoArray()
  }

  var unicodeToChar = function (text) {
    return text.replace(/\&#x[\dA-F]{4}/gi,
      function (match) {
        return String.fromCharCode(parseInt(match.replace(/\\u/g, ''), 16));
      })
  }

  var truncateString = function (string) {
    return string.length > 150 ? string.substring(0, 150) + '...' : string
  }
  
  var createPostInfoArray = function () {
    for (var idx in postInfo) {
      var post = postInfo[idx]

      // 1. remove HTML tag from description value
      var regExString = /(<([^>]+)>)/ig
      post.description = post.description.replace(regExString, '')
      // 2. remove 'Continue reading on Apache Zeppelin Stories »'
      post.description = post.description.replace(/Continue reading on Apache Zeppelin Stories »/g, '')
      // 3. replace unicode char -> string
      post.description = unicodeToChar(post.description)
      // 4. truncate description string & attach '...'
      post.description = truncateString(post.description)

      // parse strigified date to 'MMMM Do, YYYY' format (e.g October 4th, 2016)
      post.created = new Date(post.created)
      post.created = moment(post.created).format("MMMM Do, YYYY")

      postInfoArray.push(post)
    }

    $scope.postInfoArray = postInfoArray
  }

  init()
})