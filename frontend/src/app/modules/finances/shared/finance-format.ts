import { FinanceAccountKind } from "../../../../../generated/backend-api/thereabout";
export function today(): string {
  return new Intl.DateTimeFormat("sv-SE", { timeZone: "Europe/Zurich" }).format(
    new Date(),
  );
}
export function localNow(): string {
  return new Intl.DateTimeFormat("sv-SE", {
    timeZone: "Europe/Zurich",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  })
    .format(new Date())
    .replace(" ", "T");
}
export const assetKinds: ReadonlyArray<readonly [FinanceAccountKind, string]> =
  [
    ["CASH", "Cash"],
    ["INVESTMENT", "Investments"],
    ["REAL_ESTATE", "Real estate"],
    ["OTHER_ASSET", "Valuables & other assets"],
  ];
export function kindLabel(kind: string): string {
  return (
    assetKinds.find((k) => k[0] === kind)?.[1] ??
    kind.charAt(0) + kind.slice(1).toLowerCase()
  );
}

/** Round for editing without converting precise decimal strings to binary floating point. */
export function moneyInput(
  value: string | null | undefined,
  places = 2,
): string {
  if (value == null || value === "") return "";
  const match = /^(-?)(\d+)(?:\.(\d+))?$/.exec(value);
  if (!match) return value;
  const fraction = (match[3] ?? "").padEnd(places + 1, "0");
  const scale = 10n ** BigInt(places);
  let minor =
    BigInt(match[2]) * scale + BigInt(fraction.slice(0, places) || "0");
  if (fraction[places] >= "5") minor += 1n;
  const digits = minor.toString().padStart(places + 1, "0");
  const sign = minor === 0n ? "" : match[1];
  return (
    sign +
    (places ? digits.slice(0, -places) + "." + digits.slice(-places) : digits)
  );
}

/** JavaScript dates support milliseconds; retain the original timestamp for untouched edits. */
export function dateTimeInput(value: string): string {
  return value
    .replace(" ", "T")
    .replace(/(\.\d{3})\d*$/, "$1")
    .replace(/\.0+$/, "");
}
