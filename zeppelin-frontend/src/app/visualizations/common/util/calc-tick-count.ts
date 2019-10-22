export const DEFAULT_TICK_COUNT = 16;

export function calcTickCount(el: HTMLElement) {
  if (el && el.getBoundingClientRect) {
    const tickCount = Math.round(el.getBoundingClientRect().width / 60);
    return Number.isNaN(tickCount) ? DEFAULT_TICK_COUNT : tickCount;
  } else {
    return DEFAULT_TICK_COUNT;
  }
}
