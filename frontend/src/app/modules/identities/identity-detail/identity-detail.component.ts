import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, RouterModule} from '@angular/router';
import {ButtonModule} from 'primeng/button';
import {CardModule} from 'primeng/card';
import {TableModule} from 'primeng/table';
import {ToolbarComponent} from '../../../shared/toolbar/toolbar.component';
import {Identity, IdentityService} from '../../../../../generated/backend-api/thereabout';

@Component({
    selector: 'app-identity-detail',
    imports: [
        RouterModule,
        ButtonModule,
        CardModule,
        TableModule,
        ToolbarComponent,
    ],
    templateUrl: './identity-detail.component.html',
    styleUrl: './identity-detail.component.scss'
})
export class IdentityDetailComponent implements OnInit {

    identity: Identity | null = null;

    constructor(
        private readonly route: ActivatedRoute,
        private readonly identityService: IdentityService,
    ) {}

    ngOnInit(): void {
        const id = Number(this.route.snapshot.paramMap.get('id'));
        this.identityService.getIdentities().subscribe(identities => {
            this.identity = identities.find(i => i.id === id) || null;
        });
    }
}
