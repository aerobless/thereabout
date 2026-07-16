import {Directive, inject, Input, OnChanges, OnDestroy, OnInit} from '@angular/core';
import {GoogleMap} from '@angular/google-maps';
import type {HeatmapLayer} from '@deck.gl/aggregation-layers';
import type {GoogleMapsOverlay} from '@deck.gl/google-maps';
import {Subscription, take} from 'rxjs';

export interface HeatmapPoint {
    lat: number;
    lng: number;
}

@Directive({
    selector: 'thereabout-heatmap-layer',
    standalone: true
})
export class ThereaboutHeatmapLayerDirective implements OnInit, OnChanges, OnDestroy {
    @Input() data: ReadonlyArray<HeatmapPoint> = [];
    @Input() radiusPixels = 8;
    @Input() visible = true;

    private readonly googleMap = inject(GoogleMap);
    private mapInitializedSubscription: Subscription | undefined;
    private overlay: GoogleMapsOverlay | undefined;
    private heatmapLayerConstructor: typeof HeatmapLayer | undefined;
    private destroyed = false;

    ngOnInit() {
        if (this.googleMap.googleMap) {
            void this.initializeOverlay(this.googleMap.googleMap);
            return;
        }

        this.mapInitializedSubscription = this.googleMap.mapInitialized
            .pipe(take(1))
            .subscribe(map => void this.initializeOverlay(map));
    }

    ngOnChanges() {
        this.renderHeatmap();
    }

    ngOnDestroy() {
        this.destroyed = true;
        this.mapInitializedSubscription?.unsubscribe();
        this.overlay?.setMap(null);
        this.overlay?.finalize();
        this.overlay = undefined;
    }

    private async initializeOverlay(map: google.maps.Map) {
        const {HeatmapLayer, GoogleMapsOverlay} = await this.loadDeckGl();

        if (this.destroyed) {
            return;
        }

        this.heatmapLayerConstructor = HeatmapLayer;
        this.overlay = new GoogleMapsOverlay({layers: []});
        this.overlay.setMap(map);
        this.renderHeatmap();
    }

    protected async loadDeckGl(): Promise<{
        HeatmapLayer: typeof HeatmapLayer;
        GoogleMapsOverlay: typeof GoogleMapsOverlay;
    }> {
        const [{HeatmapLayer}, {GoogleMapsOverlay}] = await Promise.all([
            import('@deck.gl/aggregation-layers'),
            import('@deck.gl/google-maps')
        ]);

        return {HeatmapLayer, GoogleMapsOverlay};
    }

    private renderHeatmap() {
        if (!this.overlay || !this.heatmapLayerConstructor) {
            return;
        }

        const layers = this.visible && this.data.length > 0
            ? [new this.heatmapLayerConstructor<HeatmapPoint>({
                id: 'thereabout-location-heatmap',
                data: this.data,
                getPosition: point => [point.lng, point.lat],
                getWeight: () => 1,
                radiusPixels: this.radiusPixels
            })]
            : [];

        this.overlay.setProps({layers});
    }
}
