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

export const NodeStatus = {
  READY: 'READY',
  FINISHED: 'FINISHED',
  ABORT: 'ABORT',
  ERROR: 'ERROR',
  PENDING: 'PENDING',
  RUNNING: 'RUNNING',
};

export function getNodeIconByStatus(task) {
  let i = parseInt(task);
  if (i % 6 === 0) {
    return 'fa fa-circle-o';
  } else if (i % 6 === 1) {
    return 'fa fa-circle';
  } else if (i % 6 === 2) {
    return 'fa fa-circle';
  } else if (i % 6 === 3) {
    return 'fa fa-circle';
  } else if (i % 6 === 4) {
    return 'fa fa-circle';
  } else if (i % 6 === 5) {
    return 'fa fa-spinner';
  }
}

export function getNodeColorByStatus(task) {
  let i = parseInt(task);
  if (i % 6 === 0) {
    return 'green';
  } else if (i % 6 === 1) {
    return 'green';
  } else if (i % 6 === 2) {
    return 'orange';
  } else if (i % 6 === 3) {
    return 'red';
  } else if (i % 6 === 4) {
    return 'gray';
  } else if (i % 6 === 5) {
    return 'blue';
  }
}
