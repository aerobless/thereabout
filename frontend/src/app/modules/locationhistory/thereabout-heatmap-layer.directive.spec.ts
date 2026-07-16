import {EventEmitter} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {GoogleMap} from '@angular/google-maps';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ThereaboutHeatmapLayerDirective} from './thereabout-heatmap-layer.directive';

const deckState = {
    heatmapProperties: [] as any[],
    overlays: [] as any[]
};

class FakeHeatmapLayer {
    constructor(readonly props: any) {
        deckState.heatmapProperties.push(props);
    }
}

class FakeGoogleMapsOverlay {
    readonly setMap = vi.fn();
    readonly setProps = vi.fn();
    readonly finalize = vi.fn();

    constructor() {
        deckState.overlays.push(this);
    }
}

class TestThereaboutHeatmapLayerDirective extends ThereaboutHeatmapLayerDirective {
    protected override async loadDeckGl(): Promise<any> {
        return {
            HeatmapLayer: FakeHeatmapLayer,
            GoogleMapsOverlay: FakeGoogleMapsOverlay
        };
    }
}

describe('ThereaboutHeatmapLayerDirective', () => {
    let mapInitialized: EventEmitter<google.maps.Map>;

    beforeEach(() => {
        deckState.heatmapProperties.length = 0;
        deckState.overlays.length = 0;
        mapInitialized = new EventEmitter<google.maps.Map>();
    });

    afterEach(() => {
        TestBed.resetTestingModule();
        vi.clearAllMocks();
    });

    it('attaches a heatmap to an already initialized map', async () => {
        const map = {} as google.maps.Map;
        const directive = createDirective(map);
        directive.data = [
            {lat: 47.37, lng: 8.54},
            {lat: 46.95, lng: 7.45}
        ];

        directive.ngOnInit();

        await vi.waitFor(() => expect(deckState.overlays).toHaveLength(1));
        const overlay = deckState.overlays[0];
        const heatmapProperties = deckState.heatmapProperties[0];
        expect(overlay.setMap).toHaveBeenCalledWith(map);
        expect(overlay.setProps).toHaveBeenCalledWith({layers: [expect.anything()]});
        expect(heatmapProperties.radiusPixels).toBe(8);
        expect(heatmapProperties.getPosition(directive.data[0])).toEqual([8.54, 47.37]);
        expect(heatmapProperties.getWeight(directive.data[0])).toBe(1);
    });

    it('waits for a map that has not been initialized yet', async () => {
        const map = {} as google.maps.Map;
        const directive = createDirective();
        directive.data = [{lat: 47.37, lng: 8.54}];

        directive.ngOnInit();
        expect(deckState.overlays).toHaveLength(0);

        mapInitialized.emit(map);

        await vi.waitFor(() => expect(deckState.overlays).toHaveLength(1));
        expect(deckState.overlays[0].setMap).toHaveBeenCalledWith(map);
    });

    it('redraws changed data and removes the layer when hidden', async () => {
        const directive = createDirective({} as google.maps.Map);
        directive.data = [{lat: 47.37, lng: 8.54}];
        directive.ngOnInit();
        await vi.waitFor(() => expect(deckState.overlays).toHaveLength(1));
        const overlay = deckState.overlays[0];

        directive.data = [{lat: 40.71, lng: -74.01}];
        directive.radiusPixels = 12;
        directive.ngOnChanges();

        const updatedProperties = deckState.heatmapProperties.at(-1);
        expect(updatedProperties.data).toEqual(directive.data);
        expect(updatedProperties.radiusPixels).toBe(12);
        expect(updatedProperties.getPosition(directive.data[0])).toEqual([-74.01, 40.71]);

        directive.visible = false;
        directive.ngOnChanges();

        expect(overlay.setProps).toHaveBeenLastCalledWith({layers: []});
    });

    it('detaches and finalizes the overlay when destroyed', async () => {
        const directive = createDirective({} as google.maps.Map);
        directive.ngOnInit();
        await vi.waitFor(() => expect(deckState.overlays).toHaveLength(1));
        const overlay = deckState.overlays[0];

        directive.ngOnDestroy();

        expect(overlay.setMap).toHaveBeenCalledWith(null);
        expect(overlay.finalize).toHaveBeenCalledOnce();
    });

    function createDirective(map?: google.maps.Map) {
        TestBed.configureTestingModule({
            providers: [{
                provide: GoogleMap,
                useValue: {googleMap: map, mapInitialized}
            }]
        });

        return TestBed.runInInjectionContext(() => new TestThereaboutHeatmapLayerDirective());
    }
});
