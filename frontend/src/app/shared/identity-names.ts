/** User-only views use firstName directly; mixed identity lists use this full name. */
export function fullName(identity: {firstName?: string; lastName?: string} | null | undefined): string {
  return [identity?.firstName, identity?.lastName].map(name => name?.trim() ?? '').filter(Boolean).join(' ');
}

export function splitName(name: string): {firstName: string; lastName: string} {
  const [firstName = '', ...rest] = name.trim().split(/\s+/u);
  return {firstName, lastName: rest.join(' ')};
}
