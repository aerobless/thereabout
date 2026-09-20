import {Component, CUSTOM_ELEMENTS_SCHEMA} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {vi} from 'vitest';
import {PullToRefreshDirective} from './pull-to-refresh.directive';
import {RefreshCoordinator, RefreshHandle} from './refresh-coordinator';

@Component({schemas: [CUSTOM_ELEMENTS_SCHEMA], imports: [PullToRefreshDirective], template: `<div [appPullToRefresh]="enabled" #pull="pullToRefresh"><p>Content</p><google-map></google-map><input /><div class="nested">Scrollable</div><span>{{pull.distance() >= pull.threshold ? 'Release' : 'Pull'}}</span></div>`})
class Host { enabled = true; }

describe('PullToRefreshDirective', () => {
  let fixture: ComponentFixture<Host>;
  let refresh: ReturnType<typeof vi.spyOn>;
  let mobile = true;
  let root: HTMLElement;
  function touch(type: string, x: number, y: number, target: Element = root.querySelector('p')!, count = 1) {
    const event = new Event(type, {bubbles: true, cancelable: true});
    Object.defineProperty(event, 'touches', {value: Array.from({length: count}, () => ({clientX: x, clientY: y}))});
    target.dispatchEvent(event); fixture.detectChanges(); return event;
  }
  beforeEach(async () => {
    mobile = true;
    vi.stubGlobal('matchMedia', () => ({get matches() {return mobile;}, addEventListener() {}, removeEventListener() {}}));
    await TestBed.configureTestingModule({imports: [Host]}).compileComponents();
    const coordinator = TestBed.inject(RefreshCoordinator);
    coordinator.register(new RefreshHandle(() => {}, () => false));
    refresh = vi.spyOn(coordinator, 'refresh').mockResolvedValue(undefined);
    fixture = TestBed.createComponent(Host); fixture.detectChanges();
    root = fixture.nativeElement;
  });
  afterEach(() => { fixture.destroy(); vi.unstubAllGlobals(); });

  it('refreshes only on release after the threshold and prevents native scrolling only during a pull', () => {
    touch('touchstart', 0, 0);
    expect(touch('touchmove', 0, 50).defaultPrevented).toBe(true);
    touch('touchend', 0, 50); expect(refresh).not.toHaveBeenCalled();
    touch('touchstart', 0, 0); touch('touchmove', 0, 90);
    expect(root.textContent).toContain('Release'); expect(refresh).not.toHaveBeenCalled();
    touch('touchend', 0, 90); expect(refresh).toHaveBeenCalledTimes(1);
  });

  it('ignores horizontal, upward, multi-touch, cancelled and below-top gestures', () => {
    for (const [x, y, count] of [[100, 10, 1], [0, -20, 1], [0, 90, 2]]) {
      touch('touchstart', 0, 0); touch('touchmove', x, y, undefined, count); touch('touchend', 0, 100);
    }
    touch('touchstart', 0, 0); touch('touchmove', 0, 100); touch('touchcancel', 0, 100); touch('touchend', 0, 100);
    vi.stubGlobal('scrollY', 50);
    touch('touchstart', 0, 0); touch('touchmove', 0, 100); touch('touchend', 0, 100);
    expect(refresh).not.toHaveBeenCalled();
  });

  it('excludes maps, inputs, nested scrolling and open dialogs', () => {
    const nested = root.querySelector('.nested') as HTMLElement;
    nested.style.overflowY = 'auto'; Object.defineProperty(nested, 'scrollHeight', {value: 100});
    for (const target of [root.querySelector('google-map')!, root.querySelector('input')!, nested]) {
      touch('touchstart', 0, 0, target); touch('touchmove', 0, 100, target); touch('touchend', 0, 100, target);
    }
    const dialog = document.createElement('div'); dialog.setAttribute('role', 'dialog'); document.body.append(dialog);
    touch('touchstart', 0, 0); touch('touchmove', 0, 100); touch('touchend', 0, 100); dialog.remove();
    expect(refresh).not.toHaveBeenCalled();
  });

  it('disables desktop and embedded gestures and removes root styling', () => {
    mobile = false;
    touch('touchstart', 0, 0); touch('touchmove', 0, 100); touch('touchend', 0, 100);
    mobile = true; fixture.componentInstance.enabled = false; fixture.detectChanges();
    touch('touchstart', 0, 0); touch('touchmove', 0, 100); touch('touchend', 0, 100);
    expect(refresh).not.toHaveBeenCalled();
    expect(document.documentElement.classList.contains('app-pull-refresh')).toBe(false);
  });
});
