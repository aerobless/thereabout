import {Subject} from 'rxjs';
import {RefreshCoordinator, RefreshHandle} from '../refresh/refresh-coordinator';
import {Component} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideRouter, Router, RouterOutlet} from '@angular/router';
import {AppShellComponent} from './app-shell.component';

@Component({template: '<p>Existing page content</p>'})
class PageStub {}

@Component({imports: [AppShellComponent, RouterOutlet], template: '<thereabout-app-shell><router-outlet /></thereabout-app-shell>'})
class TestHost {}

describe('AppShellComponent', () => {
  let fixture: ComponentFixture<TestHost>;
  let router: Router;
  const root = () => fixture.nativeElement as HTMLElement;

  beforeEach(async () => {
    localStorage.removeItem('thereabout.sidebarCollapsed');
    await TestBed.configureTestingModule({
      imports: [TestHost],
      providers: [provideRouter([
        {path: '', component: PageStub},
        {path: 'locationhistory', component: PageStub},
        {path: 'statistics', component: PageStub},
        {path: 'identities', component: PageStub},
        {path: 'identities/:id', component: PageStub},
        {path: 'configuration', component: PageStub},
        {path: 'messages', component: PageStub}
      ])]
    }).compileComponents();
    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(TestHost);
    fixture.detectChanges();
  });

  afterEach(() => localStorage.removeItem('thereabout.sidebarCollapsed'));

  async function navigate(url: string) {
    await router.navigateByUrl(url);
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('announces refreshing until the active reads finish', async () => {
    const coordinator = TestBed.inject(RefreshCoordinator);
    const pending = new Subject<void>();
    const handle = new RefreshHandle(() => pending.pipe(handle.track('read')).subscribe(), () => false);
    coordinator.register(handle);
    const refreshing = coordinator.refresh();
    fixture.detectChanges();
    expect(root().querySelector('[role="status"]')?.textContent).toContain('Refreshing…');
    expect(root().querySelector('[role="status"]')?.getAttribute('aria-busy')).toBe('true');
    pending.complete(); await refreshing; fixture.detectChanges();
    expect(root().querySelector('[role="status"]')).toBeNull();
  });

  it('keeps Day View active with date query parameters and switches destinations', async () => {
    await navigate('/?date=2026-09-17');
    expect(root().querySelector('.sidebar-nav [aria-current="page"]')?.textContent).toContain('Day View');
    expect(root().querySelector('.mobile-nav [aria-current="page"]')?.textContent).toContain('Day View');
    await navigate('/statistics');
    expect(root().querySelector('.sidebar-nav [aria-current="page"]')?.textContent).toContain('Statistics');
    expect(root().querySelectorAll('.sidebar-nav .active')).toHaveLength(1);
  });

  it('collapses and expands the desktop sidebar, preserving navigation names and the saved preference', async () => {
    await navigate('/');
    const toggle = () => root().querySelector<HTMLButtonElement>('.sidebar-toggle')!;
    expect(toggle().getAttribute('aria-expanded')).toBe('true');
    toggle().click();
    fixture.detectChanges();
    expect(toggle().getAttribute('aria-label')).toBe('Expand sidebar');
    expect(toggle().getAttribute('aria-expanded')).toBe('false');
    expect(root().querySelector('thereabout-app-shell')?.classList.contains('sidebar-collapsed')).toBe(true);
    expect(root().querySelector('.sidebar-nav a')?.getAttribute('aria-label')).toBe('Day View');
    expect(root().querySelector('.sidebar-nav a')?.getAttribute('title')).toBe('Day View');
    await navigate('/identities/42');
    expect(root().querySelector('.sidebar-nav [aria-current="page"]')?.getAttribute('aria-label')).toBe('Identities');
    expect(toggle().getAttribute('aria-expanded')).toBe('false');
    fixture.destroy();
    fixture = TestBed.createComponent(TestHost);
    fixture.detectChanges();
    expect(toggle().getAttribute('aria-expanded')).toBe('false');
    toggle().click();
    fixture.detectChanges();
    expect(toggle().getAttribute('aria-label')).toBe('Collapse sidebar');
    expect(toggle().getAttribute('aria-expanded')).toBe('true');
    expect(localStorage.getItem('thereabout.sidebarCollapsed')).toBe('false');
  });

  it('highlights Identities and More for nested identity routes, and Configuration separately', async () => {
    await navigate('/identities/42');
    expect(root().querySelector('.sidebar-nav [aria-current="page"]')?.textContent).toContain('Identities');
    expect(root().querySelector('.mobile-nav button')?.classList.contains('active')).toBe(true);
    await navigate('/configuration');
    expect(root().querySelector('.settings-link')?.getAttribute('aria-current')).toBe('page');
    expect(root().querySelector('.mobile-nav button')?.classList.contains('active')).toBe(true);
    await navigate('/messages');
    expect(root().querySelector('.mobile-nav .active')).toBeNull();
  });

  it('opens More with the existing destinations and closes it after navigation', async () => {
    await navigate('/');
    const trigger = root().querySelector<HTMLButtonElement>('.mobile-nav button')!;
    trigger.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(trigger.getAttribute('aria-expanded')).toBe('true');
    const menu = document.querySelector('.mobile-more-menu')!;
    expect(menu.textContent).toContain('Identities');
    expect(menu.textContent).toContain('Configuration');
    (menu.querySelector('a[href="/configuration"]') as HTMLElement).click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(router.url).toBe('/configuration');
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
  });

  it('removes navigation and shell spacing only for location-history embeds, including query-only transitions', async () => {
    await navigate('/locationhistory?embed=true&fromDate=2026-09-14&toDate=2026-09-17');
    expect(root().querySelector('nav')).toBeNull();
    expect(root().querySelector('.mobile-brand-row')).toBeNull();
    expect(root().querySelector('main')?.classList.contains('embed-content')).toBe(true);
    expect(root().textContent).toContain('Existing page content');
    await navigate('/locationhistory?embed=false');
    expect(root().querySelector('.sidebar-nav')).not.toBeNull();
    expect(root().querySelector('main')?.classList.contains('app-content')).toBe(true);
    await navigate('/?embed=true');
    expect(root().querySelector('.sidebar-nav')).not.toBeNull();
  });
});
