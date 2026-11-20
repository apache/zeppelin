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

import type { APIRequestContext } from '@playwright/test';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { checkpoint } from '../e2e/models/notebook-core-read-page.util';

vi.mock('../e2e/models/base-page', () => ({ E2E_TEST_FOLDER: 'checkpoint-unit' }));

const response = (body: unknown, status = 200) => ({
  ok: () => status >= 200 && status < 300,
  status: () => status,
  json: async () => ({ body })
});

const requestFor = (supported: unknown, revisionId: unknown = 'revision-1', status = 200) => {
  const get = vi.fn().mockResolvedValue(response({ isRevisionSupported: supported }, status));
  const post = vi.fn().mockResolvedValue(response(revisionId));
  return { request: { get, post } as unknown as APIRequestContext, get, post };
};

afterEach(() => vi.unstubAllEnvs());

describe('Notebook Core checkpoint requirements', () => {
  it('allows unsupported storage to skip outside the required revision run', async () => {
    vi.stubEnv('ZEPPELIN_E2E_REQUIRE_REVISION', undefined);
    const { request, post } = requestFor(false);

    await expect(checkpoint(request, 'note-1', 'checkpoint')).resolves.toBeNull();
    expect(post).not.toHaveBeenCalled();
  });

  it('fails instead of skipping when the revision run requires versioned storage', async () => {
    vi.stubEnv('ZEPPELIN_E2E_REQUIRE_REVISION', 'true');
    const { request, post } = requestFor(false);

    await expect(checkpoint(request, 'note-1', 'checkpoint')).rejects.toThrow();
    expect(post).not.toHaveBeenCalled();
  });

  it('rejects a failed capabilities request before attempting a checkpoint', async () => {
    const { request, post } = requestFor(false, undefined, 503);

    await expect(checkpoint(request, 'note-1', 'checkpoint')).rejects.toThrow();
    expect(post).not.toHaveBeenCalled();
  });

  it.each([undefined, null, 'false', 0])('rejects malformed revision capability %s', async supported => {
    const { request, post } = requestFor(supported);

    await expect(checkpoint(request, 'note-1', 'checkpoint')).rejects.toThrow();
    expect(post).not.toHaveBeenCalled();
  });

  it('creates and returns the checkpoint when versioned storage is available', async () => {
    vi.stubEnv('ZEPPELIN_E2E_REQUIRE_REVISION', 'true');
    const { request, get, post } = requestFor(true);

    await expect(checkpoint(request, 'note-1', 'checkpoint')).resolves.toBe('revision-1');
    expect(get).toHaveBeenCalledWith('/api/notebook/capabilities');
    expect(post).toHaveBeenCalledWith('/api/notebook/note-1/revision', {
      data: { commitMessage: 'checkpoint' }
    });
  });

  it('rejects a failed checkpoint request', async () => {
    const { request, post } = requestFor(true);
    post.mockResolvedValue(response('revision-1', 500));

    await expect(checkpoint(request, 'note-1', 'checkpoint')).rejects.toThrow();
  });

  it.each([undefined, null, '', '   ', 42])('rejects invalid checkpoint revision id %s', async revisionId => {
    const { request, post } = requestFor(true);
    post.mockResolvedValue(response(revisionId));

    await expect(checkpoint(request, 'note-1', 'checkpoint')).rejects.toThrow();
  });
});
