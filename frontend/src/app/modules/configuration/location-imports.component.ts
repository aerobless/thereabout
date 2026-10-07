import {ChangeDetectionStrategy, Component, DestroyRef, inject, signal, viewChild} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {CardModule} from 'primeng/card';
import {ButtonModule} from 'primeng/button';
import {FrontendService} from '../../../../generated/backend-api/thereabout';
import {FileImportComponent} from './file-import.component';
import {ConfigurationEditor} from './configuration-navigation';

@Component({
  selector: 'app-location-imports', imports: [FileImportComponent, CardModule, ButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush, styleUrl: './configuration.component.scss',
  template: `<div class="configuration-container">
    <section id="data-import" tabindex="-1" aria-label="Data import"><app-file-import /></section>
    <p-card>
      <ng-template #header><div class="card-header"><i class="pi pi-map-marker card-header-icon" aria-hidden="true"></i><span id="overland-settings" tabindex="-1" class="card-header-title">Overland setup</span></div></ng-template>
      <p><a href="https://overland.p3k.app/" target="_blank" rel="noopener">Overland</a> tracks your location. Open this page on your mobile device to connect it to Thereabout.</p>
      <p-button label="Configure Overland" [disabled]="!ingestionKey()" (onClick)="configureOverland()" />
      @if (error()) { <p role="alert">{{ error() }}</p> }
    </p-card>
  </div>`
})
export class LocationImportsComponent implements ConfigurationEditor {
  private readonly api = inject(FrontendService);
  private readonly imports = viewChild(FileImportComponent);
  readonly ingestionKey = signal('');
  readonly error = signal('');
  constructor() {
    const destroyRef = inject(DestroyRef);
    this.api.getIngestionKey().pipe(takeUntilDestroyed(destroyRef)).subscribe({
      next: response => this.ingestionKey.set(response.value),
      error: () => this.error.set('Overland setup could not be loaded. Reopen this tab to retry.')
    });
    destroyRef.onDestroy(() => this.ingestionKey.set(''));
  }
  hasUnsavedChanges(): boolean { return !!this.imports()?.receiverName().trim(); }
  isNavigationBlocked(): boolean { return this.imports()?.uploading() ?? false; }
  configureOverland(): void {
    if (!this.ingestionKey()) return;
    const url = `${window.location.origin}/backend/api/v1/ingest/location/geojson`;
    const query = new URLSearchParams({url, token: this.ingestionKey(), device_id: 'iPhone'});
    window.location.href = `overland://setup?${query}`;
  }
}
