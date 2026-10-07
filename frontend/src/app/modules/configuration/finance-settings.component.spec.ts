import {provideZonelessChangeDetection, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {MessageService} from 'primeng/api';
import {of} from 'rxjs';
import {FinanceContext} from '../finances/shared/finance-ui';
import {RatesDialogComponent} from '../finances/dialogs/rates-dialog.component';
import {FinanceSettingsComponent} from './finance-settings.component';
import {ConfigurationNavigation} from './configuration-navigation';

describe('Finance configuration navigation',()=>{
  it('protects category and rate drafts with Stay/Discard and blocks outstanding saves',async()=>{
    const context={error:signal(''),metadata:signal({error:''}),categories:signal([]),currencies:signal([{code:'EUR'},{code:'CHF'}]),revision:signal(0),saving:signal(false),api:{client:{financeListRates:()=>of({items:[]})}}};
    TestBed.configureTestingModule({providers:[provideZonelessChangeDetection(),ConfigurationNavigation,{provide:MessageService,useValue:{add:vi.fn()}}]})
      .overrideComponent(FinanceSettingsComponent,{set:{providers:[{provide:FinanceContext,useValue:context}]}});
    const fixture=TestBed.createComponent(FinanceSettingsComponent);fixture.detectChanges();await fixture.whenStable();
    const editor=fixture.componentInstance;const navigation=TestBed.inject(ConfigurationNavigation);
    expect(navigation.leave(editor)).toBe(true);
    const field=fixture.nativeElement.querySelector('finance-categories-dialog input') as HTMLInputElement;
    field.value='Draft category';field.dispatchEvent(new Event('input'));
    expect(editor.hasUnsavedChanges()).toBe(true);
    const stay=navigation.leave(editor);navigation.answer(false);expect(await stay).toBe(false);
    expect(field.value).toBe('Draft category');
    const discard=navigation.leave(editor);navigation.answer(true);expect(await discard).toBe(true);
    const rate=fixture.debugElement.query(By.directive(RatesDialogComponent)).componentInstance as RatesDialogComponent;
    rate.form.markAsDirty();
    expect(editor.hasUnsavedChanges()).toBe(true);
    context.saving.set(true);expect(navigation.leave(editor)).toBe(false);expect(navigation.confirming()).toBe(false);
  });
});
