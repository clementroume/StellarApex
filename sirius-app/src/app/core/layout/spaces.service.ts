import {computed, inject, Injectable} from '@angular/core';
import {NavigationEnd, Router} from '@angular/router';
import {toSignal} from '@angular/core/rxjs-interop';
import {filter, map} from 'rxjs';
import {AccessService} from '../services/access.service';

export interface NavLink {
  link: string;
  label: string;
  icon: string;
}

export interface NavSpace extends NavLink {
  key: 'today' | 'training' | 'box' | 'catalog' | 'admin';
  /** URL prefixes belonging to the space (catalogue screens keep their historical URLs). */
  paths: string[];
  /** Sub-navigation shown as tabs inside the space. */
  children: NavLink[];
}

/**
 * Navigation by spaces (D39): Today · My training · My box · Catalogue · Admin. Which spaces and
 * staff entries show comes from AccessService only; the backends still decide every right.
 */
@Injectable({providedIn: 'root'})
export class SpacesService {
  // --- INJECTIONS ---
  private readonly accessService = inject(AccessService);
  private readonly router = inject(Router);

  // --- STATE (SIGNALS & VARIABLES) ---
  private readonly url = toSignal(
    this.router.events.pipe(filter(e => e instanceof NavigationEnd), map(() => this.router.url)),
    {initialValue: this.router.url}
  );

  // --- COMPUTED STATE ---
  readonly spaces = computed<NavSpace[]>(() => {
    const spaces: NavSpace[] = [
      {key: 'today', link: '/today', label: 'NAVBAR.SPACES.TODAY', icon: 'heroHome', paths: ['/today'], children: []},
      {
        key: 'training', link: '/training', label: 'NAVBAR.SPACES.TRAINING', icon: 'hugeDumbbell01', paths: ['/training'],
        children: [
          {link: '/training/journal', label: 'TRAINING.JOURNAL.TITLE', icon: 'heroClipboardDocumentList'},
          {link: '/training/records', label: 'TRAINING.RECORDS.TITLE', icon: 'heroTrophy'},
          {link: '/training/review', label: 'TRAINING.REVIEW.TITLE', icon: 'heroArrowTrendingUp'},
        ]
      },
    ];
    if (this.accessService.memberships().length > 0) {
      spaces.push({
        key: 'box', link: '/box', label: 'NAVBAR.SPACES.BOX', icon: 'heroBuildingOffice', paths: ['/box'],
        children: [
          {link: '/box/sessions', label: 'BOX.SESSIONS.TITLE', icon: 'heroCalendarDays'},
          {link: '/box/leaderboards', label: 'BOX.LEADERBOARDS.TITLE', icon: 'heroChartBar'},
          ...(this.accessService.gymsWithPermission('MANAGE_MEMBERSHIPS').length > 0
            ? [{link: '/box/members', label: 'BOX.MEMBERS.TITLE', icon: 'heroUsers'}] : []),
          ...(this.accessService.gymsWithPermission('MANAGE_SETTINGS').length > 0
            ? [{link: '/box/settings', label: 'BOX.SETTINGS.TITLE', icon: 'heroAdjustmentsHorizontal'}] : []),
        ]
      });
    }
    spaces.push({
      key: 'catalog', link: '/wods', label: 'NAVBAR.SPACES.CATALOG', icon: 'heroBookOpen', paths: ['/wods', '/movements', '/muscles'],
      children: [
        {link: '/wods', label: 'NAVBAR.WODS', icon: 'hugeKettlebell'},
        {link: '/movements', label: 'NAVBAR.MOVEMENTS', icon: 'hugeWorkoutStretching'},
        {link: '/muscles', label: 'NAVBAR.MUSCLES', icon: 'hugeBodyPartMuscle'},
      ]
    });
    if (this.accessService.isAdmin()) {
      spaces.push({
        key: 'admin', link: '/admin/gyms', label: 'NAVBAR.SPACES.ADMIN', icon: 'heroLifebuoy', paths: ['/admin'],
        children: [
          {link: '/admin/gyms', label: 'ADMIN.GYMS.TITLE', icon: 'heroBuildingOffice'},
          {link: '/admin/support', label: 'ADMIN.SUPPORT.TITLE', icon: 'heroLifebuoy'},
        ]
      });
    }
    return spaces;
  });

  /** Space of the current URL, if any (account pages belong to none). */
  readonly current = computed(() => {
    const path = this.url().split(/[?#]/)[0];
    return this.spaces().find(s => s.paths.some(p => path === p || path.startsWith(p + '/'))) ?? null;
  });
}
