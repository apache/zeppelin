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

// @see https://playwright.dev/docs/test-reporters#custom-reporters
import { FullResult, Reporter, TestCase, TestResult } from '@playwright/test/reporter';
import { promises as fs } from 'fs';
import { flatMap, sortBy } from 'lodash';
import { join } from 'path';
import { scanDirectory, Results } from 'scandirectory';
import cfg from './reporter.coverage.config';

const TEST_STATUS = {
  PASSED: 'passed',
  SKIPPED: 'skipped',
  FAILED: 'failed'
} as const;

interface ResultType {
  path: string;
  total: number;
  success: number;
  failed: number;
  skipped: number;
  rate: number;
}
type ResultsType = ResultType[];
type TestStatusType = typeof TEST_STATUS[keyof typeof TEST_STATUS];
interface TestedPathType {
  success: number;
  skipped: number;
  failed: number;
}

const OUTPUT_FILE_NAME = 'coverage.log';

class CoverageReporter implements Reporter {
  testedPaths = new Map<string, TestedPathType>();
  testedIds = new Map<string, TestStatusType>();
  targetPaths: string[] = [];

  async onBegin() {
    console.log('Coverage reporter starting...');
    console.log('Root path:', cfg.rootPath);

    const results = await scanDirectory({
      directory: cfg.rootPath
    });

    this.targetPaths = this.processScannedFiles(results);
    console.log('Target paths:', this.targetPaths.length);
  }

  onTestEnd(test: TestCase, result: TestResult) {
    const status =
      result.status === TEST_STATUS.PASSED || result.status === TEST_STATUS.SKIPPED
        ? result.status
        : TEST_STATUS.FAILED;
    const pages = this.extractPageAnnotations(test);
    const prevTestStatus = this.testedIds.get(test.id);

    pages.forEach(page => {
      this.updateTestedPath(page, status, prevTestStatus);
    });

    this.testedIds.set(test.id, status);
  }

  async onEnd(result: FullResult) {
    const results = this.getResults();

    console.log(this.formatTable(results));
    console.log(this.getTestedPagesResult(results));
    console.log(this.getTestCasesResult(results));
    console.log(`Finished the run: ${result.status}`);

    await this.saveResultsToFile(results, result.status);
  }

  processScannedFiles(results: Results): string[] {
    return Object.keys(results)
      .filter(key => !results[key].directory)
      .map(key => this.normalizeFilePath(key, results))
      .filter(key => key !== '.')
      .filter(key => this.shouldIncludeFile(key));
  }

  normalizeFilePath(key: string, results: Results): string {
    if (/index\.tsx?$/.test(key)) {
      return results[key].parent?.relativePath || '.';
    }
    return key.replace(/\.tsx?$/, '');
  }

  shouldIncludeFile(key: string): boolean {
    if (cfg.testMatch?.length) {
      const matchesTest = cfg.testMatch.some(rule => (rule instanceof RegExp ? rule.test(key) : rule === key));
      if (!matchesTest) {
        return false;
      }
    }

    if (cfg.excludes?.length) {
      const isExcluded = cfg.excludes.some(rule => (rule instanceof RegExp ? rule.test(key) : rule === key));
      if (isExcluded) {
        return false;
      }
    }

    return true;
  }

  extractPageAnnotations(test: TestCase): string[] {
    const annotations = test.annotations
      .filter(({ type }) => type === 'page')
      .map(({ description }) => description)
      .filter((desc): desc is string => desc !== undefined);

    return Array.from(new Set(annotations));
  }

  updateTestedPath(page: string, status: TestStatusType, prevStatus?: TestStatusType) {
    if (this.testedPaths.has(page)) {
      const currentTest = this.testedPaths.get(page)!;
      const newTest = { ...currentTest };
      this.decrementPreviousStatus(newTest, prevStatus);
      this.incrementCurrentStatus(newTest, status);
      this.testedPaths.set(page, newTest);
      return;
    }
    this.testedPaths.set(page, {
      success: status === TEST_STATUS.PASSED ? 1 : 0,
      failed: status === TEST_STATUS.FAILED ? 1 : 0,
      skipped: status === TEST_STATUS.SKIPPED ? 1 : 0
    });
  }

  decrementPreviousStatus(draftState: TestedPathType, prevStatus?: TestStatusType) {
    if (!prevStatus) {
      return;
    }

    if (prevStatus === TEST_STATUS.PASSED && draftState.success > 0) {
      draftState.success -= 1;
    } else if (prevStatus === TEST_STATUS.SKIPPED && draftState.skipped > 0) {
      draftState.skipped -= 1;
    } else if (prevStatus === TEST_STATUS.FAILED && draftState.failed > 0) {
      draftState.failed -= 1;
    }
  }

