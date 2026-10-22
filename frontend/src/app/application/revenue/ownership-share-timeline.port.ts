import type { OwnershipShareTimeline } from '../../domain/revenue/ownership-share-timeline';

export abstract class OwnershipShareTimelinePort {
  abstract getByGroup(groupId: string): Promise<OwnershipShareTimeline>;
}
