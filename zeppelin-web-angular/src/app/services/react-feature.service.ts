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

import { Injectable } from '@angular/core';
import { parseBooleanFlag } from './query-flag.util';

export type ReactSurface = 'publishedParagraph' | 'paragraphFooter';

interface ReactSurfaceConfig {
  queryParam: string;
  defaultEnabled: boolean;
}

const SURFACES: Record<ReactSurface, ReactSurfaceConfig> = {
  publishedParagraph: {
    queryParam: 'react',
    defaultEnabled: false
  },
  paragraphFooter: {
    queryParam: 'reactFooter',
    defaultEnabled: false
  }
};

/**
 * Satisfied by Angular's `ParamMap` and by any `Map`.
 * Taking the source rather than an already-read value keeps the query-param name in SURFACES only,
 * so renaming it cannot desync a call site.
 */
export interface FlagSource {
  get(name: string): string | null | undefined;
}

@Injectable({ providedIn: 'root' })
export class ReactFeatureService {
  isEnabled(surface: ReactSurface, source?: FlagSource | null): boolean {
    const config = SURFACES[surface];

    const fromQuery = parseBooleanFlag(source?.get(config.queryParam));
    if (fromQuery !== null) {
      return fromQuery;
    }

    return config.defaultEnabled;
  }
}
