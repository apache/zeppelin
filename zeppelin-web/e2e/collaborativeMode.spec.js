// Disable this test temporarily, See https://issues.apache.org/jira/browse/ZEPPELIN-5674
// describe('Collaborative mode tests', function () {
//
//   let clickOn = function(elem) {
//     browser.actions().mouseMove(elem).click().perform()
//   };
//
//   let waitVisibility = function(elem) {
//     browser.wait(protractor.ExpectedConditions.visibilityOf(elem))
//   };
//
//   let test_text_1 = "_one_more_text_for_tests";      // without space!!!
//   let test_text_2 = "Collaborative_mode_test_text";  // without space!!!
//
//   browser.get('http://localhost:8080');
//   clickOn(element(by.linkText('Create new note')));
//   waitVisibility(element(by.id('noteCreateModal')));
//   clickOn(element(by.id('createNoteButton')));
//   let user1Browser = browser.forkNewDriverInstance();
//   let user2Browser = browser.forkNewDriverInstance();
//   browser.getCurrentUrl().then(function (url) {
//     user1Browser.get(url);
//     user2Browser.get(url);
//   });
//   waitVisibility(element(by.xpath('//*[@uib-tooltip="Users who watch this note: anonymous"]')));
//   browser.sleep(500);
//
//   it('user 1 received the first patch', function () {
//     browser.switchTo().activeElement().sendKeys(test_text_1);
//     browser.sleep(500);
//     user1Browser.isElementPresent(by.xpath('//span[contains(text(), \'' + test_text_1 + '\')]'))
//       .then(function (isPresent) {
//       expect(isPresent).toBe(true);
//     });
//   });
//
//   it('user 2 received the first patch', function () {
//     user2Browser.isElementPresent(by.xpath('//span[contains(text(), \'' + test_text_1 + '\')]'))
//       .then(function (isPresent) {
//       expect(isPresent).toBe(true);
//     });
//   });
//
//   it('user root received a first patch', function () {
//     user1Browser.switchTo().activeElement().sendKeys(test_text_2);
//     user1Browser.sleep(500);
//     browser.isElementPresent(by.xpath('//span[contains(text(), \'' + test_text_2 +
//       test_text_1 + '\')]')).then(function (isPresent) {
//       expect(isPresent).toBe(true);
//     });
//   });
//
//   it('user 2 received the second patch', function () {
//     user2Browser.isElementPresent(by.xpath('//span[contains(text(), \'' + test_text_2 +
//       test_text_1 + '\')]')).then(function (isPresent) {
//       expect(isPresent).toBe(true);
//     });
//   });
//
//   it('finish', function () {
//     user1Browser.close();
//     user2Browser.close();
//     clickOn(element(by.xpath('//*[@id="main"]//button[@ng-click="moveNoteToTrash(note.id)"]')));
//     let moveToTrashDialogPath =
//       '//div[@class="modal-dialog"][contains(.,"This note will be moved to trash")]';
//     waitVisibility(element(by.xpath(moveToTrashDialogPath)));
//     let okButton = element(
//       by.xpath(moveToTrashDialogPath + '//div[@class="modal-footer"]//button[contains(.,"OK")]'));
//     clickOn(okButton);
//   });
//
// });
