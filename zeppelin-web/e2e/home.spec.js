describe('Home e2e Test', function() {
  /*Common methods for interact with elements*/
  let clickOn = function(elem) {
    browser.actions().mouseMove(elem).click().perform()
  }

  let sendKeysToInput = function(input, keys) {
    cleanInput(input)
    input.sendKeys(keys)
  }

  let cleanInput = function(inputElem) {
    inputElem.sendKeys(protractor.Key.chord(protractor.Key.CONTROL, "a"))
    inputElem.sendKeys(protractor.Key.BACK_SPACE)
  }

  let scrollToElementAndClick = function(elem) {
    browser.executeScript("arguments[0].scrollIntoView(false);", elem.getWebElement())
    browser.sleep(300)
    clickOn(elem)
  }

  //tests
  it('should have a welcome message', function() {
    browser.get('http://localhost:8080');
    browser.sleep(1000); // accomodate appveyor slower browser load
    var welcomeElem = element(by.id('welcome'))

    expect(welcomeElem.getText()).toEqual('Welcome to Zeppelin!')
  })

  it('should have the button for importing notebook', function() {
    var btn = element(by.cssContainingText('a', 'Import note'))
    expect(btn.isPresent()).toBe(true)
  })

  it('should have the button for creating notebook', function() {
    var btn = element(by.cssContainingText('a', 'Create new note'))
    expect(btn.isPresent()).toBe(true)
  })

  it('correct save permission in interpreter', function() {
    var ownerName = 'admin'
    var interpreterName = 'interpreter_e2e_test'
    clickOn(element(by.xpath('//span[@class="username ng-binding"]')))
    clickOn(element(by.xpath('//a[@href="#/interpreter"]')))
    clickOn(element(by.xpath('//button[@ng-click="showAddNewSetting = !showAddNewSetting"]')))
    sendKeysToInput(element(by.xpath('//input[@id="newInterpreterSettingName"]')), interpreterName)
    clickOn(element(by.xpath('//select[@ng-model="newInterpreterSetting.group"]')))
    browser.sleep(500)
    browser.actions().sendKeys(protractor.Key.ARROW_DOWN).perform()
    browser.actions().sendKeys(protractor.Key.ENTER).perform()
    clickOn(element(by.xpath('//div[@ng-show="showAddNewSetting"]//input[@id="idShowPermission"]')))
    sendKeysToInput(element(by.xpath('//div[@ng-show="showAddNewSetting"]//input[@class="select2-search__field"]')), ownerName)
    browser.sleep(500)
    browser.actions().sendKeys(protractor.Key.ENTER).perform()
    scrollToElementAndClick(element(by.xpath('//span[@ng-click="addNewInterpreterSetting()"]')))
    scrollToElementAndClick(element(by.xpath('//*[@id="' + interpreterName + '"]//span[@class="fa fa-pencil"]')))
    scrollToElementAndClick(element(by.xpath('//*[@id="' + interpreterName + '"]//button[@type="submit"]')))
    clickOn(element(by.xpath('//div[@class="bootstrap-dialog-footer-buttons"]//button[contains(text(), \'OK\')]')))
    browser.get('http://localhost:8080/#/interpreter');
    var text = element(by.xpath('//*[@id="' + interpreterName + '"]//li[contains(text(), \'admin\')]')).getText()
    scrollToElementAndClick(element(by.xpath('//*[@id="' + interpreterName + '"]//span//span[@class="fa fa-trash"]')))
    clickOn(element(by.xpath('//div[@class="bootstrap-dialog-footer-buttons"]//button[contains(text(), \'OK\')]')))
    expect(text).toEqual(ownerName);
  })
})
