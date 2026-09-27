import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {MessageService} from 'primeng/api';
import {FileImportComponent} from './file-import.component';
import {FrontendService, IdentityInApplicationService} from '../../../../generated/backend-api/thereabout';

describe('file import lifecycle', () => {
  const api = {fileImportStatus: vi.fn(), importFromFile: vi.fn()};
  const toast = {add: vi.fn()};
  beforeEach(() => {
    vi.useFakeTimers(); vi.clearAllMocks();
    TestBed.configureTestingModule({providers: [
      {provide: FrontendService, useValue: api},
      {provide: IdentityInApplicationService, useValue: {getIdentityInApplicationsByApplication: () => of([])}},
      {provide: MessageService, useValue: toast}
    ]}).overrideComponent(FileImportComponent, {set: {template: ''}});
  });
  afterEach(() => { TestBed.resetTestingModule(); vi.useRealTimers(); });

  it('reports a failed import as failure, retains the result and stops polling', async () => {
    api.fileImportStatus.mockReturnValueOnce(of({status: 'IN_PROGRESS', progress: 50}))
      .mockReturnValue(of({status: 'FAILED', progress: 50, error: 'Some records may already have been saved.'}));
    const fixture = TestBed.createComponent(FileImportComponent);
    await vi.advanceTimersByTimeAsync(1000);
    expect(fixture.componentInstance.status()?.status).toBe('FAILED');
    expect(toast.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'error'}));
    expect(toast.add).not.toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
    await vi.advanceTimersByTimeAsync(5000);
    expect(api.fileImportStatus).toHaveBeenCalledTimes(2);
    expect(fixture.componentInstance.isBrowseDisabled()).toBe(false);
  });

  it('replaces an existing polling stream and cancels it on destruction', async () => {
    api.fileImportStatus.mockReturnValue(of({status: 'IN_PROGRESS', progress: 10}));
    const fixture = TestBed.createComponent(FileImportComponent);
    fixture.componentInstance.pollStatus(); fixture.componentInstance.pollStatus();
    await vi.advanceTimersByTimeAsync(1000);
    expect(api.fileImportStatus).toHaveBeenCalledTimes(4);
    fixture.destroy();
    await vi.advanceTimersByTimeAsync(5000);
    expect(api.fileImportStatus).toHaveBeenCalledTimes(4);
  });

  it('recovers from polling failure without reporting a historical success', () => {
    api.fileImportStatus.mockReturnValueOnce(throwError(() => new Error('offline')))
      .mockReturnValue(of({status: 'SUCCEEDED', progress: 100}));
    const component = TestBed.createComponent(FileImportComponent).componentInstance;
    expect(component.error()).not.toBe('');
    component.pollStatus();
    expect(component.error()).toBe('');
    expect(component.status()?.status).toBe('SUCCEEDED');
    expect(toast.add).not.toHaveBeenCalled();
  });
});
