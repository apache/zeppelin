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

import type { AssistantParagraphRef } from '@zeppelin/sdk';
import type { AssistantContentRenderer } from '@/entities/assistant/ui/answerView';
import { MarkdownAnswer } from '@/entities/assistant/ui/MarkdownAnswer';

export const createNotebookAnswerRenderer = (
  paragraphs: readonly AssistantParagraphRef[],
  onOpenParagraph: (paragraphId: string) => void
): AssistantContentRenderer => {
  const references = new Map(
    paragraphs.map((paragraph, index) => {
      const text = `Paragraph ${index + 1}`;
      return [paragraph.id, { text, name: paragraph.title?.trim() || text }] as const;
    })
  );
  const getParagraphReference = (paragraphId: string) => references.get(paragraphId);
  const openParagraph = (paragraphId: string) => onOpenParagraph(paragraphId);

  const renderAnswer: AssistantContentRenderer = content => (
    <MarkdownAnswer content={content} describeParagraph={getParagraphReference} onOpenParagraph={openParagraph} />
  );
  return renderAnswer;
};
