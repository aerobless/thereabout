import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {FinanceContext} from '../shared/finance-ui';
import {CounterpartyListComponent} from './counterparty-list.component';

it('copies the surviving identity metadata, clears an absent website and invalidates the merge preview', () => {
  TestBed.configureTestingModule({providers:[{provide:FinanceContext,useValue:{revision:signal(0)}}]});
  const component = TestBed.runInInjectionContext(() => new CounterpartyListComponent());
  component.selected.set([
    {id:1,name:'First merchant',websiteUrl:'https://example.com/',active:true,aliases:[],version:1,accounts:[]},
    {id:2,name:'Second merchant',active:true,aliases:[],version:2,accounts:[]},
  ]);
  component.targetId = 1; component.targetChanged();
  expect(component.name).toBe('First merchant');
  expect(component.websiteUrl).toBe('https://example.com/');
  component.targetId = 2; component.targetChanged();
  expect(component.name).toBe('Second merchant');
  expect(component.websiteUrl).toBe('');
  expect(component.preview()).toBeNull();
});
