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

import { Locator } from '@playwright/test';

export class JobCard {
  readonly container: Locator;
  readonly noteIcon: Locator;
  readonly noteName: Locator;
  readonly interpreter: Locator;
  readonly relativeTime: Locator;
  readonly statusText: Locator;
  readonly progressPercentage: Locator;
  readonly controlButton: Locator;
  readonly paragraphStatusBadges: Locator;
  readonly progressBar: Locator;

  constructor(container: Locator) {
    this.container = container;
    this.noteIcon = container.locator('.note-icon');
    this.noteName = container.locator('a[routerLink]').first();
    this.interpreter = container.locator('.interpreter');
    this.relativeTime = container.locator('.right-tools small');
    this.statusText = container.locator('.right-tools > span').nth(0);
    this.progressPercentage = container.locator('.right-tools > span').nth(1);
    this.controlButton = container.locator('.job-control-btn');
    this.paragraphStatusBadges = container.locator('zeppelin-job-manager-job-status');
    this.progressBar = container.locator('nz-progress');
  }

  async getNoteName(): Promise<string | null> {
    return await this.noteName.textContent();
  }

  async getInterpreter(): Promise<string | null> {
    return await this.interpreter.textContent();
  }

  async getStatus(): Promise<string | null> {
    return await this.statusText.textContent();
  }

  async isRunning(): Promise<boolean> {
    const status = await this.getStatus();
    return status === 'RUNNING';
  }

  async getProgress(): Promise<string | null> {
    if (await this.isRunning()) {
      return await this.progressPercentage.textContent();
    }
    return null;
  }

  async clickControlButton(): Promise<void> {
    await this.controlButton.click();
  }

  async getControlButtonTooltip(): Promise<string | null> {
    return await this.controlButton.getAttribute('nz-tooltip');
  }

  async clickNoteName(): Promise<void> {
    await this.noteName.click();
  }

  async getParagraphCount(): Promise<number> {
    return await this.paragraphStatusBadges.count();
  }

  async isProgressBarVisible(): Promise<boolean> {
    try {
      return await this.progressBar.isVisible({ timeout: 1000 });
    } catch {
      return false;
    }
  }

  getParagraphBadge(index: number): Locator {
    return this.paragraphStatusBadges.nth(index);
  }

  async getParagraphStatuses(): Promise<string[]> {
    const statuses: string[] = [];
    const count = await this.getParagraphCount();
    for (let i = 0; i < count; i++) {
      const badge = this.getParagraphBadge(i);
      const status = await badge.textContent();
      if (status) {
        statuses.push(status.trim());
      }
    }
    return statuses;
  }

  async getNoteIconType(): Promise<string | null> {
    return await this.noteIcon.getAttribute('nzType');
  }

  async getRelativeTime(): Promise<string | null> {
    return await this.relativeTime.textContent();
  }

  async hasStatus(status: string): Promise<boolean> {
    const currentStatus = await this.getStatus();
    return currentStatus === status;
  }
}
