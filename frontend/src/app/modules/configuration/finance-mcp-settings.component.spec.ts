import { TestBed } from "@angular/core/testing";
import { provideHttpClient } from "@angular/common/http";
import {
  HttpTestingController,
  provideHttpClientTesting,
} from "@angular/common/http/testing";
import { FinanceMcpSettingsComponent } from "./finance-mcp-settings.component";

describe("FinanceMcpSettingsComponent", () => {
  const endpoint = "http://localhost/api/finances/configuration/mcp-key";
  beforeEach(() =>
    TestBed.configureTestingModule({
      imports: [FinanceMcpSettingsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }),
  );

  it("loads only on focus, reveals the persisted key and clears it on blur", () => {
    const fixture = TestBed.createComponent(FinanceMcpSettingsComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    const input: HTMLInputElement =
      fixture.nativeElement.querySelector("input");
    expect(input.type).toBe("password");
    http.expectNone(endpoint);
    input.dispatchEvent(new Event("focus"));
    http.expectOne(endpoint).flush("test-credential-only");
    fixture.detectChanges();
    expect(input.type).toBe("text");
    expect(input.value).toBe("test-credential-only");
    input.dispatchEvent(new Event("blur"));
    fixture.detectChanges();
    expect(input.type).toBe("password");
    expect(fixture.componentInstance.key()).toBe("");
    expect(input.value).not.toContain("test-credential");
    http.verify();
  });

  it("cancels an outstanding reveal when the user leaves the field", () => {
    const fixture = TestBed.createComponent(FinanceMcpSettingsComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.componentInstance.reveal();
    const request = http.expectOne(endpoint);
    fixture.componentInstance.hide();
    expect(request.cancelled).toBe(true);
    expect(fixture.componentInstance.revealed()).toBe(false);
    expect(fixture.componentInstance.key()).toBe("");
    http.verify();
  });
});
