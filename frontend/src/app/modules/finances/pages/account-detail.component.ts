import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
} from "@angular/core";
import { toSignal } from "@angular/core/rxjs-interop";
import { ActivatedRoute } from "@angular/router";
import { OverviewComponent } from "./overview.component";
@Component({
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [OverviewComponent],
  template: '<finance-overview [accountId]="id()" />',
})
export class AccountDetailComponent {
  private readonly params = toSignal(inject(ActivatedRoute).paramMap);
  readonly id = computed(() => Number(this.params()?.get("id") ?? 0));
}
