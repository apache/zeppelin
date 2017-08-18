describe('Home e2e Test', function() {
  it('should have a welcome message', function() {
    browser.get('http://localhost:8080');
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
})
