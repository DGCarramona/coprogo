import type { ExpenseSupportingDocument } from '../../../application/supporting-document/expense-supporting-documents.port';

export interface ExpenseSupportingDocumentHistory {
  readonly rootSourceUploadIntent: string;
  readonly versions: readonly [ExpenseSupportingDocument, ...ExpenseSupportingDocument[]];
}

export const reconstructSupportingDocumentHistoryChains = (
  history: readonly ExpenseSupportingDocument[],
): readonly ExpenseSupportingDocumentHistory[] => {
  const documentsBySource = new Map(
    history.map((document) => [document.sourceUploadIntent, document] as const),
  );
  if (documentsBySource.size !== history.length) {
    throw new Error('errors.documents.duplicateOrigin');
  }

  const replacementBySource = history.reduce((replacements, document) => {
    const source = document.replacesSourceUploadIntent;
    if (source === null) return replacements;
    if (replacements.has(source)) {
      throw new Error('errors.documents.multipleReplacements');
    }

    return replacements.set(source, document);
  }, new Map<string, ExpenseSupportingDocument>());

  history.forEach((document) => {
    const source = document.replacesSourceUploadIntent;
    if (source !== null && !documentsBySource.has(source)) {
      throw new Error(
        `Le justificatif ${document.sourceUploadIntent} remplace une version introuvable: ${source}.`,
      );
    }
  });

  const roots = history.filter((document) => document.replacesSourceUploadIntent === null);
  const chains = roots.map((root): ExpenseSupportingDocumentHistory => ({
    rootSourceUploadIntent: root.sourceUploadIntent,
    versions: chainFrom(root, replacementBySource),
  }));

  if (chains.flatMap((chain) => chain.versions).length !== history.length) {
    throw new Error('errors.documents.historyCycle');
  }

  return chains;
};

const chainFrom = (
  root: ExpenseSupportingDocument,
  replacementBySource: ReadonlyMap<string, ExpenseSupportingDocument>,
): readonly [ExpenseSupportingDocument, ...ExpenseSupportingDocument[]] => {
  const chain: [ExpenseSupportingDocument, ...ExpenseSupportingDocument[]] = [root];
  const seen = new Set([root.sourceUploadIntent]);
  let current = replacementBySource.get(root.sourceUploadIntent);

  while (current !== undefined) {
    if (seen.has(current.sourceUploadIntent)) {
      throw new Error('errors.documents.historyCycle');
    }

    chain.push(current);
    seen.add(current.sourceUploadIntent);
    current = replacementBySource.get(current.sourceUploadIntent);
  }

  return chain;
};
