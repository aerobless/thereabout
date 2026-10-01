import {ChangeDetectionStrategy, Component, DestroyRef, inject, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {CardModule} from 'primeng/card';
import {ButtonModule} from 'primeng/button';
import {InputTextModule} from 'primeng/inputtext';
import {TooltipModule} from 'primeng/tooltip';
import {FrontendConfigurationResponse, FrontendService} from '../../../../generated/backend-api/thereabout';
import {FinanceMcpSettingsComponent} from './finance-mcp-settings.component';
import {GoogleCalendarSettingsComponent} from '../calendar/google-calendar-settings.component';
import {FileImportComponent} from './file-import.component';
import {TelegramSettingsComponent} from './telegram-settings.component';
import {registerRefresh} from '../../shared/refresh/refresh-coordinator';

@Component({
  selector: 'app-configuration',
  imports: [CardModule, ButtonModule, InputTextModule, TooltipModule, FinanceMcpSettingsComponent, GoogleCalendarSettingsComponent, FileImportComponent, TelegramSettingsComponent],
  templateUrl: './configuration.component.html', styleUrl: './configuration.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ConfigurationComponent {
  private readonly api = inject(FrontendService);
  private readonly destroyRef = inject(DestroyRef);
  readonly ingestionKey = signal('');
  readonly thereaboutConfig = signal<FrontendConfigurationResponse | undefined>(undefined);
  private readonly refresh = registerRefresh(() => this.loadConfiguration());
  constructor() {
    this.loadConfiguration();
    this.api.getIngestionKey().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({next: response => this.ingestionKey.set(response.value), error: () => {}});
  }
  private loadConfiguration(): void {
    this.api.getFrontendConfiguration().pipe(this.refresh.track('configuration'), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: config => this.thereaboutConfig.set(config), error: () => {}
    });
  }
  configureOverland(): void {
    const url = `${window.location.origin}/backend/api/v1/ingest/location/geojson`;
    const query = new URLSearchParams({url, token: this.ingestionKey(), device_id: 'iPhone'});
    window.location.href = `overland://setup?${query}`;
  }
}
