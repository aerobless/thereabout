import {Directive, inject, Input, OnChanges, OnDestroy, OnInit} from '@angular/core';
import {GoogleMap} from '@angular/google-maps';
import type {HeatmapLayer} from '@deck.gl/aggregation-layers';
import type {GoogleMapsOverlay} from '@deck.gl/google-maps';
import type {ScatterplotLayer} from '@deck.gl/layers';
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
    @Input() radiusPixels = 18;
    @Input() visible = true;

    private readonly googleMap = inject(GoogleMap);
    private mapInitializedSubscription: Subscription | undefined;
    private overlay: GoogleMapsOverlay | undefined;
    private heatmapLayerConstructor: typeof HeatmapLayer | undefined;
    private scatterplotLayerConstructor: typeof ScatterplotLayer | undefined;
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
        const {HeatmapLayer, ScatterplotLayer, GoogleMapsOverlay} = await this.loadDeckGl();

        if (this.destroyed) {
            return;
        }

        this.heatmapLayerConstructor = HeatmapLayer;
        this.scatterplotLayerConstructor = ScatterplotLayer;
        this.overlay = new GoogleMapsOverlay({layers: []});
        this.overlay.setMap(map);
        this.renderHeatmap();
    }

    protected async loadDeckGl(): Promise<{
        HeatmapLayer: typeof HeatmapLayer;
        ScatterplotLayer: typeof ScatterplotLayer;
        GoogleMapsOverlay: typeof GoogleMapsOverlay;
    }> {
        const [{HeatmapLayer}, {ScatterplotLayer}, {GoogleMapsOverlay}] = await Promise.all([
            import('@deck.gl/aggregation-layers'),
            import('@deck.gl/layers'),
            import('@deck.gl/google-maps')
        ]);

        return {HeatmapLayer, ScatterplotLayer, GoogleMapsOverlay};
    }

    private renderHeatmap() {
        if (!this.overlay || !this.heatmapLayerConstructor || !this.scatterplotLayerConstructor) {
            return;
        }

        const layers = this.visible && this.data.length > 0
            ? [
                // Keep occasional visits visible even when years of home/work data
                // dominate the heatmap's viewport-relative density scale.
                new this.scatterplotLayerConstructor<HeatmapPoint>({
                    id: 'thereabout-visited-locations',
                    data: this.data,
                    getPosition: point => [point.lng, point.lat],
                    radiusUnits: 'pixels',
                    getRadius: 3,
                    getFillColor: [255, 112, 0, 230],
                    stroked: true,
                    lineWidthUnits: 'pixels',
                    getLineWidth: 1,
                    getLineColor: [154, 38, 0, 230],
                    pickable: false
                }),
                new this.heatmapLayerConstructor<HeatmapPoint>({
                    id: 'thereabout-location-heatmap',
                    data: this.data,
                    getPosition: point => [point.lng, point.lat],
                    getWeight: () => 1,
                    radiusPixels: this.radiusPixels,
                    intensity: 2,
                    threshold: 0.01,
                    colorRange: [
                        [255, 140, 0],
                        [255, 100, 0],
                        [245, 55, 0],
                        [220, 20, 30],
                        [175, 0, 50],
                        [115, 0, 55]
                    ]
                })]
            : [];

        this.overlay.setProps({layers});
    }
}
