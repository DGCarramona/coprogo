import { GroupFinancialDashboard } from '../../domain/ledger/group-financial-dashboard';

export abstract class GroupFinancialDashboardPort {
  abstract get(groupId: string): Promise<GroupFinancialDashboard>;
}
