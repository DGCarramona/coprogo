# Backlog

Backlog derive de l'etat actuel du depot au 2026-04-03.

## Etat actuel constate

- Backend: coeur metier deja entame pour `expense`, `revenue` et `ledger`, avec des tests de domaine et d'application.
- Backend HTTP: seul le preview de distribution de revenus est expose aujourd'hui, plus l'OpenAPI.
- Backend infra: pas encore de migrations Flyway, pas d'adaptateurs R2DBC visibles, pas de stockage S3, pas de validation Google ID token orientee produit.
- Frontend: application Angular encore au stade bootstrap, sans routes fonctionnelles ni ecrans metier.
- Frontend API: client OpenAPI genere uniquement pour le preview de distribution de revenus, avec un faux token dans l'intercepteur.
- Monorepo: pas encore d'orchestration Gradle racine pour piloter ensemble le front et le back.

## Regles de decoupage

- Un item doit idealement tenir dans une petite PR reviewable.
- Chaque item livre son code, ses tests et, si besoin, la mise a jour OpenAPI.
- Un item backend n'est pas termine sans tests et verifications `./gradlew test` au minimum.
- Un item frontend n'est pas termine sans tests et verifications `npm run lint`, `npm run test`, `npm run build`.

## Fondations monorepo et DX

- [ ] MONO-001 Ajouter un `settings.gradle.kts` et un `build.gradle.kts` racine pour l'orchestration monorepo.
- [ ] MONO-002 Configurer le plugin Gradle `com.github.node-gradle.node` pour provisionner Node.js et npm cote frontend.
- [ ] MONO-003 Ajouter des taches Gradle racine pour installer les dependances frontend et backend en une seule entree (`frontendInstall`, `bootstrap` ou equivalent).
- [ ] MONO-004 Ajouter des taches Gradle racine pour lancer `lint`, `test` et `build` du frontend depuis Gradle.
- [ ] MONO-005 Ajouter des taches Gradle racine qui agregent les checks front et back (`checkAll`, `buildAll` ou equivalent).
- [ ] MONO-006 Ajouter une tache Gradle de dev qui demarre front et back ensemble, avec un mecanisme explicite pour les processus longs (`execfork` ou equivalent).
- [ ] MONO-007 Completer `docker-compose.yml` avec un service S3-compatible local (ex: MinIO).
- [ ] MONO-008 Documenter le workflow local monorepo et les nouvelles commandes Gradle racine dans un README racine.
- [ ] MONO-009 Ajouter une CI qui execute les checks front et back via les entrees Gradle racine.

## Backend - authentification, groupes et membres

- [ ] BE-AUTH-001 Introduire un adaptateur backend qui extrait l'utilisateur authentifie depuis Micronaut sans fuiter le framework dans le coeur applicatif.
- [ ] BE-AUTH-002 Remplacer la logique JWT generique par une validation de Google ID token conforme au besoin produit.
- [ ] BE-AUTH-003 Ajouter des tests d'integration pour les cas token valide, invalide et utilisateur inconnu du systeme.
- [ ] BE-GROUP-001 Modeliser les groupes et les membres du groupe dans le domaine.
- [ ] BE-GROUP-002 Ajouter les migrations Flyway pour groupes, membres et appartenances.
- [ ] BE-GROUP-003 Creer les repositories et adaptateurs R2DBC pour groupes et membres.
- [ ] BE-GROUP-004 Implementer le cas d'usage de creation de groupe.
- [ ] BE-GROUP-005 Implementer le cas d'usage d'invitation d'un membre par n'importe quel membre du groupe.
- [ ] BE-GROUP-006 Implementer le cas d'usage d'acceptation d'une invitation.
- [ ] BE-GROUP-007 Exposer les endpoints REST de creation de groupe, consultation du groupe et invitation.
- [ ] BE-GROUP-008 Enforcer explicitement la regle "seul le createur du groupe peut modifier les quotes-parts".

## Backend - quotes-parts et revenus

- [ ] BE-REV-001 Ajouter un adaptateur de persistance pour `OwnershipShareTimeline`.
- [ ] BE-REV-002 Exposer l'endpoint REST pour enregistrer un changement de quotes-parts.
- [ ] BE-REV-003 Exposer l'endpoint REST pour consulter l'historique des quotes-parts.
- [ ] BE-REV-004 Ajouter un controle d'autorisation sur le changement de quotes-parts.
- [ ] BE-REV-005 Implementer le cas d'usage d'enregistrement d'un revenu entrant dans la caisse commune.
- [ ] BE-REV-006 Persister les evenements de ledger lies aux revenus et distributions.
- [ ] BE-REV-007 Implementer le cas d'usage qui distribue un revenu selon les quotes-parts effectives a une date donnee.
- [ ] BE-REV-008 Exposer un endpoint REST pour previsualiser une distribution a partir de la timeline enregistree.
- [ ] BE-REV-009 Exposer un endpoint REST pour enregistrer une distribution effective de revenu.
- [ ] BE-REV-010 Ajouter des tests d'integration sur l'historisation des quotes-parts et le calcul a date d'effet.

