import {ChangeDetectionStrategy, Component, DestroyRef, Injector, afterNextRender, inject, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {FormsModule} from '@angular/forms';
import {NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet} from '@angular/router';
import {AutoCompleteCompleteEvent, AutoCompleteModule} from 'primeng/autocomplete';
import {ButtonModule} from 'primeng/button';
import {filter} from 'rxjs';
import {AppModalComponent} from '../../shared/modal/app-modal.component';
import {ConfigurationNavigation} from './configuration-navigation';
import {ConfigurationSearchResult, searchConfiguration} from './configuration-search';

@Component({
  selector: 'app-configuration',
  imports: [FormsModule, RouterLink, RouterLinkActive, RouterOutlet, AutoCompleteModule, ButtonModule, AppModalComponent],
  templateUrl: './configuration.component.html', styleUrl: './configuration.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ConfigurationComponent {
  readonly navigation = inject(ConfigurationNavigation);
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);
  readonly search = signal<ConfigurationSearchResult | string | null>(null);
  readonly suggestions = signal<ConfigurationSearchResult[]>([]);
  readonly tabs = [
    {label: 'Connections', path: '/configuration/connections'},
    {label: 'Location & imports', path: '/configuration/location-imports'},
    {label: 'API & access', path: '/configuration/api-access'},
    {label: 'Finances', path: '/configuration/finances'},
    {label: 'About', path: '/configuration/about'}
  ];
  constructor() {
    this.router.events.pipe(filter(event => event instanceof NavigationEnd), takeUntilDestroyed(inject(DestroyRef))).subscribe(() => this.focusFragment());
    this.focusFragment();
  }
  complete(event: AutoCompleteCompleteEvent): void { this.suggestions.set(searchConfiguration(event.query)); }
  async select(result: ConfigurationSearchResult): Promise<void> {
    const navigated = await this.router.navigate([result.path], {fragment: result.anchor});
    if (navigated || this.router.url === result.path + '#' + result.anchor) this.focusFragment();
    this.search.set(null);
  }
  private focusFragment(): void {
    const fragment = this.router.parseUrl(this.router.url).fragment;
    if (!fragment) return;
    afterNextRender(() => {
      const target = document.getElementById(fragment);
      if (!target) return;
      const details = target.closest('details');
      if (details) details.open = true;
      target.scrollIntoView?.({block: 'start'});
      target.focus({preventScroll: true});
    }, {injector: this.injector});
  }
}
