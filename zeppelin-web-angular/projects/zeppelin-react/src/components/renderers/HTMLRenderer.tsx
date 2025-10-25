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

import { useEffect, useRef } from 'react';
// Added styles for tables rendered as HTML from app/pages/workspace/share/result/result.component.ts
import './HTMLRenderer.css';

interface HTMLRendererProps {
  html: string;
}

export const HTMLRenderer = ({ html }: HTMLRendererProps) => {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (container) {
      // For security reasons, React's dangerouslySetInnerHTML does not execute script tags.
      // To render HTML containing libraries like BokehJS, we must manually add script tags
      // to the DOM to execute them.
      container.innerHTML = html;

      const scripts = Array.from(container.querySelectorAll('script'));

      scripts.forEach(script => {
        const newScript = document.createElement('script');

        for (const attr of Array.from(script.attributes)) {
          newScript.setAttribute(attr.name, attr.value);
        }

        newScript.textContent = script.textContent;
        newScript.async = false;

        script.parentNode?.replaceChild(newScript, script);
      });

      return () => {
        container.innerHTML = '';
      };
    }
  }, [html]);

  return <div ref={containerRef} className="inner-html" />;
};