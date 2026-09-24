export * from './expenses.service';
import { ExpensesService } from './expenses.service';
export * from './groups.service';
import { GroupsService } from './groups.service';
export * from './ledger.service';
import { LedgerService } from './ledger.service';
export * from './revenue-distribution.service';
import { RevenueDistributionService } from './revenue-distribution.service';
export * from './supporting-document-uploads.service';
import { SupportingDocumentUploadsService } from './supporting-document-uploads.service';
export * from './supporting-documents.service';
import { SupportingDocumentsService } from './supporting-documents.service';
export const APIS = [
  ExpensesService,
  GroupsService,
  LedgerService,
  RevenueDistributionService,
  SupportingDocumentUploadsService,
  SupportingDocumentsService,
];
