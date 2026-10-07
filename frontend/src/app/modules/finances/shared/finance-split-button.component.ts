import { ChangeDetectionStrategy, Component, ViewEncapsulation, input, output } from '@angular/core';
import { MenuItem } from 'primeng/api';
import { SplitButtonModule } from 'primeng/splitbutton';

@Component({
  selector: 'finance-split-button',
  imports: [SplitButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
  template: `<p-splitbutton class="finance-split-button" [severity]="primary() ? 'primary' : 'secondary'"
    [label]="label()" [icon]="icon()" [model]="items()" (onClick)="action.emit()"
    [expandAriaLabel]="label() + ' options'" appendTo="body" menuStyleClass="finance-action-menu" />`,
  styleUrl: './finance-split-button.component.scss',
})
export class FinanceSplitButtonComponent {
  readonly label = input.required<string>();
  readonly icon = input<string>();
  readonly items = input<MenuItem[]>([]);
  readonly primary = input(false);
  readonly action = output<void>();
}
