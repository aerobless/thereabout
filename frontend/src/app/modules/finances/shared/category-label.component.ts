import {ChangeDetectionStrategy, Component, input} from '@angular/core';
import {categoryIcon} from './category-presentation';
@Component({selector:'finance-category', changeDetection:ChangeDetectionStrategy.OnPush,
  template:`<i [class]="icon(name())" aria-hidden="true"></i><span>{{name() || 'Uncategorized'}}</span>`,
  styles:`:host { display:inline-flex; align-items:center; gap:.4rem; } i { flex:none; }`
})
export class CategoryLabelComponent { readonly name=input<string | null>(); readonly icon=categoryIcon; }
