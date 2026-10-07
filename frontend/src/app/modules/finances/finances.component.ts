import { FinanceSplitButtonComponent } from './shared/finance-split-button.component';
import { ChangeDetectionStrategy, Component, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { RouterLink, RouterLinkActive, RouterOutlet } from "@angular/router";
import {
  FinanceApi,
  FinanceContext,
  FinanceDialogs,
} from "./shared/finance-ui";
import { DialogHostComponent } from "./dialogs/dialog-host.component";
@Component({
  selector: "app-finances",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FinanceSplitButtonComponent,
    CommonModule,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    DialogHostComponent,
  ],
  providers: [FinanceApi, FinanceContext, FinanceDialogs],
  templateUrl: "./finances.component.html",
  styleUrl: "./finances.component.scss",
})
export class FinancesComponent {
  readonly context = inject(FinanceContext);
  readonly dialogs = inject(FinanceDialogs);
  readonly transactionActions = [{label: 'Import transactions from CSV', icon: 'pi pi-upload',
    command: () => this.dialogs.open({kind: 'import'})}];
}
