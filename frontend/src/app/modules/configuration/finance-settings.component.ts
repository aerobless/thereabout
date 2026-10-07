import {ChangeDetectionStrategy, Component, inject, viewChild} from '@angular/core';
import {CardModule} from 'primeng/card';
import {FinanceApi, FinanceContext} from '../finances/shared/finance-ui';
import {CategoriesDialogComponent} from '../finances/dialogs/categories-dialog.component';
import {RatesDialogComponent} from '../finances/dialogs/rates-dialog.component';
import {ConfigurationEditor} from './configuration-navigation';

@Component({
  selector: 'app-finance-settings',
  imports: [CardModule, CategoriesDialogComponent, RatesDialogComponent],
  providers: [FinanceApi, FinanceContext],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './configuration.component.scss',
  template: `<div class="configuration-container">
    @if (context.error() || context.metadata().error) { <p role="alert">{{context.error() || context.metadata().error}}</p> }
    <p-card><ng-template #header><div class="card-header"><i class="pi pi-tags card-header-icon" aria-hidden="true"></i><span class="card-header-title" id="finance-categories" tabindex="-1">Categories</span></div></ng-template><finance-categories-dialog /></p-card>
    <p-card><ng-template #header><div class="card-header"><i class="pi pi-globe card-header-icon" aria-hidden="true"></i><span class="card-header-title" id="finance-rates" tabindex="-1">Exchange rates</span></div></ng-template><finance-rates-dialog /></p-card>
  </div>`
})
export class FinanceSettingsComponent implements ConfigurationEditor {
  readonly context = inject(FinanceContext);
  private readonly categories = viewChild(CategoriesDialogComponent);
  private readonly rates = viewChild(RatesDialogComponent);
  hasUnsavedChanges(): boolean { return !!(this.categories()?.form.dirty || this.rates()?.form.dirty); }
  isNavigationBlocked(): boolean { return this.context.saving(); }
}
