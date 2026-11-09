# ADR-0011 — Un poste par bénévole et par soirée, plutôt qu'une inscription par soirée

- **Statut** : proposé
- **Date** : 2026-11-09

## Contexte

Jusqu'au lot 3, R2 limitait un bénévole à une inscription par soirée ([cahier des charges](../cahier-des-charges.md) §5). La base le garantissait : contrainte `UNIQUE (evening_id, volunteer_id)` en V4, devenue en V8 l'index unique partiel `registration_one_active_per_volunteer_and_evening` sur les inscriptions actives ([ADR-0008](0008-annulation-logique-et-anonymisation.md)). Une exception « sur décision d'un coordinateur » était prévue (§11 question 1), sans être livrée.

Le porteur du projet a décidé (prompt du lot 3) :

1. une même personne peut cumuler, sur une soirée, des postes **distincts** (passer la nuit, préparer le plat principal, préparer les accompagnements), mais pas deux fois le même ;
2. le comptage ne change pas : chaque « préparer et partager » compte une présence au dîner. Plat et accompagnements préparés et partagés par la même personne font donc deux présences (en pratique, un couple ; les coordinateurs vérifient) ;
3. « Récupérer le dîner et le partager » n'est proposé et accepté que pour un repas préparé chez soi qui attend encore sa récupération.

Le 9 novembre 2026, il a aussi décidé que « Récupérer » ne se cumule qu'avec le petit-déjeuner, et que la désinscription d'une préparation chez soi déjà récupérée est acceptée, avec une alerte aux coordinateurs.

Plusieurs parties du code supposaient une inscription par soirée :
- la désinscription désignait l'inscription par sa seule soirée (paramètre `soiree`, ADR-0009) ;
- le calcul des alertes retirait « l'inscription du bénévole » de la soirée ;
- la suppression du compte annulait une inscription par soirée.

## Options envisagées

1. **Garder une inscription par soirée, qui couvre plusieurs postes** : une ligne par bénévole et par soirée, avec ses postes en colonnes ou dans une table `registration_post`.
   - Avantages : l'index de R2 ne change pas ; `soiree` suffit toujours à désigner l'inscription.
   - Inconvénients : les cinq types d'inscription deviennent des combinaisons, et R1, R3 et R4 doivent compter des postes dans une table fille. Se désinscrire d'un seul poste modifie une ligne au lieu d'en annuler une, alors que l'historique de l'annulation logique (`cancelled_at`, ADR-0008) est par ligne. Le déplacement d'un seul poste par un coordinateur devient une opération à part. C'est une refonte du modèle à cinq semaines de l'ouverture.
2. **Plusieurs inscriptions par soirée, unicité par poste en base** : une ligne par inscription, comme aujourd'hui. L'index de V8 est remplacé par un index unique partiel sur `(evening_id, volunteer_id, COALESCE(dish_choice, type)) WHERE cancelled_at IS NULL` : le poste d'une inscription est son plat pour qui prépare, son type sinon. Les règles qui croisent deux postes restent vérifiées par le domaine sous le verrou de la soirée, comme R1 : « Récupérer » seulement avec le petit-déjeuner, et pas plus de récupérations que de repas préparés chez soi.
   - Avantages : le modèle ne change pas, et le comptage de R1 donne naturellement les deux présences de la décision 2. Une désinscription annule une ligne. La migration est triviale : le nouvel index est moins strict que l'ancien, donc toute base existante le respecte.
   - Inconvénients :
     - une désinscription doit désigner l'inscription par sa soirée **et** son choix (`soiree` + `choix`) ;
     - la suppression du compte et les alertes raisonnent par inscription ;
     - la base ne garantit que la partie « même poste » de R2, et le reste dépend du verrou.
3. **Plusieurs inscriptions par soirée, sans unicité en base** : supprimer l'index et s'en remettre au domaine sous le verrou.
   - Avantages : la migration la plus simple.
   - Inconvénients : on perd le filet de sécurité que la base apporte depuis la V4 ; un oubli du verrou dans un futur chemin d'écriture laisserait passer deux fois le même poste.

Variante de l'option 2 : une colonne générée `post` (`GENERATED ALWAYS AS (COALESCE(dish_choice, type)) STORED`) avec un index unique sur elle. Le poste serait visible dans les requêtes et les exports, mais l'information serait dupliquée, et l'entité JPA devrait ignorer une colonne en écriture. Un index sur expression suffit.

## Décision

Option 2, avec un index sur expression (`V14__one_post_per_volunteer_and_evening.sql`, index `registration_one_active_post_per_volunteer_and_evening`). Elle donne les règles voulues sans toucher au modèle ni au comptage, garde une garantie en base pour le cas le plus probable (le même poste deux fois, par un double envoi ou deux onglets), et sa migration passe sur toute base existante.

Mise en œuvre :
- **domaine** :
  - `Registration.ownPost()` reprend le découpage de l'index (le plat, la présence pour une récupération, la nuit, le petit-déjeuner) ;
  - `Registration.conflictsWith()` ajoute la règle de la récupération ;
  - `Evening.decide` répond `ALREADY_REGISTERED` (même poste), `NOT_CUMULABLE` (récupération avec un autre poste du dîner) ou `NOTHING_TO_PICK_UP` (R4), avant R1 ;
- **désinscription** : désignée par `soiree` + `choix`, uniques ensemble pour un bénévole. Sur ce point, ADR-0009 ne vaut plus, car `soiree` seul ne suffit plus ;
- **alertes** : une annulation (désinscription, annulation ou déplacement par un coordinateur, suppression du compte) retire du calcul la seule inscription annulée. Elle enregistre, s'il y a lieu, l'alerte « soirée devenue incomplète » et la nouvelle alerte « récupération sans repas » (`coordinator_alert.kind = 'PICKUP_WITHOUT_MEAL'`, la soirée seule).

## Conséquences

- L'exception « sur décision d'un coordinateur » de R2 est sans objet pour le cumul de postes (§11 question 1).
- Un bénévole peut avoir plusieurs inscriptions sur une même carte du planning. Chaque bouton « Me désinscrire » nomme alors son poste, et deux présences au dîner tenues par la même personne s'affichent « MarieD (2 places) ».
- La fiche journée du back-office signale un bénévole qui compte pour deux présences, pour que les coordinateurs vérifient qu'ils viennent à deux.
- « Récupérer » n'est plus proposé sans repas préparé chez soi à récupérer. Une récupération sans repas n'existe plus qu'après l'annulation ou le déplacement d'une préparation chez soi déjà récupérée. Elle est alertée, listée au tableau de bord et sur la fiche journée, et se rattache d'elle-même à la prochaine préparation chez soi de la soirée.
- À surveiller : les règles qui croisent deux postes ne sont pas garanties par la base. Tout nouveau chemin d'écriture d'une inscription doit passer par `RegistrationService`, sous le verrou de la soirée, comme R1 (CLAUDE.md).
