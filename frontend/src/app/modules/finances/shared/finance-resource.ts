import { Signal } from "@angular/core";
import { toObservable, toSignal } from "@angular/core/rxjs-interop";
import { Observable, catchError, map, of, startWith, switchMap } from "rxjs";
import { HttpErrorResponse } from "@angular/common/http";
export interface LoadState<T> {
  data?: T;
  loading: boolean;
  error: string;
}
export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const body: unknown = error.error;
    if (
      typeof body === "object" &&
      body !== null &&
      "detail" in body &&
      typeof body.detail === "string"
    )
      return body.detail;
    return error.status === 0
      ? "The local finance server is unavailable."
      : `Request failed (${error.status}).`;
  }
  return error instanceof Error ? error.message : "Request failed.";
}
/** switchMap cancels obsolete requests; a failure does not terminate future reloads. */
export function loadResource<Q, T>(
  query: Signal<Q>,
  fetch: (query: Q) => Observable<T>,
): Signal<LoadState<T>> {
  return toSignal(
    toObservable(query).pipe(
      switchMap((q) =>
        fetch(q).pipe(
          map((data) => ({ data, loading: false, error: "" })),
          startWith({ loading: true, error: "" } as LoadState<T>),
          catchError((error) =>
            of({ loading: false, error: errorMessage(error) } as LoadState<T>),
          ),
        ),
      ),
    ),
    { initialValue: { loading: true, error: "" } },
  );
}
