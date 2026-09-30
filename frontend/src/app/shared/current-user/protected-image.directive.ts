import {Directive, ElementRef, effect, inject, input} from '@angular/core';
import {ProtectedImageCache} from './protected-image-cache.service';

@Directive({selector: 'img[protectedImage]'})
export class ProtectedImageDirective {
  readonly protectedImage = input<string | undefined>();
  private readonly element = inject<ElementRef<HTMLImageElement>>(ElementRef);
  private readonly images = inject(ProtectedImageCache);
  constructor() {
    effect(cleanup => {
      const url=this.protectedImage();
      const image=this.element.nativeElement;
      image.removeAttribute('src');
      if (!url) return;
      if (!url.startsWith('/backend/api/') && !url.startsWith('/api/finances/')) { image.src=url; return; }
      let blobUrl: string | undefined;
      const request=this.images.get(url).subscribe({
        next: blob => { blobUrl=URL.createObjectURL(blob); image.src=blobUrl; },
        error: () => image.dispatchEvent(new Event('error'))
      });
      cleanup(() => { request.unsubscribe(); if (blobUrl) URL.revokeObjectURL(blobUrl); image.removeAttribute('src'); });
    });
  }
}
