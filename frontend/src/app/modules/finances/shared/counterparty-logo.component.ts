import {ChangeDetectionStrategy, Component, computed, input, signal} from '@angular/core';
import {ProtectedImageDirective} from '../../../shared/current-user/protected-image.directive';
import {FinanceCounterparty} from '../../../../../generated/backend-api/thereabout';
@Component({
  selector: 'finance-counterparty-logo', imports: [ProtectedImageDirective], changeDetection: ChangeDetectionStrategy.OnPush,
  template: `@if (url() && failed() !== url()) { <img [protectedImage]="url()" alt="" referrerpolicy="no-referrer" (error)="failed.set(url())" /> } @else { <i class="pi pi-shop" aria-hidden="true"></i> }`,
  styles: `:host { display:grid; place-items:center; width:2.5rem; height:2.5rem; flex:0 0 2.5rem; border-radius:.65rem; background:var(--app-hover); color:var(--app-link); overflow:hidden; } img { width:100%; height:100%; object-fit:contain; padding:.3rem; }`
})
export class CounterpartyLogoComponent {
  readonly counterparty = input.required<FinanceCounterparty>();
  readonly url = computed(() => { const c = this.counterparty(); return c.websiteUrl ? '/api/finances/counterparties/' + c.id + '/icon?v=' + c.version : undefined; });
  readonly failed = signal<string | undefined>(undefined);
}
