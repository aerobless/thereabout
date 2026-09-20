import {DestroyRef, Directive, ElementRef, effect, inject, input, signal} from '@angular/core';
import {NavigationStart, Router} from '@angular/router';
import {RefreshCoordinator} from './refresh-coordinator';

@Directive({selector: '[appPullToRefresh]', exportAs: 'pullToRefresh'})
export class PullToRefreshDirective {
  readonly appPullToRefresh = input(true);
  readonly distance = signal(0);
  readonly refresh = inject(RefreshCoordinator);
  readonly threshold = 80;
  private start: {x: number; y: number} | null = null;
  private claimed = false;
  private readonly host = inject(ElementRef<HTMLElement>).nativeElement;
  private readonly mobile = window.matchMedia?.('(max-width: 768px)');

  constructor() {
    const sync = () => {
      this.reset();
      document.documentElement.classList.toggle('app-pull-refresh', !!this.mobile?.matches && this.appPullToRefresh());
    };
    effect(sync);
    this.mobile?.addEventListener('change', sync);
    const events = inject(Router, {optional: true})?.events.subscribe(event => {
      if (event instanceof NavigationStart) this.reset();
    });
    this.host.addEventListener('touchstart', this.onStart, {passive: true});
    this.host.addEventListener('touchmove', this.onMove, {passive: false});
    this.host.addEventListener('touchend', this.onEnd);
    this.host.addEventListener('touchcancel', this.onCancel);
    inject(DestroyRef).onDestroy(() => {
      this.host.removeEventListener('touchstart', this.onStart);
      this.host.removeEventListener('touchmove', this.onMove);
      this.host.removeEventListener('touchend', this.onEnd);
      this.host.removeEventListener('touchcancel', this.onCancel);
      this.mobile?.removeEventListener('change', sync);
      events?.unsubscribe();
      document.documentElement.classList.remove('app-pull-refresh');
    });
  }

  private reset() { this.start = null; this.claimed = false; this.distance.set(0); }

  private excluded(target: EventTarget | null) {
    if (!(target instanceof Element)) return true;
    if (document.querySelector('[role="dialog"], .p-dialog-mask, .p-datepicker-panel, .mobile-more-menu')) return true;
    if (target.closest('google-map, input, textarea, select, button, a, [contenteditable], nav, [role="slider"], [data-no-pull-refresh]')) return true;
    for (let element: Element | null = target; element && element !== this.host; element = element.parentElement) {
      const style = getComputedStyle(element);
      if (/(auto|scroll)/.test(style.overflowY) && element.scrollHeight > element.clientHeight) return true;
      if (/(auto|scroll)/.test(style.overflowX) && element.scrollWidth > element.clientWidth) return true;
    }
    return false;
  }

  private onStart = (event: TouchEvent) => {
    this.reset();
    if (!this.mobile?.matches || !this.appPullToRefresh() || event.touches.length !== 1 ||
      window.scrollY > 0 || !this.refresh.canRefresh() || this.excluded(event.target)) return;
    this.start = {x: event.touches[0].clientX, y: event.touches[0].clientY};
  };

  private onMove = (event: TouchEvent) => {
    if (!this.start) return;
    if (event.touches.length !== 1) { this.reset(); return; }
    const dx = Math.abs(event.touches[0].clientX - this.start.x);
    const dy = event.touches[0].clientY - this.start.y;
    if (!this.claimed && (dy < -8 || dx > Math.max(8, dy))) { this.reset(); return; }
    if (dy <= 0) { this.distance.set(0); return; }
    if (!this.claimed && dy < 8) return;
    if (!event.cancelable || window.scrollY > 0) { this.reset(); return; }
    this.claimed = true;
    event.preventDefault();
    this.distance.set(Math.min(120, dy));
  };

  private onEnd = () => {
    const trigger = this.distance() >= this.threshold;
    this.reset();
    if (trigger) void this.refresh.refresh();
  };
  private onCancel = () => this.reset();
}
