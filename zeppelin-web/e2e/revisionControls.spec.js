describe('Revision controls e2e Test', function() {
  /*Common methods for interact with elements*/
  let clickOn = function (elem) {
    browser.actions().mouseMove(elem).click().perform()
  }

  let clickAndWait = function(elem) {
    clickOn(elem)
    browser.sleep(60);
  }

  let waitVisibility = function(elem) {
    browser.wait(protractor.ExpectedConditions.visibilityOf(elem))
  }

  beforeEach(function() {
    browser.get('http://localhost:8080')
    clickOn(element(by.linkText('Create new note')))
    waitVisibility(element(by.id('noteCreateModal')))
    clickAndWait(element(by.id('createNoteButton')))
  })

  afterEach(function() {
    clickOn(element(by.xpath('//*[@id="main"]//button[@ng-click="moveNoteToTrash(note.id)"]')))
    let moveToTrashDialogPath =
      '//div[@class="modal-dialog"][contains(.,"This note will be moved to trash")]'
    waitVisibility(element(by.xpath(moveToTrashDialogPath)))
    let okButton = element(
      by.xpath(moveToTrashDialogPath + '//div[@class="modal-footer"]//button[contains(.,"OK")]'))
    clickOn(okButton)
  })

  //tests
  it('should have revision controls even after refresh', function() {
    waitVisibility(element(by.xpath('//button[@id="versionControlDropdown"]')))

    // revision controls should be visible after refresh as well
    browser.refresh()
    waitVisibility(element(by.xpath('//button[@id="versionControlDropdown"]')))
    browser.refresh()
    waitVisibility(element(by.xpath('//button[@id="versionControlDropdown"]')))
    browser.refresh()
    waitVisibility(element(by.xpath('//button[@id="versionControlDropdown"]')))
  })
})
