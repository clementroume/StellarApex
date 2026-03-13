import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ExporterService {
  private readonly http = inject(HttpClient);

  exportMuscles(): Observable<string> {
    return this.http.post((this.buildUrl(`/muscles`)), {}, {responseType: 'text' as const});
  }

  exportMovements(): Observable<string> {
    return this.http.post((this.buildUrl(`/movements`)), {}, {responseType: 'text' as const});
  }

  private buildUrl(path: string): string {
    return `${environment.trainingUrl}/export${path}`;
  }
}
