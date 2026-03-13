export type GymStatus = 'PENDING_APPROVAL' | 'ACTIVE' | 'REJECTED' | 'SUSPENDED';

export interface GymRequest {
  name: string;
  description?: string;
  isProgramming: boolean;
  creationToken: string;
}

export interface GymResponse {
  id: number;
  name: string;
  description?: string;
  isProgramming: boolean;
  isAutoSubscription: boolean;
  status: GymStatus;
  createdAt: string;
}

export interface GymSettingsRequest {
  enrollmentCode: string;
  isAutoSubscription: boolean;
}

export interface JoinGymRequest {
  gymId: number;
  enrollmentCode: string;
}

