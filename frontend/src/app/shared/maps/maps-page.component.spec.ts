import {ChangeDetectionStrategy, Component, NgZone} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {ActivatedRoute} from '@angular/router';
import {By} from '@angular/platform-browser';
import {MapsLoader, MapsPageComponent} from './maps-page.component';

@Component({selector:'test-async-page',template:'<p>{{ status }}</p>',changeDetection:ChangeDetectionStrategy.Eager})
class AsyncPage {
  status='Loading';
  complete() { setTimeout(()=>this.status='Loaded',0); }
}

describe('MapsPageComponent',()=> {
  it('continues checking mutable child views after asynchronous data arrives',async()=> {
    await TestBed.configureTestingModule({imports:[MapsPageComponent,AsyncPage],providers:[
      {provide:ActivatedRoute,useValue:{snapshot:{data:{mapPage:'day'}}}},
      {provide:MapsLoader,useValue:{load:()=>new Promise<void>(()=>{})}}
    ]}).compileComponents();
    const fixture=TestBed.createComponent(MapsPageComponent);
    fixture.componentInstance.page.set(AsyncPage);
    fixture.autoDetectChanges();
    const child=fixture.debugElement.query(By.directive(AsyncPage)).componentInstance as AsyncPage;
    TestBed.inject(NgZone).run(()=>child.complete());
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Loaded');
    fixture.destroy();
  });
});
