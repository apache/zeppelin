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

import { Directive, ElementRef, Injectable, Input, OnChanges, OnDestroy } from '@angular/core';
import { BehaviorSubject, Subject } from 'rxjs';

import type { AssistantSlot } from '@zeppelin/sdk';

@Injectable()
export class AssistantSlots {
  readonly slots = new BehaviorSubject<AssistantSlot[]>([]);
  readonly panelOpen = new BehaviorSubject(false);
  readonly panelCloseRequests = new Subject<void>();

  setPanelOpen(open: boolean): void {
    if (this.panelOpen.value !== open) this.panelOpen.next(open);
  }

  requestPanelClose(): void {
    if (this.panelOpen.value) this.panelCloseRequests.next();
  }

  set(slot: AssistantSlot): void {
    const others = this.slots.value.filter(item => item.element !== slot.element);
    this.slots.next([...others, slot]);
  }

  remove(element: HTMLElement): void {
    this.slots.next(this.slots.value.filter(item => item.element !== element));
  }
}

@Directive({ selector: '[zeppelin-assistant-slot]', standalone: false })
export class AssistantSlotDirective implements OnChanges, OnDestroy {
  @Input('zeppelin-assistant-slot') assistantSlot!: AssistantSlot['kind'];

  constructor(
    private host: ElementRef<HTMLElement>,
    private registry: AssistantSlots
  ) {}

  ngOnChanges(): void {
    this.registry.set({ element: this.host.nativeElement, kind: this.assistantSlot });
  }

  ngOnDestroy(): void {
    this.registry.remove(this.host.nativeElement);
  }
}
