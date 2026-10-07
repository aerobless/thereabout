import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {Subject, of} from 'rxjs';
import {MessageService} from 'primeng/api';
import {FrontendService, IdentityInApplicationService} from '../../../../generated/backend-api/thereabout';
import {FileImportComponent} from './file-import.component';
import {LocationImportsComponent} from './location-imports.component';

describe('Location import navigation', () => {
  it('blocks while uploading, then permits navigation while the accepted import runs on the server', async () => {
    const upload = new Subject<void>();
    const status = vi.fn().mockReturnValueOnce(of({status: 'IDLE', progress: 0}))
      .mockReturnValue(of({status: 'IN_PROGRESS', progress: 10}));
    await TestBed.configureTestingModule({providers: [
      provideZonelessChangeDetection(),
      {provide: MessageService, useValue: {add: vi.fn()}},
      {provide: FrontendService, useValue: {
        getIngestionKey: () => of({value: 'synthetic-key'}), fileImportStatus: status, importFromFile: () => upload
      }},
      {provide: IdentityInApplicationService, useValue: {getIdentityInApplicationsByApplication: () => of([])}}
    ]}).compileComponents();
    const fixture = TestBed.createComponent(LocationImportsComponent);
    await fixture.whenStable();
    const imports = fixture.debugElement.query(By.directive(FileImportComponent)).injector.get(FileImportComponent);
    imports.customUpload({files: [new File(['{}'], 'Records.json', {type: 'application/json'})]});
    expect(fixture.componentInstance.isNavigationBlocked()).toBe(true);
    upload.next(); upload.complete();
    await fixture.whenStable();
    expect(imports.status()?.status).toBe('IN_PROGRESS');
    expect(fixture.componentInstance.isNavigationBlocked()).toBe(false);
    fixture.destroy();
    expect(fixture.componentInstance.ingestionKey()).toBe('');
  });
});
