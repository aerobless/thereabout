import {Injectable, signal} from '@angular/core';
@Injectable()
export class IdentityNavigation { readonly group = signal(false); }
