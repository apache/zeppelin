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

import { createRoot, Root } from 'react-dom/client';
import { format, formatDistanceStrict, formatDistanceToNow } from 'date-fns';
import { ReactErrorBoundary } from './ReactErrorBoundary';
import './ParagraphFooter.css';

export interface ParagraphFooterProps {
  dateStarted?: string;
  dateFinished?: string;
  dateUpdated?: string;
  showExecutionTime?: boolean;
  showElapsedTime?: boolean;
  user?: string;
  onError?: (error: unknown) => void;
}

const isOutdated = (dateUpdated?: string, dateStarted?: string): boolean => {
  return dateUpdated !== undefined && dateStarted !== undefined && Date.parse(dateUpdated) > Date.parse(dateStarted);
};

const computeExecutionTime = (props: ParagraphFooterProps): string => {
  const { dateStarted, dateFinished, user, dateUpdated } = props;
  if (dateFinished === undefined || dateStarted === undefined) {
    return '';
  }
  const timeMs = Date.parse(dateFinished) - Date.parse(dateStarted);
  if (isNaN(timeMs) || timeMs < 0) {
    return isOutdated(dateUpdated, dateStarted) ? 'outdated' : '';
  }

  const durationFormat = formatDistanceStrict(new Date(dateStarted), new Date(dateFinished));
  const endFormat = format(new Date(dateFinished), 'MMMM dd yyyy, h:mm:ss a');
  const userLabel = user === undefined || user === null ? 'anonymous' : user;
  let desc = `Took ${durationFormat}. Last updated by ${userLabel} at ${endFormat}.`;
  if (isOutdated(dateUpdated, dateStarted)) {
    desc += ' (outdated)';
  }
  return desc;
};

const computeElapsedTime = (dateStarted?: string): string => {
  // A running paragraph may not have a dateStarted yet (e.g. queued/pending on the
  // interpreter). Fall back to a neutral label instead of measuring from "now", which
  // would render a misleading "Started less than a minute ago." message.
  if (!dateStarted) {
    return 'Running…';
  }
  return `Started ${formatDistanceToNow(new Date(dateStarted))} ago.`;
};

export const ParagraphFooter = (props: ParagraphFooterProps) => {
  const { showExecutionTime, showElapsedTime } = props;
  const executionTime = computeExecutionTime(props);
  const elapsedTime = computeElapsedTime(props.dateStarted);

  return (
    <div className="zeppelin-react-paragraph-footer" data-testid="react-paragraph-footer-content">
      {showExecutionTime && <div className="execution-time">{executionTime}</div>}
      {showElapsedTime && <div className="elapsed-time">{elapsedTime}</div>}
    </div>
  );
};

export interface ParagraphFooterMountHandle {
  update: (props: ParagraphFooterProps) => void;
  unmount: () => void;
}

export const mount = (element: HTMLElement, initialProps: ParagraphFooterProps): ParagraphFooterMountHandle => {
  if (!element) {
    throw new Error('Mount element is required');
  }

  const root: Root = createRoot(element);

  const renderWith = (props: ParagraphFooterProps) => {
    root.render(
      <ReactErrorBoundary onError={props.onError}>
        <ParagraphFooter {...props} />
      </ReactErrorBoundary>
    );
  };

  renderWith(initialProps);

  return {
    update: (newProps: ParagraphFooterProps) => {
      renderWith(newProps);
    },
    unmount: () => {
      root.unmount();
    }
  };
};
