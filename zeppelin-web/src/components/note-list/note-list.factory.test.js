describe('Factory: NoteList', function () {
  let noteList

  beforeEach(function () {
    angular.mock.module('zeppelinWebApp')

    inject(function ($injector) {
      noteList = $injector.get('noteListFactory')
    })
  })

  it('should generate both flat list and folder-based list properly', function () {
    let notesList = [
      {name: 'A', id: '000001'},
      {name: 'B', id: '000002'},
      {id: '000003'},                     // note without name
      {name: '/C/CA', id: '000004'},
      {name: '/C/CB', id: '000005'},
      {name: '/C/CB/CBA', id: '000006'},  // same name with a dir
      {name: '/C/CB/CBA', id: '000007'},  // same name with another note
      {name: 'C///CB//CBB', id: '000008'},
      {name: 'D/D[A/DA]B', id: '000009'}   // check if '[' and ']' considered as folder seperator
    ]
    noteList.setNotes(notesList)

    let flatList = noteList.flatList
    expect(flatList.length).toBe(9)
    expect(flatList[0].name).toBe('A')
    expect(flatList[0].id).toBe('000001')
    expect(flatList[1].name).toBe('B')
    expect(flatList[2].name).toBeUndefined()
    expect(flatList[3].name).toBe('/C/CA')
    expect(flatList[4].name).toBe('/C/CB')
    expect(flatList[5].name).toBe('/C/CB/CBA')
    expect(flatList[6].name).toBe('/C/CB/CBA')
    expect(flatList[7].name).toBe('C///CB//CBB')
    expect(flatList[8].name).toBe('D/D[A/DA]B')

    let folderList = noteList.root.children
    expect(folderList.length).toBe(5)
    expect(folderList[3].name).toBe('A')
    expect(folderList[3].id).toBe('000001')
    expect(folderList[4].name).toBe('B')
    expect(folderList[2].name).toBe('000003')
    expect(folderList[0].name).toBe('C')
    expect(folderList[0].id).toBe('C')
    expect(folderList[0].children.length).toBe(3)
    expect(folderList[0].children[0].name).toBe('CA')
    expect(folderList[0].children[0].id).toBe('000004')
    expect(folderList[0].children[0].children).toBeUndefined()
    expect(folderList[0].children[1].name).toBe('CB')
    expect(folderList[0].children[1].id).toBe('000005')
    expect(folderList[0].children[1].children).toBeUndefined()
    expect(folderList[0].children[2].name).toBe('CB')
    expect(folderList[0].children[2].id).toBe('C/CB')
    expect(folderList[0].children[2].children.length).toBe(3)
    expect(folderList[0].children[2].children[0].name).toBe('CBA')
    expect(folderList[0].children[2].children[0].id).toBe('000006')
    expect(folderList[0].children[2].children[0].children).toBeUndefined()
    expect(folderList[0].children[2].children[1].name).toBe('CBA')
    expect(folderList[0].children[2].children[1].id).toBe('000007')
    expect(folderList[0].children[2].children[1].children).toBeUndefined()
    expect(folderList[0].children[2].children[2].name).toBe('CBB')
    expect(folderList[0].children[2].children[2].id).toBe('000008')
    expect(folderList[0].children[2].children[2].children).toBeUndefined()
    expect(folderList[1].name).toBe('D')
    expect(folderList[1].id).toBe('D')
    expect(folderList[1].children.length).toBe(1)
    expect(folderList[1].children[0].name).toBe('D[A')
    expect(folderList[1].children[0].id).toBe('D/D[A')
    expect(folderList[1].children[0].children[0].name).toBe('DA]B')
    expect(folderList[1].children[0].children[0].id).toBe('000009')
    expect(folderList[1].children[0].children[0].children).toBeUndefined()
  })
})
