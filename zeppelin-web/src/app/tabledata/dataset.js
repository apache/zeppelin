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

/**
 * The abstract dataset rapresentation
 */
class Dataset {
  /**
   * Load the paragraph result, every Dataset implementation must override this method
   * where is contained the business rules to convert the paragraphResult object to the related dataset type
   */
  loadParagraphResult(paragraphResult) {
    // override this
  }
}

/**
 * Dataset types
 */
const DatasetType = Object.freeze({
  NETWORK: 'NETWORK',
  TABLE: 'TABLE'
})

export {Dataset, DatasetType}
