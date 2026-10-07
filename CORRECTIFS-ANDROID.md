# LinkBridge Android — correctifs

Version du fichier : 7 octobre 2026. Côté téléphone uniquement : le module `core` et
l'application PC n'ont pas été touchés.

---

## 1. L'invitation « Invitation à se connecter » qui revient, et l'acceptation qui ne fait rien

Cinq défauts réels se cumulaient. Ils sont tous corrigés.

| Ce qui se passait | Cause exacte | Correction |
|---|---|---|
| Accepter la demande ne déclenche rien | Le gestionnaire de connexion ne réagissait que si l'utilisateur avait déjà choisi Partager ou Recevoir dans l'application. Une invitation acceptée depuis la barre de notifications arrivait donc dans une impasse | Le rôle est déduit du sens réel du lien : propriétaire du groupe = partage, client = réception. L'écran s'ouvre seul sur la bonne face |
| L'acceptation ne relie toujours rien après | Le code à six chiffres manquait, et le tunnel refusait de démarrer sans rien dire | Le lien reste en attente et le tunnel part tout seul à la sixième chiffre saisie |
| La demande revient sans fin | Une nouvelle invitation pouvait être envoyée alors qu'une précédente était encore en attente : les demandes s'empilaient | Une seule invitation à la fois. Un second appui ne renvoie rien, il explique |
| Elle revient aussi après un échec | La recherche des appareils restait active, ce qui fait échouer `connect()` avec un code d'occupation, puis le système relance | La recherche est arrêtée avant toute connexion, et l'invitation en attente est annulée avant chaque tentative |
| Après une session précédente | Android garde des groupes persistants et les réveille tout seul | Non supprimables depuis une application : l'API `requestPersistentGroupInfo` n'est pas exposée dans le SDK public. Le code qui prétendait le faire a été retiré et le guide explique la vraie solution : oublier le réseau `DIRECT-…` dans les réglages Wi‑Fi |

Un surveillant de trente secondes prévient maintenant l'utilisateur si une demande n'aboutit
pas, au lieu de laisser un écran muet.

## 2. Position approximative manquante — bloquant sur Android 12 et avant

`ACCESS_FINE_LOCATION` était déclarée sans `ACCESS_COARSE_LOCATION`. Depuis Android 12, une
demande de position précise qui ne réclame pas aussi la position approximative est refusée
d'office par le système. Sur un téléphone en Android 12 — le cas de nombreux Galaxy Note10+ —
Wi‑Fi Direct ne pouvait donc jamais démarrer. Les deux autorisations sont maintenant
déclarées et demandées ensemble, et le guide rappelle que la localisation doit être active
sur ces versions.

## 3. Appels Wi‑Fi Direct sans contrôle d'autorisation

Les appels au système — création de groupe, recherche, connexion, liste des pairs — étaient
faits sans vérifier l'autorisation. Un refus de l'utilisateur faisait lever une exception de
sécurité, donc tomber le processus en pleine session. Chaque point d'entrée vérifie
désormais l'autorisation et prévient par un message, et le récepteur d'événements est
protégé : une autorisation retirée en cours de session n'arrête plus l'application.

## 4. Connexion terminée mais notification toujours au vert

Quand l'application était fermée, plus personne ne suivait l'état du lien : le partage ou le
tunnel continuait de tourner, notification verte, pour une connexion morte. Les deux services
surveillent maintenant le lien eux-mêmes, s'arrêtent proprement et affichent « connexion
terminée ». La surveillance ne s'active que pour une session née d'un Wi‑Fi Direct : le
relais du réseau local, utilisé depuis un PC, n'est jamais interrompu par un événement sans
rapport.

## 5. Autres erreurs supprimées

- Le tunnel VPN était relancé à chaque envoi de commande identique, ce qui coupait la
  connexion de toutes les applications en cours. Une signature de session empêche ce
  redémarrage.
- L'échec du tunnel était silencieux : aucune application utilisable, ou interface refusée par
  Android, et l'utilisateur ne voyait rien. Une notification explique désormais la cause.
- Les applications désinstallées depuis le dernier choix faisaient échouer la préparation du
  tunnel. Elles sont retirées de la liste avant préparation.
- La liste des applications n'était plus un obstacle : pendant son chargement, le choix ne
  bloque plus la connexion, et le service ignore de lui-même un paquet disparu.
- Sur Android 13 et plus, l'autorisation d'afficher les notifications est demandée : sans
  elle, la notification du partage restait invisible alors que le service tournait.
- Une couleur hors palette `#F0ECF7` traînait dans la carte de confiance : remplacée par le
  lavande de la charte.
- Erreurs de compilation corrigées : rôle `LinkRole` déclaré deux fois, thème du guide
  dupliqué, marque de l'application privée donc inaccessible depuis la page du guide.

## 6. Le guide n'est plus une fenêtre de dialogue

La fenêtre géante est supprimée. Le guide est une page complète, avec sa barre du haut, sa
flèche de retour, son bouton bas et six sections : ce qu'il faut savoir, le téléphone qui
partage, le téléphone qui reçoit, si ça ne marche pas, depuis un ordinateur, signature. Deux
ajouts répondent directement aux pannes rencontrées : accepter une demande arrivée dans la
barre de notifications suffit, et sur Android 12 et avant la localisation doit être active.

## 7. Fluidité de la barre du haut

La barre est celle de Material 3 avec effondrement au défilement : elle se replie en douceur,
le contenu glisse dessous sans saccade, et le statut change par un fondu plutôt que par un
saut. Les blocs apparaissent et disparaissent en glissant.

## 8. Arrière-plan

Le partage comme la réception tournent dans des services de premier plan qui survivent à la
fermeture de l'application et au balayage de la tâche récente. La session en cours est
retrouvée à la réouverture, avec le bon code et le bon écran. Elle ne s'arrête que par le
bouton Arrêter, par l'action de la notification, ou quand le lien disparaît.

---

## Ce qui a été vérifié ici, et ce qui reste à vérifier sur les téléphones

Vérifié dans cet environnement, sur le code réel :

- compilation Kotlin complète de l'application, puis assemblage de l'APK ;
- analyse lint : plus aucune erreur ;
- tests du module `core` : verts, réseau et application PC inchangés ;
- contenu de l'APK relu : les deux activités, les deux services avec leurs types de premier
  plan, les permissions, les classes nouvelles, et la bibliothèque native dans les quatre
  architectures ;
- palette et textes relus : aucune parenthèse dans un texte affiché, aucune couleur hors
  charte.

Reste à vérifier sur un vrai téléphone, et c'est la seule chose qui ne peut pas être testée
depuis un environnement sans appareil : la formation du lien Wi‑Fi Direct entre les deux
téléphones, et l'acceptation de l'invitation depuis la barre de notifications.

---

Metoushela Walker · <https://github.com/MeganAe/linkbridge> · licence MIT
