import { ParagraphStatus } from '../notebook/paragraph/paragraph.status'
import { JobManagerService } from './jobmanager.service'

describe('JobManagerService', () => {
  const baseUrlSrvMock = { getRestApiBase: () => '' }
  let service
  let $httpBackend

  beforeEach(angular.mock.module('zeppelinWebApp'))
  beforeEach(angular.mock.inject((_$rootScope_, _$httpBackend_, _$http_, _websocketMsgSrv_) => {
    $httpBackend = _$httpBackend_
    service = new JobManagerService(_$http_, _$rootScope_, baseUrlSrvMock, _websocketMsgSrv_)
  }))

  it('should sent valid request to run a job', () => {
    const paragraphs = [ { status: ParagraphStatus.PENDING }, ]
    const mockNote = createMockNote(paragraphs)

    const noteId = mockNote.noteId
    service.sendRunJobRequest(noteId)

    const url = `/notebook/job/${noteId}`

    $httpBackend
      .when('POST', url)
      .respond(200, { /** return nothing */ })
    $httpBackend.expectPOST(url)
    $httpBackend.flush()

    checkUnknownHttpRequests()
  })

  it('should sent valid request to stop a job', () => {
    const paragraphs = [ { status: ParagraphStatus.PENDING }, ]
    const mockNote = createMockNote(paragraphs)

    const noteId = mockNote.noteId
    service.sendStopJobRequest(noteId)

    const url = `/notebook/job/${noteId}`

    $httpBackend
      .when('DELETE', url)
      .respond(200, { /** return nothing */ })
    $httpBackend.expectDELETE(url)
    $httpBackend.flush()

    checkUnknownHttpRequests()
  })

  function checkUnknownHttpRequests() {
    $httpBackend.verifyNoOutstandingExpectation()
    $httpBackend.verifyNoOutstandingRequest()
  }

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
