import { Component } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { FormControl, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { FinanceDateInputComponent } from "./finance-date-input.component";

@Component({
  imports: [ReactiveFormsModule, FinanceDateInputComponent],
  template: `<finance-date-input
    [formControl]="date"
    [dateTime]="withTime"
    ariaLabel="Booking date"
  />`,
})
class DateForm {
  readonly date = new FormControl("2026-01-01", { nonNullable: true });
  withTime = false;
}

describe("Finance date input", () => {
  it("displays and edits local calendar dates without a UTC conversion", () => {
    const fixture = TestBed.createComponent(DateForm);
    fixture.detectChanges();
    const picker = fixture.debugElement.query(
      By.directive(FinanceDateInputComponent),
    ).componentInstance as FinanceDateInputComponent;
    expect(picker.value()?.getFullYear()).toBe(2026);
    expect(picker.value()?.getMonth()).toBe(0);
    expect(picker.value()?.getDate()).toBe(1);
    expect(picker.value()?.getHours()).toBe(0);
    expect(fixture.componentInstance.date.pristine).toBe(true);
    picker.select(new Date(2026, 8, 26));
    expect(fixture.componentInstance.date.value).toBe("2026-09-26");
    expect(fixture.componentInstance.date.dirty).toBe(true);
    picker.select(null);
    expect(fixture.componentInstance.date.value).toBe("");
  });

  it("retains seconds and milliseconds for a local booking time and supports disabled forms", () => {
    const fixture = TestBed.createComponent(DateForm);
    fixture.componentInstance.withTime = true;
    fixture.componentInstance.date.setValue("2026-09-26T00:12:34.123");
    fixture.detectChanges();
    const picker = fixture.debugElement.query(
      By.directive(FinanceDateInputComponent),
    ).componentInstance as FinanceDateInputComponent;
    expect(picker.value()?.getHours()).toBe(0);
    expect(picker.value()?.getMilliseconds()).toBe(123);
    expect(fixture.componentInstance.date.pristine).toBe(true);
    picker.select(new Date(2026, 8, 27, 0, 12, 34, 123));
    expect(fixture.componentInstance.date.value).toBe(
      "2026-09-27T00:12:34.123",
    );
    fixture.componentInstance.date.disable();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector("input").disabled).toBe(true);
  });
});
