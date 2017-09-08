import { ErrorHandlerService } from './error-handler.service'

describe('ErrorHandlerService', function () {
  beforeEach(angular.mock.module('zeppelinWebApp'))

  let ngToast
  let baseUrlSrv

  beforeEach(inject((_ngToast_, _baseUrlSrv_) => {
    ngToast = _ngToast_
    baseUrlSrv = _baseUrlSrv_
  }))

  it('should display danger toast when got 401', () => {
    const service = createService()
    spyOn(ngToast, 'danger')

    let responseHandler = service.handleHttpError('Got 401')
    responseHandler({
      status: 401, data: { message: 'auth error' }
    })

    expect(ngToast.danger).toHaveBeenCalledWith({
      content: 'You don\'t have permission on this page', verticalPosition: 'bottom', timeout: '3000'
    })
  })

  it('should display danger toast with default message when no response.data.message', () => {
    const service = createService()
    spyOn(ngToast, 'danger')

    let responseHandler = service.handleHttpError('Failed to do something')
    responseHandler({
      status: 500, data: { }
    })

    expect(ngToast.danger).toHaveBeenCalledWith({
      content: 'Failed to do something', verticalPosition: 'bottom', timeout: '3000'
    })
  })

  it('should display danger toast with data.body.message', () => {
    const service = createService()
    spyOn(ngToast, 'danger')

    let responseHandler = service.handleHttpError('Failed to do something')
    responseHandler({
      status: 500, data: { message: 'Internal Server Error' }
    })

    expect(ngToast.danger).toHaveBeenCalledWith({
      content: 'Internal Server Error', verticalPosition: 'bottom', timeout: '3000'
    })
  })

  function createService() {
    return new ErrorHandlerService(ngToast, baseUrlSrv)
  }
})
