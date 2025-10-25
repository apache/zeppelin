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

import { createRoot } from 'react-dom/client';
import { ConfigProvider } from 'antd';
import { Empty } from '@/components';
import { SingleResultRenderer } from '@/templates';
import type { ParagraphResult, ParagraphConfig } from '@/types';

export interface PublishedParagraphProps {
  paragraphId: string;
  results?: ParagraphResult[];
  config?: ParagraphConfig;
}

const PublishedParagraph = ({ results, config }: PublishedParagraphProps) => {
  if (!results || results.length === 0) {
    return <Empty />;
  }

  return (
    <ConfigProvider
      theme={{
        token: {
          fontFamily: "'Lucida Console', Consolas, Monaco, 'Andale Mono', 'Ubuntu Mono', monospace"
        }
      }}
    >
      <div>
        {results.map((result: ParagraphResult, index: number) => (
          <div key={index}>
            <SingleResultRenderer result={result} config={config} />
          </div>
        ))}
      </div>
    </ConfigProvider>
  );
};

export const mount = (element: HTMLElement, props?: PublishedParagraphProps) => {
  if (!element) {
    throw new Error('Mount element is required');
  }

  const root = createRoot(element);

  root.render(
    <PublishedParagraph
      paragraphId={props?.paragraphId || 'demo-paragraph'}
      results={props?.results}
      config={props?.config}
    />
  );

  return () => {
    root.unmount();
  };
};

export default PublishedParagraph;