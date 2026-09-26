import { dateTimeInput, moneyInput } from "./finance-format";

describe("Money input presentation", () => {
  it("rounds half units consistently, including carry and negatives", () => {
    expect(moneyInput("1.995")).toBe("2.00");
    expect(moneyInput("-1.005")).toBe("-1.01");
    expect(moneyInput("-0.001")).toBe("0.00");
  });
  it("pads everyday money without the database scale", () => {
    expect(moneyInput("25.900000000000000000000000")).toBe("25.90");
    expect(moneyInput("-140.000000000000000000000000")).toBe("-140.00");
    expect(moneyInput("0")).toBe("0.00");
    expect(moneyInput("")).toBe("");
  });
  it("rounds sub-cent display values without floating point loss", () => {
    expect(moneyInput("10.000000000001")).toBe("10.00");
    expect(moneyInput("999999999999.990000000000")).toBe("999999999999.99");
  });
  it("respects currency precision including currencies with zero or three decimals", () => {
    expect(moneyInput("100.000", 0)).toBe("100");
    expect(moneyInput("1.20", 3)).toBe("1.200");
  });
});

it("formats database timestamps for native date controls", () => {
  expect(dateTimeInput("2026-08-29 12:00:00.000000")).toBe(
    "2026-08-29T12:00:00",
  );
  expect(dateTimeInput("2026-08-29 12:00:00.123456")).toBe(
    "2026-08-29T12:00:00.123",
  );
});
