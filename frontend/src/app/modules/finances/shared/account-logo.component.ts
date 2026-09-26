import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  signal,
} from "@angular/core";
import { FinanceAccount } from "../../../../../generated/backend-api/thereabout";
@Component({
  selector: "finance-account-logo",
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `@if (imageUrl() && failedUrl() !== imageUrl()) {
      <img
        [src]="imageUrl()"
        alt=""
        referrerpolicy="no-referrer"
        (error)="failedUrl.set(imageUrl())"
      />
    } @else {
      <i
        [class]="
          account().kind === 'CASH'
            ? 'pi pi-wallet'
            : account().kind === 'REAL_ESTATE'
              ? 'pi pi-home'
              : 'pi pi-chart-line'
        "
        aria-hidden="true"
      ></i>
    }`,
  styles: [
    `
      :host {
        display: grid;
        place-items: center;
        width: 46px;
        height: 46px;
        flex: 0 0 46px;
        border-radius: 12px;
        background: var(--app-surface-muted, #f1f5fa);
        color: var(--p-primary-600);
        overflow: hidden;
      }
      img {
        width: 100%;
        height: 100%;
        object-fit: contain;
        padding: 5px;
      }
    `,
  ],
})
export class AccountLogoComponent {
  readonly account = input.required<FinanceAccount>();
  readonly imageUrl = computed(() => {
    const account = this.account();
    return account.websiteUrl
      ? `/api/finances/accounts/${account.id}/icon?v=${account.version}`
      : account.logoUrl;
  });
  readonly failedUrl = signal<string | undefined>(undefined);
}
