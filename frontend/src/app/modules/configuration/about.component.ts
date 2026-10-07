import {ChangeDetectionStrategy, Component, DestroyRef, inject, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {DatePipe} from '@angular/common';
import {CardModule} from 'primeng/card';
import {FrontendConfigurationResponse, FrontendService} from '../../../../generated/backend-api/thereabout';
import {registerRefresh} from '../../shared/refresh/refresh-coordinator';

@Component({
  selector: 'app-configuration-about', imports: [CardModule, DatePipe], changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<p-card>
    <ng-template #header><div class="card-header"><i class="pi pi-info-circle card-header-icon" aria-hidden="true"></i><span id="version-settings" tabindex="-1" class="card-header-title">About Thereabout</span></div></ng-template>
    @if (config()?.versionDetails; as version) {
      <dl class="app-metadata">
        <div><dt>Version</dt><dd><code>{{ version.version }}</code></dd></div>
        <div><dt>Commit date</dt><dd>{{ version.commitTime | date:'d MMM y, HH:mm' }}</dd></div>
        <div><dt>Branch</dt><dd><code>{{ version.branch }}</code></dd></div>
        <div><dt>Commit</dt><dd><code>{{ version.commitRef }}</code></dd></div>
      </dl>
    } @else { <p role="status">{{ error() || 'Loading version…' }}</p> }
    <p class="app-card-attribution">Made with ♡ by <a href="https://github.com/aerobless/thereabout" target="_blank" rel="noopener">aerobless <i class="pi pi-arrow-up-right" aria-hidden="true"></i></a></p>
  </p-card>`
})
export class AboutComponent {
  private readonly api = inject(FrontendService);
  private readonly destroyRef = inject(DestroyRef);
  readonly config = signal<FrontendConfigurationResponse | null>(null);
  readonly error = signal('');
  private readonly refresh = registerRefresh(() => this.load());
  constructor() { this.load(); }
  private load(): void {
    this.api.getFrontendConfiguration().pipe(this.refresh.track('configuration'), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: config => { this.error.set(''); this.config.set(config); },
      error: () => this.error.set('Version information could not be loaded.')
    });
  }
}
