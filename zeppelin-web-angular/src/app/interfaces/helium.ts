/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { HeliumPackageType } from '@zeppelin/helium';

export type HeliumType = 'VISUALIZATION';

interface HeliumPackage {
  name: string;
  artifact: string;
  description: string;
  icon: string;
  type: HeliumPackageType;
  license: string;
  published: string;
}

export interface HeliumPackageSearchResult {
  registry: string;
  pkg: HeliumPackage;
  enabled: boolean;
}

export interface HeliumBundle {
  id: string;
  name: string;
  icon: string;
  type: HeliumType;
  // tslint:disable-next-line:no-any
  class: any;
}
