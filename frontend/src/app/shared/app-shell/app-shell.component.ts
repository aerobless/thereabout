import {ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal, viewChild} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {IsActiveMatchOptions, NavigationEnd, PRIMARY_OUTLET, Router, RouterLink, RouterLinkActive} from '@angular/router';
import {filter, map, tap} from 'rxjs';
import {Menu, MenuModule} from 'primeng/menu';
import {MenuItem} from 'primeng/api';

@Component({
  selector: 'thereabout-app-shell',
  imports: [RouterLink, RouterLinkActive, MenuModule],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
  host: {'[class.sidebar-collapsed]': 'sidebarCollapsed()'},
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppShellComponent {
  readonly sidebarCollapsed = signal(this.readSidebarPreference());
  private readonly router = inject(Router);
  readonly moreMenu = viewChild<Menu>('moreMenu');
  private readonly url = toSignal(this.router.events.pipe(
    filter((event): event is NavigationEnd => event instanceof NavigationEnd),
    tap(() => this.moreMenu()?.hide()),
    map(event => event.urlAfterRedirects)
  ), {initialValue: this.router.url});
  private readonly urlTree = computed(() => this.router.parseUrl(this.url()));
  private readonly path = computed(() => this.urlTree().root.children[PRIMARY_OUTLET]?.segments.map(segment => segment.path).join('/') ?? '');
  readonly embedded = computed(() => this.path() === 'locationhistory' && this.urlTree().queryParams['embed'] === 'true');
  readonly moreActive = computed(() => this.path() === 'configuration' || this.path() === 'identities' || this.path().startsWith('identities/'));
  readonly exactMatch: IsActiveMatchOptions = {paths: 'exact', queryParams: 'ignored', matrixParams: 'ignored', fragment: 'ignored'};
  readonly sectionMatch: IsActiveMatchOptions = {...this.exactMatch, paths: 'subset'};
  readonly links = [
    {label: 'Day View', mobileLabel: 'Day View', path: '/', icon: 'pi pi-home', match: this.exactMatch},
    {label: 'Location History', mobileLabel: 'Locations', path: '/locationhistory', icon: 'pi pi-map-marker', match: this.exactMatch},
    {label: 'Statistics', mobileLabel: 'Statistics', path: '/statistics', icon: 'pi pi-chart-bar', match: this.exactMatch},
    {label: 'Identities', mobileLabel: 'Identities', path: '/identities', icon: 'pi pi-users', match: this.sectionMatch}
  ];
  readonly moreItems: MenuItem[] = [
    {label: 'Identities', icon: 'pi pi-users', routerLink: '/identities'},
    {label: 'Configuration', icon: 'pi pi-cog', routerLink: '/configuration'}
  ];

  constructor() {
    const mobile = window.matchMedia?.('(max-width: 768px)');
    const closeMenu = () => this.moreMenu()?.hide();
    mobile?.addEventListener('change', closeMenu);
    inject(DestroyRef).onDestroy(() => mobile?.removeEventListener('change', closeMenu));
  }

  toggleSidebar() {
    this.sidebarCollapsed.update(collapsed => !collapsed);
    try {
      localStorage.setItem('thereabout.sidebarCollapsed', String(this.sidebarCollapsed()));
    } catch { /* Keep the toggle usable when browser storage is unavailable. */ }
  }

  private readSidebarPreference(): boolean {
    try {
      return localStorage.getItem('thereabout.sidebarCollapsed') === 'true';
    } catch {
      return false;
    }
  }
}
