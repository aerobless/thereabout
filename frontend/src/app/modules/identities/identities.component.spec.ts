import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {of, Subject} from 'rxjs';
import {Identity, IdentityInApplicationService, IdentityService} from '../../../../generated/backend-api/thereabout';
import {IdentitiesComponent} from './identities.component';

describe('IdentitiesComponent table', () => {
  it('renders roles in their own column and sorts numeric app counts across pages after a delayed load', async () => {
    const response=new Subject<Identity[]>();
    await TestBed.configureTestingModule({imports:[IdentitiesComponent],providers:[
      provideZonelessChangeDetection(),provideRouter([]),
      {provide:IdentityService,useValue:{getIdentities:()=>response}},
      {provide:IdentityInApplicationService,useValue:{getUnlinkedIdentityInApplications:()=>of([])}}
    ]}).compileComponents();
    const fixture=TestBed.createComponent(IdentitiesComponent);
    await fixture.whenStable();
    const identities:Identity[]=Array.from({length:18},(_,index)=>({
      id:index+1,shortName:`Person ${String(index).padStart(2,'0')}`,
      role:index===1?'USER':index===2?'ADMIN':null,
      identityInApplications:Array.from({length:index},(_,app)=>({id:index*100+app,application:'Cloudflare',identifier:'example@example.org'}))
    }));
    response.next(identities);await fixture.whenStable();
    const table=(fixture.nativeElement as HTMLElement).querySelector('.identity-table')!;
    expect(Array.from(table.querySelectorAll('th'),cell=>cell.textContent?.trim())).toEqual(['Short Name','Role','Relationship','Group','App Identities']);
    const user=table.querySelectorAll('tbody tr')[1];
    expect(user.querySelectorAll('td')[0].textContent).toBe('Person 01');
    expect(user.querySelectorAll('td')[1].textContent).toBe('User');
    expect(table.textContent).not.toContain('Cloudflare:');
    const counts=()=>Array.from(table.querySelectorAll('td[data-label="App identities"]'),cell=>Number(cell.textContent));
    const header=table.querySelector<HTMLElement>('th[psortablecolumn="appIdentityCount"]')!;
    header.click();await fixture.whenStable();
    expect(counts()).toEqual(Array.from({length:15},(_,index)=>index));
    header.click();await fixture.whenStable();
    expect(counts()).toEqual(Array.from({length:15},(_,index)=>17-index));
    fixture.destroy();
  });
});
