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

import * as fs from 'fs';
import * as path from 'path';
import { promisify } from 'util';

const access = promisify(fs.access);
const readFile = promisify(fs.readFile);

export interface TestCredentials {
  username: string;
  password: string;
  roles?: string[];
}

export class LoginTestUtil {
  private static readonly SHIRO_CONFIG_PATH = path.join(process.cwd(), '..', 'conf', 'shiro.ini');

  private static _testCredentials: Record<string, TestCredentials> | null = null;
  private static _isShiroEnabled: boolean | null = null;

  static resetCache(): void {
    this._testCredentials = null;
    this._isShiroEnabled = null;
  }

  static async isShiroEnabled(): Promise<boolean> {
    if (this._isShiroEnabled !== null) {
      return this._isShiroEnabled;
    }

    try {
      await access(this.SHIRO_CONFIG_PATH);
      this._isShiroEnabled = true;
    } catch (error) {
      this._isShiroEnabled = false;
    }

    return this._isShiroEnabled;
  }

  static async getTestCredentials(): Promise<Record<string, TestCredentials>> {
    if (!(await this.isShiroEnabled())) {
      return {};
    }

    if (this._testCredentials !== null) {
      return this._testCredentials;
    }

    try {
      const content = await readFile(this.SHIRO_CONFIG_PATH, 'utf-8');
      const users: Record<string, TestCredentials> = {};

      this._parseUsersSection(content, users);
      this._addTestCredentials(users);

      this._testCredentials = users;
      return users;
    } catch (error) {
      console.error('Failed to parse shiro.ini:', error);
      return {};
    }
  }

  private static _parseUsersSection(content: string, users: Record<string, TestCredentials>): void {
    const lines = content.split('\n');
    let inUsersSection = false;

    for (const line of lines) {
      const trimmedLine = line.trim();

      if (trimmedLine === '[users]') {
        inUsersSection = true;
        continue;
      }

      if (trimmedLine.startsWith('[') && trimmedLine !== '[users]') {
        inUsersSection = false;
        continue;
      }

      if (inUsersSection && trimmedLine && !trimmedLine.startsWith('#')) {
        this._parseUserLine(trimmedLine, users);
      }
    }
  }

  private static _parseUserLine(line: string, users: Record<string, TestCredentials>): void {
    const [userPart, ...roleParts] = line.split('=');
    if (!userPart || roleParts.length === 0) return;

    const username = userPart.trim();
    const rightSide = roleParts.join('=').trim();
    const parts = rightSide.split(',').map(p => p.trim());

    if (parts.length > 0) {
      const password = parts[0];
      const roles = parts.slice(1);

      users[username] = {
        username,
        password,
        roles
      };
    }
  }

  private static _addTestCredentials(users: Record<string, TestCredentials>): void {
    users.INVALID_USER = { username: 'wronguser', password: 'wrongpass' };
    users.EMPTY_CREDENTIALS = { username: '', password: '' };
  }
}
