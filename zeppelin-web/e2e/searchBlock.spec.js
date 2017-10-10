describe('Search block e2e Test', function() {
  let testData = {
    textInFirstP: 'text word text',
    textInSecondP: 'text tete tt'
  }

  /*Common methods for interact with elements*/
  let clickOn = function(elem) {
    browser.actions().mouseMove(elem).click().perform()
  }

  let clickAndWait = function(elem){
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

  /*Getting elements*/
  let getFindInput = function() {
    return element(by.id('findInput'))
  }

  let getReplaceInput = function() {
    return element(
      by.xpath('//div[contains(@class, "search-group")]//span[text()="Replace"]//..//input'))
  }

  let getNextOccurrenceButton = function() {
    return element(by.xpath('//div[contains(@class, "search-group")]' +
      '/div/button[@ng-click="nextOccurrence()"]'))
  }

  let getPrevOccurrenceButton = function() {
    return element(by.xpath('//div[contains(@class, "search-group")]' +
      '/div/button[@ng-click="prevOccurrence()"]'))
  }

  let getReplaceButton = function() {
    return element(by.xpath('//div[contains(@class, "search-group")]//button[@ng-click="replace()"]'))
  }

  let getReplaceAllButton = function() {
    return element(
      by.xpath('//div[contains(@class, "search-group")]//button[@ng-click="replaceAll()"]'))
  }

  let getMatchesElement = function() {
    return element(by.xpath('//div[contains(@class, "search-group")]' +
      '//span[contains(@class, "after-input")]'))
  }

  /*Require: focus on any paragraph editor*/
  let openSearchBoxByShortcut = function() {
    browser.switchTo().activeElement().sendKeys(protractor.Key.chord(protractor.Key.CONTROL,
      protractor.Key.ALT, "f"))
  }

  let countSubstringOccurrence = function(s, subs) {
    return (s.match(new RegExp(subs,"g")) || []).length
  }

  let cleanInput = function(inputElem) {
    inputElem.sendKeys(protractor.Key.chord(protractor.Key.CONTROL, "a"))
    inputElem.sendKeys(protractor.Key.BACK_SPACE)
  }

  let checkFind = function(findInput, text, expectedMatchesCount) {
    cleanInput(findInput)
    findInput.sendKeys(text)
    let matchesCount = element(by.xpath('//div[contains(@class, "search-group")]' +
      '//span[contains(@class, "after-input")]'))
    matchesCount.getText().then(function(text) {
    expect(text.indexOf((expectedMatchesCount === 0 ? 0 : 1) + ' of ' +
      expectedMatchesCount) !== -1).toBe(true)
    })
  }

  let makeTestParagraphs = function() {
    waitVisibility(element(by.repeater('currentParagraph in note.paragraphs')))
    browser.switchTo().activeElement().sendKeys(testData.textInFirstP)
    let addBelow = element(
      by.xpath('//div[@class="new-paragraph last-paragraph" and @ng-click="insertNew(\'below\');"]'))
    clickAndWait(addBelow)
    browser.switchTo().activeElement().sendKeys(testData.textInSecondP)
  }

  let checkMatchesElement = function(elem, current, amount) {
    elem.getText().then(function(text) {
      expect(text.indexOf(current + ' of ' + amount) !== -1).toBe(true)})
  }

  let sendKeysToInput = function(input, keys) {
    cleanInput(input)
    input.sendKeys(keys)
  }

  let sendKeysToFindInput = function(keys) {
    sendKeysToInput(getFindInput(), keys)
  }

  let sendKeysToReplaceInput = function(keys) {
    sendKeysToInput(getReplaceInput(), keys)
  }

  /*Tests*/
  it('shortcut works', function() {
    waitVisibility(element(by.repeater('currentParagraph in note.paragraphs')))
    openSearchBoxByShortcut()
    expect(element(by.xpath('//ul[contains(@class,"search-dropdown")]')).isDisplayed()).toBeTruthy()
  })

  it('correct count of selections', function() {
    makeTestParagraphs()
    openSearchBoxByShortcut()
    let subs = 'te'
    sendKeysToFindInput(subs)
    var markers = element.all(by.xpath('//code-editor/div/div[@class="ace_scroller"]' +
      '/div[@class="ace_content"]/div[@class="ace_layer ace_marker-layer"]/div'))
    expect(markers.count()).toEqual(countSubstringOccurrence(testData.textInFirstP, subs) +
      countSubstringOccurrence(testData.textInSecondP, subs) + 1)
  })

  it('correct matches count number', function() {
    makeTestParagraphs()
    openSearchBoxByShortcut()
    let findInput = getFindInput()
    clickAndWait(findInput)
    let subs = 't';
    checkFind(findInput, subs, countSubstringOccurrence(testData.textInFirstP, subs) +
      countSubstringOccurrence(testData.textInSecondP, subs))
    subs = 'te';
    checkFind(findInput, subs, countSubstringOccurrence(testData.textInFirstP, subs) +
      countSubstringOccurrence(testData.textInSecondP, subs))
  })

  it('counter increase and decrease correctly', function() {
    makeTestParagraphs()
    openSearchBoxByShortcut()
    let subs = 'te'
    sendKeysToFindInput(subs)
    let matchesElement = getMatchesElement()
    let matchesCount = countSubstringOccurrence(testData.textInFirstP, subs) +
      countSubstringOccurrence(testData.textInSecondP, subs)
    checkMatchesElement(matchesElement, matchesCount > 0 ? 1 : 0, matchesCount)
    let nextOccurrenceButton = getNextOccurrenceButton()
    let prevOccurrenceButton = getPrevOccurrenceButton()
    clickOn(nextOccurrenceButton)
    checkMatchesElement(matchesElement, matchesCount > 1 ? 2 : 1, matchesCount)
    clickOn(prevOccurrenceButton)
    checkMatchesElement(matchesElement, 1, matchesCount)
    clickOn(prevOccurrenceButton)
    checkMatchesElement(matchesElement, matchesCount, matchesCount)
  })

  it('matches count changes correctly after replace', function() {
    makeTestParagraphs()
    openSearchBoxByShortcut()
    let textToFind = 'te'
    sendKeysToFindInput(textToFind)
    let matchesElement = getMatchesElement()
    let matchesCount = countSubstringOccurrence(testData.textInFirstP, textToFind) +
      countSubstringOccurrence(testData.textInSecondP, textToFind)
    sendKeysToReplaceInput('ABC')
    let replaceButton = getReplaceButton()
    clickOn(replaceButton)
    checkMatchesElement(matchesElement, 1, matchesCount - 1)
    clickOn(getPrevOccurrenceButton())
    clickOn(replaceButton)
    checkMatchesElement(matchesElement, 1, matchesCount - 2)
  })

  it('replace all works correctly', function() {
    makeTestParagraphs()
    openSearchBoxByShortcut()
    let textToFind = 'te'
    sendKeysToFindInput(textToFind)
    let matchesElement = getMatchesElement()
    let matchesCount = countSubstringOccurrence(testData.textInFirstP, textToFind) +
      countSubstringOccurrence(testData.textInSecondP, textToFind)
    sendKeysToReplaceInput('ABC')
    let replaceAllButton = getReplaceAllButton()
    clickOn(replaceAllButton)
    checkMatchesElement(matchesElement, 0, 0)
  })
})
