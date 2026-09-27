import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {MessageService} from 'primeng/api';
import {FrontendService} from '../../../../generated/backend-api/thereabout';
import {TelegramSettingsComponent} from './telegram-settings.component';

describe('Telegram status polling', () => {
  const api = {getTelegramStatus: vi.fn()};
  beforeEach(() => {
    vi.useFakeTimers(); vi.clearAllMocks();
    TestBed.configureTestingModule({providers: [
      {provide: FrontendService, useValue: api}, {provide: MessageService, useValue: {add: vi.fn()}}
    ]}).overrideComponent(TelegramSettingsComponent, {set: {template: ''}});
  });
  afterEach(() => { TestBed.resetTestingModule(); vi.useRealTimers(); });

  it('uses one stream across authentication and backfill and stops at completion', async () => {
    api.getTelegramStatus.mockReturnValueOnce(of({status: 'WAIT_CODE'}))
      .mockReturnValueOnce(of({status: 'READY', resyncStatus: 'IN_PROGRESS'}))
      .mockReturnValue(of({status: 'READY', resyncStatus: 'COMPLETE'}));
    const component = TestBed.createComponent(TelegramSettingsComponent).componentInstance;
    await vi.advanceTimersByTimeAsync(4000);
    expect(component.status()?.resyncStatus).toBe('COMPLETE');
    await vi.advanceTimersByTimeAsync(4000);
    expect(api.getTelegramStatus).toHaveBeenCalledTimes(3);
  });

  it('can retry after an error and cancels pending polls when destroyed', async () => {
    api.getTelegramStatus.mockReturnValueOnce(throwError(() => new Error('offline')))
      .mockReturnValue(of({status: 'WAIT_CODE'}));
    const fixture = TestBed.createComponent(TelegramSettingsComponent);
    expect(fixture.componentInstance.error()).not.toBe('');
    fixture.componentInstance.loadStatus(); fixture.componentInstance.loadStatus();
    fixture.destroy(); await vi.advanceTimersByTimeAsync(4000);
    expect(api.getTelegramStatus).toHaveBeenCalledTimes(3);
  });
});
