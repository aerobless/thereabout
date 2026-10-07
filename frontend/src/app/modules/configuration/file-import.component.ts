import {ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {FormsModule} from '@angular/forms';
import {EMPTY, Subscription, expand, finalize, switchMap, timer} from 'rxjs';
import {CardModule} from 'primeng/card';
import {ButtonModule} from 'primeng/button';
import {SelectModule} from 'primeng/select';
import {TooltipModule} from 'primeng/tooltip';
import {ProgressBarModule} from 'primeng/progressbar';
import {FileUploadHandlerEvent, FileUploadModule} from 'primeng/fileupload';
import {AutoCompleteCompleteEvent, AutoCompleteModule} from 'primeng/autocomplete';
import {MessageService} from 'primeng/api';
import {FileImportStatus, FrontendService, IdentityInApplicationService, ImportType} from '../../../../generated/backend-api/thereabout';
import {registerRefresh} from '../../shared/refresh/refresh-coordinator';

@Component({
  selector: 'app-file-import',
  imports: [FormsModule, CardModule, ButtonModule, SelectModule, TooltipModule, ProgressBarModule, FileUploadModule, AutoCompleteModule],
  templateUrl: './file-import.component.html', styleUrl: './configuration.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FileImportComponent {
  private readonly api = inject(FrontendService);
  private readonly identities = inject(IdentityInApplicationService);
  private readonly toast = inject(MessageService);
  private readonly destroyRef = inject(DestroyRef);
  private polling?: Subscription;
  private observedRunning = false;
  readonly status = signal<FileImportStatus | null>(null);
  readonly error = signal('');
  readonly uploading = signal(false);
  readonly receiverName = signal('');
  readonly receivers = signal<string[]>([]);
  readonly filteredReceivers = signal<string[]>([]);
  readonly importTypeOptions: {label: string; value: ImportType; accept: string; description: string}[] = [
    {label: 'Google Maps Records.json', value: 'GOOGLE_MAPS_RECORDS', accept: '.json', description: 'Upload your Google Maps Records.json export.'},
    {label: 'WhatsApp Chat History', value: 'WHATSAPP_CHAT', accept: '.txt', description: 'Upload your WhatsApp chat export (.txt).'},
    {label: 'Health Auto Export JSON', value: 'HEALTH_AUTO_EXPORT_JSON', accept: '.json', description: 'Upload your Health Auto Export metrics and workouts.'}
  ];
  readonly selectedImportType = signal(this.importTypeOptions[0]);
  readonly isReceiverRequired = computed(() => this.selectedImportType().value === 'WHATSAPP_CHAT');
  readonly isBrowseDisabled = computed(() => this.uploading() || this.status()?.status === 'IN_PROGRESS' || (this.isReceiverRequired() && !this.receiverName().trim()));
  protected readonly FileImportStatus = FileImportStatus;
  private readonly refresh = registerRefresh(() => { this.pollStatus(); this.loadReceivers(); }, () => this.uploading());

  constructor() { this.pollStatus(); this.loadReceivers(); }

  pollStatus(): void {
    this.polling?.unsubscribe();
    this.error.set('');
    this.polling = this.api.fileImportStatus().pipe(
      this.refresh.track('import'),
      expand(status => status.status === 'IN_PROGRESS'
        ? timer(1000).pipe(switchMap(() => this.api.fileImportStatus())) : EMPTY),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: status => {
        this.status.set(status);
        if (status.status === 'IN_PROGRESS') this.observedRunning = true;
        else if (this.observedRunning) {
          this.observedRunning = false;
          if (status.status === 'SUCCEEDED') this.toast.add({severity: 'success', summary: 'Import complete'});
          if (status.status === 'FAILED') this.toast.add({severity: 'error', summary: 'Import failed', detail: status.error});
        }
      },
      error: () => this.error.set('Import status could not be loaded. Retry to check the result.')
    });
  }

  customUpload(event: FileUploadHandlerEvent): void {
    const file = event.files[0];
    if (!file || this.isBrowseDisabled()) return;
    this.uploading.set(true);
    this.api.importFromFile(file, this.selectedImportType().value, this.isReceiverRequired() ? this.receiverName().trim() : undefined)
      .pipe(finalize(() => this.uploading.set(false)), takeUntilDestroyed(this.destroyRef)).subscribe({
        next: () => { this.receiverName.set(''); this.observedRunning = true; this.pollStatus(); },
        error: () => { this.onError(); this.pollStatus(); }
      });
  }

  onError(): void { this.toast.add({severity: 'error', summary: 'Upload failed', detail: 'Check the file and whether another import is running.'}); }
  filterReceivers(event: AutoCompleteCompleteEvent): void {
    this.filteredReceivers.set(this.receivers().filter(name => name.toLowerCase().includes(event.query.toLowerCase())));
  }
  private loadReceivers(): void {
    this.identities.getIdentityInApplicationsByApplication('WhatsApp').pipe(this.refresh.track('receivers'), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: values => this.receivers.set(values.map(value => value.identifier)), error: () => {}
    });
  }
}
