import {
  ChangeDetectionStrategy,
  Component,
  forwardRef,
  input,
  output,
  signal,
} from "@angular/core";
import {
  ControlValueAccessor,
  FormsModule,
  NG_VALUE_ACCESSOR,
} from "@angular/forms";
import { DatePickerModule } from "primeng/datepicker";

/** Keep API dates as local ISO strings while using the application's date picker. */
@Component({
  selector: "finance-date-input",
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, DatePickerModule],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => FinanceDateInputComponent),
      multi: true,
    },
  ],
  template: `
    <p-datepicker
      [ngModel]="value()"
      (ngModelChange)="select($event)"
      [ngModelOptions]="{ standalone: true }"
      [ariaLabel]="ariaLabel()"
      [disabled]="disabled()"
      dateFormat="dd.mm.yy"
      appendTo="body"
      [showIcon]="true"
      iconDisplay="input"
      [showTime]="dateTime()"
      [showSeconds]="dateTime()"
      hourFormat="24"
      (onBlur)="touched()"
    />
  `,
  styles: [":host { display: inline-block; min-width: 0; }"],
})
export class FinanceDateInputComponent implements ControlValueAccessor {
  readonly ariaLabel = input("Date");
  readonly dateTime = input(false);
  readonly dateChange = output<string>();
  readonly value = signal<Date | null>(null);
  readonly disabled = signal(false);
  private changed: (value: string) => void = () => {};
  protected touched: () => void = () => {};

  writeValue(value: string | null): void {
    // Parsing components locally avoids UTC shifts for dates near midnight.
    const match = value?.match(
      /^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.(\d+))?)?)?$/,
    );
    if (!match) {
      this.value.set(null);
      return;
    }
    const [
      ,
      year,
      month,
      day,
      hour = "0",
      minute = "0",
      second = "0",
      fraction = "0",
    ] = match;
    this.value.set(
      new Date(
        +year,
        +month - 1,
        +day,
        +hour,
        +minute,
        +second,
        +fraction.padEnd(3, "0").slice(0, 3),
      ),
    );
  }
  registerOnChange(fn: (value: string) => void): void {
    this.changed = fn;
  }
  registerOnTouched(fn: () => void): void {
    this.touched = fn;
  }
  setDisabledState(disabled: boolean): void {
    this.disabled.set(disabled);
  }

  select(date: Date | null): void {
    this.value.set(date);
    const pad = (n: number) => String(n).padStart(2, "0");
    let value = "";
    if (date instanceof Date && !Number.isNaN(date.getTime())) {
      value = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
      if (this.dateTime()) {
        value += `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
        if (date.getMilliseconds())
          value += `.${String(date.getMilliseconds()).padStart(3, "0")}`;
      }
    }
    this.changed(value);
    this.dateChange.emit(value);
  }
}
