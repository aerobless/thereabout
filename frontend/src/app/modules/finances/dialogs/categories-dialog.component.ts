import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  OnInit,
  signal,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { firstValueFrom } from "rxjs";
import {
  FinanceContext,
  FinanceDialogs,
  FinanceAccount,
  FinanceAccountKind,
  FinanceCategory,
  FinanceTransaction,
  FinanceValuationPreview,
  assetKinds,
  loadResource,
  localNow,
  today,
  errorMessage,
} from "../shared/finance-ui";
@Component({
  selector: "finance-categories-dialog",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: "./categories-dialog.component.html",
  styleUrl: "./dialog.scss",
})
export class CategoriesDialogComponent {
  readonly context = inject(FinanceContext);
  readonly dialogs = inject(FinanceDialogs);
  private fb = inject(FormBuilder).nonNullable;
  get saving() {
    return this.context.saving();
  }
  get currencies() {
    return this.context.currencies();
  }

  readonly form = this.fb.group({
    id: [0],
    version: [0],
    name: ["", Validators.required],
  });
  get categories() {
    return this.context.categories();
  }
  reset() {
    this.form.reset({ id: 0, version: 0, name: "" });
  }
  editCategory(c: FinanceCategory) {
    this.form.patchValue(c);
  }
  async saveCategory() {
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    const result = await this.context.write(
      "categories.save",
      {
        name: v.name,
        id: v.id || undefined,
        version: v.id ? v.version : undefined,
      },
      (p) =>
        v.id
          ? this.context.api.client.financeUpdateCategories(v.id, p)
          : this.context.api.client.financeCreateCategories(p),
    );
    if (result) this.reset();
  }
}
