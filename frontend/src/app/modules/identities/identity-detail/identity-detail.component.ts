import {registerRefresh} from '../../../shared/refresh/refresh-coordinator';
import {Component, OnInit, ChangeDetectionStrategy} from '@angular/core';
import {ActivatedRoute, RouterModule} from '@angular/router';
import {ButtonModule} from 'primeng/button';
import {CardModule} from 'primeng/card';
import {TableModule} from 'primeng/table';
import {Identity, IdentityService} from '../../../../../generated/backend-api/thereabout';

@Component({
    selector: 'app-identity-detail',
    imports: [
        RouterModule,
        ButtonModule,
        CardModule,
        TableModule,
    ],
    templateUrl: './identity-detail.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './identity-detail.component.scss'
})
export class IdentityDetailComponent implements OnInit {
  private readonly refresh = registerRefresh(() => this.loadIdentity());

    identity: Identity | null = null;

    constructor(
        private readonly route: ActivatedRoute,
        private readonly identityService: IdentityService,
    ) {}

    ngOnInit(): void { this.loadIdentity(); }

    private loadIdentity() {
        const id = Number(this.route.snapshot.paramMap.get('id'));
        this.identityService.getIdentities().pipe(this.refresh.track('identity')).subscribe({next: identities => {
            this.identity = identities.find(i => i.id === id) || null;
        }, error: () => {}});
    }
}
