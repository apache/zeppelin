/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

export class ErrorHandlerService {
  constructor(ngToast, baseUrlSrv) {
    'ngInject'
    ngToast.dismiss()

    this.toast = ngToast
    this.BaseUrlService = baseUrlSrv
  }

  handleHttpError(defaultMessage) {
    const self = this

    return (response) => {
      const status = response.status
      let message = defaultMessage

      if (response.data && response.data.message) {
        message = response.data.message
      }

      if (status === 401) {
        self.showToast(message, 'danger')
        setTimeout(function () {
          window.location = self.BaseUrlService.getBase()
        }, 3000)
      } else {
        self.showToast(message, 'danger')
      }

      console.log('Error %o %o', status, message)
    }
  }

  showToast(message, type) {
    const verticalPosition = 'bottom'
    const timeout = '3000'

    if (type === 'success') {
      this.toast.success({ content: message, verticalPosition: verticalPosition, timeout: timeout, })
    } else if (type === 'info') {
      this.toast.info({ content: message, verticalPosition: verticalPosition, timeout: timeout, })
    } else {
      this.toast.danger({ content: message, verticalPosition: verticalPosition, timeout: timeout, })
    }
  }
}