## Backend - depenses

- [ ] BE-EXP-001 Ajouter les migrations Flyway pour `expenses` et `expense_participations`.
- [ ] BE-EXP-002 Creer l'adaptateur R2DBC de `ExpenseRepository`.
- [ ] BE-EXP-003 Exposer l'endpoint REST de proposition de depense avec partage egal.
- [ ] BE-EXP-004 Exposer l'endpoint REST d'enregistrement d'une decision de participation.
- [ ] BE-EXP-005 Exposer l'endpoint REST de detail d'une depense.
- [ ] BE-EXP-006 Exposer l'endpoint REST de liste des depenses d'un groupe.
- [ ] BE-EXP-007 Ajouter au domaine la variante "participation partielle" explicitement nommee dans l'API.
- [ ] BE-EXP-008 Ajouter au domaine la variante "participation capee".
- [ ] BE-EXP-009 Ajouter au domaine la variante "contribution maximale".
- [ ] BE-EXP-010 Ajouter au domaine la variante "montant de reference avec excedent restant au createur".
- [ ] BE-EXP-011 Adapter les commandes, DTOs et mappers REST a ces nouvelles variantes d'allocation.
- [ ] BE-EXP-012 Conserver une raison exploitable de refus/invalidation dans les vues de detail et d'historique.
- [ ] BE-EXP-013 Ajouter des tests d'integration couvrant acceptation, refus et emission d'evenement ledger.

## Backend - remboursements

- [ ] BE-REB-001 Introduire le modele de domaine pour un remboursement direct.
- [ ] BE-REB-002 Introduire le modele de domaine pour un remboursement declare par le debiteur avec justificatif contestable.
- [ ] BE-REB-003 Ajouter les migrations Flyway pour les remboursements et leur historique de decision.
- [ ] BE-REB-004 Creer les repositories et adaptateurs R2DBC des remboursements.
- [ ] BE-REB-005 Implementer le cas d'usage "Alice enregistre que Bob l'a rembourse" sans confirmation de Bob.
- [ ] BE-REB-006 Implementer le cas d'usage "Bob declare qu'il a rembourse Alice avec justificatif".
- [ ] BE-REB-007 Implementer le cas d'usage de rejet d'un remboursement conteste par le crediteur.
- [ ] BE-REB-008 Produire les effets ledger associes aux remboursements acceptes.
- [ ] BE-REB-009 Exposer les endpoints REST de creation, consultation et revue des remboursements.
- [ ] BE-REB-010 Ajouter des tests d'integration sur les parcours de remboursement directs et contestables.

## Backend - caisse commune, balances et historique explicatif

- [ ] BE-LED-001 Ajouter la persistance des `LedgerEvent` en base.
- [ ] BE-LED-002 Creer l'adaptateur R2DBC de `LedgerEventRepository`.
- [ ] BE-LED-003 Exposer l'endpoint REST de consultation des balances d'un groupe.
- [ ] BE-LED-004 Exposer l'endpoint REST de consultation du solde de la caisse commune.
- [ ] BE-LED-005 Exposer l'endpoint REST de consultation des parts de caisse commune par membre.
- [ ] BE-LED-006 Implementer le cas d'usage de retrait de la caisse commune avec verification de disponibilite.
- [ ] BE-LED-007 Implementer la compensation automatique quand un retrait depasse la part propre de revenu du membre.
- [ ] BE-LED-008 Exposer l'endpoint REST de retrait de caisse commune.
- [ ] BE-LED-009 Construire une requete applicative "qu'est-ce qui a reduit la dette d'un membre".
- [ ] BE-LED-010 Exposer l'endpoint REST d'historique unifie des reductions de dette.
- [ ] BE-LED-011 Ajouter des tests d'integration sur projections, retraits et historique unifie.

## Backend - justificatifs, audit et traceabilite

- [ ] BE-DOC-001 Introduire un port applicatif pour le stockage de justificatifs.
- [ ] BE-DOC-002 Ajouter un adaptateur S3-compatible pour l'upload et le download de documents.
- [ ] BE-DOC-003 Ajouter les migrations Flyway pour les metadonnees de documents et leur historique.
- [ ] BE-DOC-004 Implementer l'ajout d'un justificatif a une depense.
- [ ] BE-DOC-005 Implementer le remplacement d'un justificatif sans detruire l'ancien.
- [ ] BE-DOC-006 Implementer la suppression logique d'un justificatif avec audit.
- [ ] BE-DOC-007 Enforcer la regle "seul le createur de l'evenement peut supprimer son justificatif".
- [ ] BE-DOC-008 Exposer les endpoints REST d'upload, liste, consultation et suppression de justificatifs.
- [ ] BE-DOC-009 Ajouter une vue d'historique audit des actions sensibles.
- [ ] BE-DOC-010 Ajouter des tests d'integration sur la non-destruction de l'historique documentaire.

## Frontend - socle applicatif et authentification

