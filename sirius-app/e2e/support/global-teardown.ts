import {purgeE2eData} from './purge';

/** Leaves the local database clean at the end of a run. */
export default function globalTeardown(): void {
  purgeE2eData();
}
