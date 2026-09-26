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
