import { ParagraphIResultsMsgItem } from '@zeppelin/sdk';

export abstract class DataSet {
  abstract loadParagraphResult(paragraphResult: ParagraphIResultsMsgItem): void;
}
