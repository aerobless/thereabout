/** Keep a retry's receipt key until its payload changes, including after a lost response. */
export class IdentityWriteKeys {
  private readonly pending = new Map<string, {fingerprint: string; key: string}>();
  key(operation: string, input: unknown): string {
    const fingerprint = JSON.stringify(input);
    const previous = this.pending.get(operation);
    if (previous?.fingerprint === fingerprint) return previous.key;
    const key = crypto.randomUUID();
    this.pending.set(operation, {fingerprint, key});
    return key;
  }
}
