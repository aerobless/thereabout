import {fullName, splitName} from './identity-names';

describe('identity names', () => {
  it('renders complete people and group names without missing surname artifacts', () => {
    expect(fullName({firstName:'Theo',lastName:'Winter'})).toBe('Theo Winter');
    expect(fullName({firstName:'Family Group Chat',lastName:''})).toBe('Family Group Chat');
    expect(fullName({firstName:'Björk'})).toBe('Björk');
    expect(fullName(undefined)).toBe('');
  });
  it('splits only the first word and preserves compound surnames', () => {
    expect(splitName(' Anna  van der Meer ')).toEqual({firstName:'Anna',lastName:'van der Meer'});
  });
});
