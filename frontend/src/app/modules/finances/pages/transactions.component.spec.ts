import {provideZonelessChangeDetection, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {of, Subject} from 'rxjs';
import {FinanceAccount, FinanceTransaction} from '../../../../../generated/backend-api/thereabout';
import {ProtectedImageCache} from '../../../shared/current-user/protected-image-cache.service';
import {FinanceContext, FinanceDialogs} from '../shared/finance-ui';
import {TransactionsComponent} from './transactions.component';

const account = (id: number, name: string): FinanceAccount => ({
  id, name, kind:'CASH', currency:'CHF', active:true, deleted:false, includeNetWorth:true,
  version:2, balance:'100', websiteUrl:'https://example.com',
});
const transaction = (changes: Partial<FinanceTransaction>): FinanceTransaction => ({
  id:1, type:'WITHDRAWAL', effect:'OPERATING', description:'Purchase', occurredAt:'2026-10-08T12:00:00',
  deleted:false, version:0, sourceAccountId:1, sourceName:'Neon', sourceAmount:'10', sourceCurrency:'CHF',
  destinationAccountId:3, destinationCounterpartyId:45, destinationName:'Shop', destinationAmount:'10', destinationCurrency:'CHF',
  ...changes,
});

describe('Transaction account columns', () => {
  it('keeps expenses, income and transfers in the correct columns with their logos and counterparty links', async () => {
    const revision = signal(0);
    const icons = new Map<string, Subject<Blob>>();
    const images = {get:vi.fn((url:string) => {
      if (!icons.has(url)) icons.set(url,new Subject<Blob>());
      return icons.get(url)!;
    })};
    const items = [
      transaction({}),
      transaction({id:2,type:'DEPOSIT',sourceAccountId:3,sourceName:'Shop',sourceCounterpartyId:45,destinationAccountId:1,destinationName:'Neon',destinationCounterpartyId:undefined}),
      transaction({id:3,type:'TRANSFER',destinationAccountId:2,destinationName:'Revolut',destinationCounterpartyId:undefined}),
      transaction({id:4,type:'TRANSFER',sourceAccountId:2,sourceName:'Revolut',destinationAccountId:1,destinationName:'Neon',destinationCounterpartyId:undefined}),
    ];
    TestBed.configureTestingModule({providers:[
      provideZonelessChangeDetection(), provideRouter([]),
      {provide:ProtectedImageCache,useValue:images},
      {provide:FinanceDialogs,useValue:{open:vi.fn()}},
      {provide:FinanceContext,useValue:{
        accounts:signal([account(1,'Neon'),account(2,'Revolut')]), categories:signal([]), revision, saving:signal(false),
        api:{transactions:vi.fn(()=>of({items,total:items.length,page:0,pageSize:50}))}, money:(value:string)=>value,
      }},
    ]});
    const fixture = TestBed.createComponent(TransactionsComponent);
    await fixture.whenStable();
    const root:HTMLElement = fixture.nativeElement;
    expect([...root.querySelectorAll('thead tr:first-child th')].map(cell=>cell.textContent?.trim())).toEqual(['','Description','Date','From','To','Category','Amount','']);
    const rows = [...root.querySelectorAll('tbody tr')];
    expect(rows.map(row=>[row.children[3].textContent?.trim(),row.children[4].textContent?.trim()])).toEqual([
      ['Neon','Shop'],['Shop','Neon'],['Neon','Revolut'],['Revolut','Neon'],
    ]);
    expect(rows[0].children[4].querySelector('a')?.getAttribute('href')).toBe('/finances/counterparties/45');
    expect(rows[1].children[3].querySelector('a')?.getAttribute('href')).toBe('/finances/counterparties/45');
    expect(images.get).toHaveBeenCalledWith('/api/finances/accounts/1/icon?v=2');
    expect(images.get).toHaveBeenCalledWith('/api/finances/accounts/2/icon?v=2');
    expect(images.get).toHaveBeenCalledWith('/api/finances/counterparties/45/icon?revision=0');
    icons.get('/api/finances/counterparties/45/icon?revision=0')!.error(new Error('No website icon'));
    await fixture.whenStable();
    expect(root.querySelectorAll('finance-counterparty-logo img')).toHaveLength(0);
    expect(root.querySelectorAll('finance-counterparty-logo .pi-shop')).toHaveLength(2);
    expect(rows[0].children[4].textContent?.trim()).toBe('Shop');
    revision.update(value=>value+1);
    await fixture.whenStable();
    expect(images.get).toHaveBeenCalledWith('/api/finances/counterparties/45/icon?revision=1');
    fixture.componentRef.setInput('accountId',1);
    await fixture.whenStable();
    expect([...root.querySelectorAll('thead tr:first-child th')].map(cell=>cell.textContent?.trim())).toEqual(['','Description','Date','Counterparty','Category','Amount','Running balance','']);
    const accountRows = [...root.querySelectorAll('tbody tr')];
    expect(accountRows.map(row=>row.children[3].textContent?.trim())).toEqual(['Shop','Shop','Revolut','Revolut']);
    expect(root.querySelectorAll('tbody finance-transaction-account')).toHaveLength(4);
    expect(accountRows[0].children[3].querySelector('a')?.getAttribute('href')).toBe('/finances/counterparties/45');
    expect(accountRows[1].children[3].querySelector('a')?.getAttribute('href')).toBe('/finances/counterparties/45');
  });
});