  incrementCurrentStatus(draftState: TestedPathType, status: TestStatusType) {
    if (status === TEST_STATUS.PASSED) {
      draftState.success += 1;
    } else if (status === TEST_STATUS.SKIPPED) {
      draftState.skipped += 1;
    } else if (status === TEST_STATUS.FAILED) {
      draftState.failed += 1;
    }
  }

  getResults(): ResultsType {
    const testedPaths = Array.from(this.testedPaths.keys());
    const results = flatMap(this.targetPaths, path => this.processTargetPath(path, testedPaths));

    return sortBy(results, ['rate', 'total', 'path']).reverse();
  }

  processTargetPath(targetPath: string, testedPaths: string[]): ResultsType {
    const matchingPaths = this.findMatchingPaths(targetPath, testedPaths);

    if (matchingPaths.length > 0) {
      return matchingPaths.map(path => this.createResultEntry(path));
    }

    return [{ path: targetPath, total: 0, success: 0, failed: 0, skipped: 0, rate: 0 }];
  }

  findMatchingPaths(targetPath: string, testedPaths: string[]): string[] {
    const regExp = new RegExp(`^${targetPath.replace(/\[.*?\]/g, '[^/]+?')}$`);

    return testedPaths.filter(key => {
      if (!this.targetPaths.includes(key)) {
        return regExp.test(key);
      }
      return targetPath === key;
    });
  }

  createResultEntry(path: string): ResultType {
    const testData = this.testedPaths.get(path);
    if (!testData) {
      return { path, total: 0, success: 0, failed: 0, skipped: 0, rate: 0 };
    }

    const { success, skipped, failed } = testData;
    const total = success + failed + skipped;
    const rate = this.toRate(success, total - skipped);

    return { path, total, success, failed, skipped, rate };
  }

  getTestedPagesResult(results: ResultsType) {
    const testedList = results.filter(item => !!item.total);
    const failedList = testedList.filter(item => !!item.failed);
    const skippedList = testedList.filter(item => item.skipped === item.total);

    const failed = failedList.length;
    const tested = testedList.length;
    const skipped = skippedList.length;
    const success = tested - failed - skipped;
    const testedRate = this.toRate(testedList.length, results.length).toFixed(2);

    return `Tested pages: ${tested}/${results.length} (${testedRate}%) (${this.toRate(success, tested).toFixed(
      2
    )}%, success: ${success}, failed: ${failed}, skipped: ${skipped})`;
  }

  getTestCasesResult(results: ResultsType) {
    const stats = results.reduce(
      (acc, item) => ({
        total: acc.total + item.total,
        failed: acc.failed + item.failed,
        skipped: acc.skipped + item.skipped,
        success: acc.success + item.success
      }),
      { total: 0, failed: 0, skipped: 0, success: 0 }
    );
    const rate = this.toRate(stats.success, stats.total - stats.skipped).toFixed(2);

    return `Test cases: ${stats.total} (${rate}%, success: ${stats.success}, failed: ${stats.failed}, skipped: ${stats.skipped})`;
  }

  getTableData(results: ResultsType): Array<Array<string | number>> {
    const header = ['No.', 'Path', 'Test cases', 'Successes', 'Failures', 'Skipped', 'Success rate'];
    const rows = results.map((item, index) => {
      const { path, total, success, failed, skipped, rate } = item;
      return [index + 1, path, total, success, failed, skipped, `${rate.toFixed(2)}%`];
    });

    return [header, ...rows];
  }

  formatTable(results: ResultsType): string {
    const tableData = this.getTableData(results);
    if (tableData.length === 0) {
      return '';
    }

    const colWidths = tableData[0].map((_, colIndex) => {
      let maxWidth = 0;
      for (const row of tableData) {
        const width = String(row[colIndex]).length;
        if (width > maxWidth) {
          maxWidth = width;
        }
      }
      return maxWidth;
    });

    const lines: string[] = [];

    const header = tableData[0].map((cell, i) => String(cell).padEnd(colWidths[i])).join(' │ ');
    lines.push(header);

    const separator = colWidths.map(width => '─'.repeat(width)).join('─┼─');
    lines.push(separator);

    for (let i = 1; i < tableData.length; i++) {
      const row = tableData[i].map((cell, j) => String(cell).padEnd(colWidths[j])).join(' │ ');
      lines.push(row);
    }

    return lines.join('\n');
  }

  async saveResultsToFile(results: ResultsType, status: string) {
    const contents = [
      this.formatTable(results),
      this.getTestedPagesResult(results),
      this.getTestCasesResult(results),
      `Finished the run: ${status}`
    ].join('\n');

    try {
      await fs.mkdir(cfg.outputPath, { recursive: true });
      await fs.writeFile(join(cfg.outputPath, OUTPUT_FILE_NAME), contents, 'utf8');
      console.log('The file has been saved!');
    } catch (e) {
      console.error('Error saving coverage report:', e);
    }
  }

  toRate(count: number, total: number) {
    return total ? (count / total) * 100 : 0;
  }
}

export default CoverageReporter;
