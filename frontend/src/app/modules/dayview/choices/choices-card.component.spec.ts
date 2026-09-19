import {TestBed, ComponentFixture} from '@angular/core/testing';
import {of, Subject, throwError} from 'rxjs';
import {vi} from 'vitest';
import {ChoicesService, ChoicesHistory, ChoicesDay} from '../../../../../generated/backend-api/thereabout';
import {ChoicesCardComponent} from './choices-card.component';

describe('ChoicesCardComponent', () => {
  let fixture: ComponentFixture<ChoicesCardComponent>;
  let component: ChoicesCardComponent;
  const history = (date = '2026-09-18', score = 0, editable = true): ChoicesHistory => ({
    date, score, editable, series: [{date, score}]
  });
  let api: {getChoicesHistory: ReturnType<typeof vi.fn>; adjustChoices: ReturnType<typeof vi.fn>};
  beforeEach(async () => {
    api = {getChoicesHistory: vi.fn().mockReturnValue(of(history())), adjustChoices: vi.fn().mockReturnValue(of({date:'2026-09-18',score:1}))};
    await TestBed.configureTestingModule({imports:[ChoicesCardComponent], providers:[{provide:ChoicesService,useValue:api}]}).compileComponents();
    fixture = TestBed.createComponent(ChoicesCardComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('date', '2026-09-18');
    fixture.detectChanges();
  });
  afterEach(() => TestBed.resetTestingModule());

  it('starts neutral and changes colour with the saved score without opening the dialog', () => {
    expect(component.state).toBe('neutral');
    api.getChoicesHistory.mockReturnValue(of(history('2026-09-18',1)));
    fixture.nativeElement.querySelector('[aria-label="Add one Choices point"]').click();
    fixture.detectChanges();
    expect(api.adjustChoices).toHaveBeenCalledWith('2026-09-18',{delta:1});
    expect(component.visible).toBe(false);
    expect(component.state).toBe('positive');
    expect(fixture.nativeElement.querySelector('.score').textContent).toBe('+1');
    api.getChoicesHistory.mockReturnValue(of(history('2026-09-18',-1)));
    component.adjust(-1);
    expect(component.state).toBe('negative');
  });

  it('blocks repeated presses during a save and refreshes the selected date after navigation', () => {
    const save = new Subject<ChoicesDay>();
    api.adjustChoices.mockReturnValue(save);
    component.adjust(1); component.adjust(1);
    expect(api.adjustChoices).toHaveBeenCalledTimes(1);
    api.getChoicesHistory.mockReturnValue(of(history('2026-09-17',4)));
    fixture.componentRef.setInput('date','2026-09-17'); fixture.detectChanges();
    save.next({date:'2026-09-18',score:1});
    expect(component.history?.score).toBe(4);
    expect(api.getChoicesHistory).toHaveBeenLastCalledWith('2026-09-17',7);
  });

  it('reconciles an uncertain save without replaying it and locks controls when reconciliation fails', () => {
    api.adjustChoices.mockReturnValue(throwError(() => new Error('Lost response')));
    api.getChoicesHistory.mockReturnValue(throwError(() => new Error('Offline')));
    component.adjust(1);
    expect(component.saveError).toBe(true);
    expect(component.canAdjust).toBe(false);
    expect(api.adjustChoices).toHaveBeenCalledTimes(1);
    api.getChoicesHistory.mockReturnValue(of(history('2026-09-18',1)));
    component.load();
    expect(component.canAdjust).toBe(true);
    expect(component.history?.score).toBe(1);
  });

  it('ignores stale reads and uses the selected range', () => {
    const old = new Subject<ChoicesHistory>();
    api.getChoicesHistory.mockReturnValueOnce(old).mockReturnValueOnce(of(history('2026-09-18',2)));
    component.load(); component.selectRange(30);
    old.next(history('2026-09-18',99));
    expect(component.history?.score).toBe(2);
    expect(api.getChoicesHistory).toHaveBeenLastCalledWith('2026-09-18',30);
    expect(component.chartData?.datasets[0].data).toEqual([2]);
  });

  it('disables adjustments on future dates and while loading', () => {
    api.getChoicesHistory.mockReturnValue(of(history('2099-01-01',0,false)));
    fixture.componentRef.setInput('date','2099-01-01'); fixture.detectChanges();
    component.adjust(1);
    expect(api.adjustChoices).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('[aria-label="Add one Choices point"]').disabled).toBe(true);
    api.getChoicesHistory.mockReturnValue(new Subject<ChoicesHistory>());
    component.load(); expect(component.canAdjust).toBe(false);
  });
});
