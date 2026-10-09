import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {RouterLink, RouterOutlet} from '@angular/router';
import {IdentityNavigation} from './identity-navigation';
@Component({
  imports:[RouterLink,RouterOutlet], providers:[IdentityNavigation], changeDetection:ChangeDetectionStrategy.OnPush,
  template:`<div class="configuration-page"><header class="configuration-heading"><h1>Identities</h1></header>
    <nav class="configuration-tabs" aria-label="Identity navigation">
      <a routerLink="/identities" [class.active]="!navigation.group()" [attr.aria-current]="!navigation.group() ? 'page' : null">Identities</a>
      <a routerLink="/identities/groups" [class.active]="navigation.group()" [attr.aria-current]="navigation.group() ? 'page' : null">Groups</a>
    </nav><router-outlet /></div>`,
  styleUrl:'../configuration/configuration.component.scss'
})
export class IdentitiesShellComponent { readonly navigation = inject(IdentityNavigation); }
