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

import { Alert } from 'antd';
import {
  HTMLRenderer,
  TextRenderer,
  ImageRenderer,
  TableVisualization
} from '@/components';
import { DatasetType } from '@/types';
import type { ParagraphResult, ParagraphConfig } from '@/types';
import { checkAndReplaceCarriageReturn } from '@/utils';

interface SingleResultRendererProps {
  result: ParagraphResult;
  config?: ParagraphConfig;
}

export const SingleResultRenderer = ({ result, config }: SingleResultRendererProps) => {
  switch (result.type) {
    case DatasetType.TABLE:
      return <TableVisualization result={result} config={config} />;
    case DatasetType.HTML:
      return <HTMLRenderer html={result.data} />;
    case DatasetType.TEXT:
      return <TextRenderer text={checkAndReplaceCarriageReturn(result.data)} />;
    case DatasetType.IMG:
      return <ImageRenderer imageData={result.data} />;
    case DatasetType.ANGULAR:
      return (
        <Alert
          message="Angular Component"
          description="Angular components are not supported in React environment"
          type="warning"
          showIcon
        />
      );
    default:
      return null;
  }
};