- [ ] FE-CORE-001 Remplacer la page Angular par defaut par un shell d'application minimal.
- [ ] FE-CORE-002 Declarer les routes principales de l'application.
- [ ] FE-CORE-003 Introduire une couche `domain` frontend distincte des DTOs OpenAPI.
- [ ] FE-CORE-004 Introduire une couche `application` frontend avec cas d'usage et services par feature.
- [ ] FE-CORE-005 Mettre en place un service d'authentification base sur Signals.
- [ ] FE-CORE-006 Remplacer le faux token par une vraie source de Google ID token.
- [ ] FE-CORE-007 Ajouter une guard de route pour les ecrans authentifies.
- [ ] FE-CORE-008 Mettre en place la gestion uniforme loading / empty / error.
- [ ] FE-CORE-009 Ajouter des utilitaires de mapping argent / dates / pourcentages.

## Frontend - groupes, dashboard et lecture des soldes

- [ ] FE-DASH-001 Creer la page dashboard de groupe.
- [ ] FE-DASH-002 Creer le ViewModel de dashboard avec Signals et etat derive.
- [ ] FE-DASH-003 Afficher le resume "je dois / on me doit" par membre.
- [ ] FE-DASH-004 Afficher le solde disponible de la caisse commune.
- [ ] FE-DASH-005 Afficher les parts de caisse commune par membre.
- [ ] FE-DASH-006 Ajouter une vue explicative qui rattache les soldes aux evenements financiers.
- [ ] FE-DASH-007 Ajouter les tests de ViewModel et de rendu du dashboard.

## Frontend - depenses

- [ ] FE-EXP-001 Creer la page de liste des depenses d'un groupe.
- [ ] FE-EXP-002 Creer la page de detail d'une depense avec statuts de participation.
- [ ] FE-EXP-003 Creer le formulaire de depense a partage egal.
- [ ] FE-EXP-004 Creer le formulaire de depense a montant partiel.
- [ ] FE-EXP-005 Creer le formulaire de depense pour participations capees.
- [ ] FE-EXP-006 Creer le formulaire de depense pour contributions maximales.
- [ ] FE-EXP-007 Creer le formulaire de depense a montant de reference.
- [ ] FE-EXP-008 Ajouter l'action d'approbation ou refus de sa propre participation.
- [ ] FE-EXP-009 Afficher clairement l'invalidation d'une depense refusee et la necessite de ressaisie.
- [ ] FE-EXP-010 Ajouter les tests de ViewModel, mapping et composants critiques des depenses.

## Frontend - remboursements

- [ ] FE-REB-001 Creer la page d'historique des remboursements.
- [ ] FE-REB-002 Creer le formulaire de remboursement direct.
- [ ] FE-REB-003 Creer le formulaire de remboursement auto-declare avec justificatif.
- [ ] FE-REB-004 Creer l'ecran de revue d'un remboursement contestable.
- [ ] FE-REB-005 Afficher le statut "a revoir / refuse / accepte" des remboursements documentes.
- [ ] FE-REB-006 Ajouter les tests de ViewModel et de composants des remboursements.

## Frontend - revenus, quotes-parts et caisse commune

- [ ] FE-REV-001 Creer la page de timeline des quotes-parts.
- [ ] FE-REV-002 Creer le formulaire de changement de quotes-parts avec date d'effet.
- [ ] FE-REV-003 Creer la page de preview de distribution de revenus a partir de l'API existante.
- [ ] FE-REV-004 Creer le formulaire d'enregistrement d'un revenu entrant.
- [ ] FE-REV-005 Creer le formulaire de distribution effective d'un revenu.
- [ ] FE-REV-006 Creer le formulaire de retrait de la caisse commune.
- [ ] FE-REV-007 Afficher l'historique des revenus, distributions et retraits.
- [ ] FE-REV-008 Ajouter les tests de ViewModel et de composants pour quotes-parts et caisse commune.

## Frontend - justificatifs et historique audit

- [ ] FE-DOC-001 Creer un composant reutilisable d'upload de justificatif.
- [ ] FE-DOC-002 Creer un composant d'historique des versions d'un justificatif.
- [ ] FE-DOC-003 Ajouter l'action de telechargement et consultation d'un justificatif.
- [ ] FE-DOC-004 Ajouter l'action de suppression avec message d'audit clair.
- [ ] FE-DOC-005 Afficher, dans les ecrans depense et remboursement, l'historique des pieces liees.
- [ ] FE-DOC-006 Ajouter les tests de composants et de mapping pour la gestion documentaire.

## Tranche verticale minimale recommandee

Si on veut avancer avec des PRs simples et visibles rapidement, l'ordre recommande est:

1. `MONO-001` a `MONO-009`
2. `BE-GROUP-001` a `BE-GROUP-008`
3. `BE-EXP-001` a `BE-EXP-006`
4. `BE-LED-001` a `BE-LED-005`
5. `FE-CORE-001` a `FE-CORE-009`
6. `FE-DASH-001` a `FE-DASH-007`
7. `FE-EXP-001` a `FE-EXP-010`
8. Puis revenus, remboursements et justificatifs
