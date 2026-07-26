import { ExpenseResponseDto } from '../../infrastructure/api/generated';

export abstract class ExpenseListPort {
  abstract listByGroup(groupId: string): Promise<ExpenseResponseDto[]>;
}
