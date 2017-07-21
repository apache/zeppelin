import { ParagraphStatus } from '../../notebook/paragraph/paragraph.status'

describe('JobComponent', () => {
  let $componentController

  beforeEach(angular.mock.module('zeppelinWebApp'))
  beforeEach(angular.mock.inject((_$componentController_) => {
    $componentController = _$componentController_
  }))

  it('should get progress when there is a finished paragraph', () => {
    const paragraphs = [
      { status: ParagraphStatus.FINISHED },
    ]
    const mockNote = createMockNote(paragraphs)
    const bindings = { note: mockNote, }

    const ctrl = $componentController('job', null, bindings)
    expect(ctrl).toBeDefined()

    const progress1 = ctrl.getProgress()
    expect(progress1).toBe('100%')
  })

  it('should get progress when there is pending and finished paragraphs', () => {
    const paragraphs = [
      { status: ParagraphStatus.PENDING },
      { status: ParagraphStatus.FINISHED},
    ]
    const mockNote = createMockNote(paragraphs)
    const bindings = { note: mockNote, }

    const ctrl = $componentController('job', null, bindings)

    const progress1 = ctrl.getProgress()
    expect(progress1).toBe('50%')
  })

  it('should get proper job type icons', () => {
    const paragraphs = [ { status: ParagraphStatus.PENDING }, ]
    const mockNote = createMockNote(paragraphs)
    const bindings = { note: mockNote, }

    const ctrl = $componentController('job', null, bindings)

    let icon = ctrl.getJobTypeIcon()
    expect(icon).toBe('icon-doc')

    mockNote.noteType = 'cron'
    icon = ctrl.getJobTypeIcon()
    expect(icon).toBe('icon-clock')
  })

  function createMockNote(paragraphs) {
    return {
      isRunningJob: false,
      paragraphs: paragraphs,
      noteId: 'NT01',
      noteName: 'TestNote01',
      noteType: 'normal',
    }
  }
})
