# LinkBridge Android — notes de version

Côté téléphone uniquement. Le module `core` et l'application PC n'ont pas été touchés.

---

## 0.5.1 — 12 octobre 2026

### La demande d'activation n'arrive plus sans raison

La fenêtre qui demande d'activer le Wi‑Fi et la localisation surgissait à l'ouverture de
l'application et à chaque retour au premier plan, sans que personne n'ait rien demandé. Elle
n'apparaît plus que sur une action volontaire : **un appui sur Partager ou sur Recevoir**, et
à aucun autre moment. La notification qui l'accompagne suit la même règle.

- Tant que l'utilisateur ne touche ni Partager ni Recevoir, l'application ne réclame rien.
- « Plus tard » retire la notification au lieu de la laisser relancer sans cesse.
- Si l'utilisateur va activer le Wi‑Fi dans les réglages Android, **l'action qu'il voulait
  faire reprend toute seule** en revenant : plus besoin de tout recommencer.
- Le message rappelle que le partage de connexion et le point d'accès d'Android ne servent à
  rien ici, puisque seul le Wi‑Fi est utile.

---

## 0.5.0 — 12 octobre 2026

### La connexion se vérifie enfin

- **Vérifier la connexion**, sur l'écran de réception : LinkBridge se connecte au relais du
  téléphone A, s'authentifie avec le code, puis demande une page minuscule à travers lui.
  Jusqu'ici « connecté » voulait seulement dire « le lien Wi‑Fi Direct existe » — l'écran
  pouvait annoncer une réussite parfaite sans que rien ne passe. Le test emploie le client
  SOCKS5 du module core, celui qui sert déjà au bouton Tester de la version PC.
- L'adresse du relais et le code sont retenus avec la session : le test reste utilisable
  après une fermeture de l'application.

### Le partage sans Internet est annoncé

- Un téléphone qui partage sans données mobiles créait un lien impeccable et rien de plus.
  L'application vérifie maintenant, à chaque retour au premier plan et avant chaque partage,
  qu'un réseau donne réellement accès à Internet. Sinon : un message dans le statut, un
  avertissement sur la carte du partage, une notification, et un bouton Revérifier.
- La vérification regarde tous les réseaux, pas seulement le réseau actif : pendant un
  partage, le réseau actif est le groupe Wi‑Fi Direct, qui n'a évidemment pas Internet.

### Le choix des applications devient une vraie page

- La fenêtre de 320 dp est remplacée par une page entière, ouverte depuis l'écran principal :
  la liste occupe tout l'écran, **chaque application arrive avec son icône**, la recherche
  reste visible, et deux boutons permettent de tout cocher ou de tout décocher.
- Le choix est écrit à chaque appui : revenir en arrière ne fait rien perdre.

### Notifications classées

- Deux canaux au lieu d'un seul : **informations** (connexion établie, fin de session) en
  silence, **problèmes** (connexion impossible, demande non aboutie, réglages à activer) avec
  son. Auparavant, une connexion réussie faisait exactement le même bruit qu'un échec.

### Économie de batterie

- Un rappel apparaît pendant un partage quand Android limite encore LinkBridge, avec un bouton
  qui ouvre directement le bon réglage. Le guide a le même bouton. C'est la cause des partages
  qui « tiennent dix minutes puis lâchent », surtout écran éteint.

### Construction et publication

- **Signature stable** : le certificat de développement est livré avec le projet. Chaque
  construction portait auparavant une signature différente, et Android refusait d'installer
  la nouvelle version par-dessus l'ancienne.
- **APK publié automatiquement** : à chaque étiquette `vX.Y.Z`, l'APK est attaché à une
  release GitHub, avec un lien de téléchargement permanent. Les poussées ordinaires
  produisent un artifact de trente jours.

---

## 0.4.0 — 11 octobre 2026

- **Retour = accueil.** Sur l'écran Partager ou Recevoir, le retour ramène à l'accueil et
  coupe la recherche. Si un partage est actif, le retour met l'application en arrière-plan
  sans le couper. Depuis le guide, la flèche, le bouton et le geste de retour ramènent à l'accueil.
- **Nom dans la barre du haut**, à côté du logo.
- **Vraies notifications** (bandeau avec son) pour : téléphone connecté, connexion
  impossible, demande non aboutie, connexion terminée, Wi‑Fi ou localisation à activer.
  L'autorisation d'afficher les notifications est demandée dès l'ouverture.
- **Wi‑Fi et localisation** : une fenêtre propose de les activer à l'ouverture et avant
  Partager ou Recevoir. « Activer » ouvre directement le panneau Wi‑Fi, puis le réglage
  de localisation.
- **Carte du haut** : coins moins arrondis (32 dp → 12 dp).
- **Numéro de version** : porté à 0.4.0, code 5. Il était resté à 3, or Android refuse
  d'installer un paquet plus ancien que celui déjà présent.
- **Localisation** : demandée seulement sur Android 12 et avant, car c'est la seule version où
  Android l'impose à Wi‑Fi Direct. Sur Android 13 et plus, la demande bloquait Partager et
  Recevoir alors que le système n'en a plus besoin.

---

## 0.3.1 — 11 octobre 2026

### « Connexion impossible » sur le téléphone B, sans aucune ligne pour réessayer

Trois causes cumulées, toutes dans `WifiDirectController` :

- Pendant l'invitation, Android signale « pas de groupe ». Le code y voyait un groupe dissous
  et appelait `removeGroup()`, ce qui annulait la connexion qui venait de partir et vidait la
  liste des téléphones. Il ne défait désormais que si un groupe a réellement existé.
- `connect()` partait juste après `stopPeerDiscovery()`, qui est asynchrone : erreur
  générique sur beaucoup de Samsung. On attend maintenant la fin de l'arrêt, puis deux
  nouveaux essais automatiques sont faits avant d'afficher l'erreur.
- Après un échec la recherche n'était pas relancée : l'écran affichait « Recherche… » sans
  rien à toucher. Elle reprend maintenant toute seule.

---

## 0.3.0 — 8 octobre 2026

### La barre du haut

Le nom de l'application ne flotte plus en bas de la barre. La grande barre qui se dépliait et
poussait le titre vers le bas est remplacée par une barre normale, compacte, qui ne bouge pas
au défilement : la marque à gauche, les accès au guide et à la fenêtre À propos à droite. Le
contenu gagne la hauteur que la grande barre occupait.

### Les barres système

Le bandeau gris en haut et le bandeau noir en bas venaient du thème, qui laissait les couleurs
par défaut du système. La barre d'état et la barre de navigation prennent maintenant la couleur
du fond de l'application, avec des icônes sombres : l'écran est d'un seul tenant, du haut
jusqu'en bas.

### La carte des préparatifs

Quand le Wi‑Fi était éteint, la liste des préparatifs commençait à l'étape 2 : la première
ligne avait disparu au profit du texte d'explication, ce qui donnait l'impression d'un
affichage cassé. La liste est numérotée de 1 à 3 dans tous les cas.

---

## 0.2.0 — 7 octobre 2026

### L'invitation « Invitation à se connecter » qui revient, et l'acceptation qui ne fait rien

Cinq défauts réels se cumulaient.

| Ce qui se passait | Cause exacte | Correction |
|---|---|---|
| Accepter la demande ne déclenche rien | Le gestionnaire de connexion ne réagissait que si l'utilisateur avait déjà choisi Partager ou Recevoir dans l'application. Une invitation acceptée depuis la barre de notifications arrivait donc dans une impasse | Le rôle est déduit du sens réel du lien : propriétaire du groupe = partage, client = réception. L'écran s'ouvre seul sur la bonne face |
| L'acceptation ne relie toujours rien après | Le code à six chiffres manquait, et le tunnel refusait de démarrer sans rien dire | Le lien reste en attente et le tunnel part tout seul à la sixième chiffre saisie |
| La demande revient sans fin | Une nouvelle invitation pouvait être envoyée alors qu'une précédente était encore en attente : les demandes s'empilaient | Une seule invitation à la fois. Un second appui ne renvoie rien, il explique |
| Elle revient aussi après un échec | La recherche des appareils restait active, ce qui fait échouer `connect()` avec un code d'occupation, puis le système relance | La recherche est arrêtée avant toute connexion, et l'invitation en attente est annulée avant chaque tentative |
| Après une session précédente | Android garde des groupes persistants et les réveille tout seul | Non supprimables depuis une application : l'API `requestPersistentGroupInfo` n'est pas exposée dans le SDK public. Le code qui prétendait le faire a été retiré et le guide explique la vraie solution : oublier le réseau `DIRECT-…` dans les réglages Wi‑Fi |

Un surveillant de trente secondes prévient maintenant l'utilisateur si une demande n'aboutit
pas, au lieu de laisser un écran muet.

### Position approximative manquante — bloquant sur Android 12 et avant

`ACCESS_FINE_LOCATION` était déclarée sans `ACCESS_COARSE_LOCATION`. Depuis Android 12, une
demande de position précise qui ne réclame pas aussi la position approximative est refusée
d'office par le système. Sur un téléphone en Android 12, Wi‑Fi Direct ne pouvait donc jamais
démarrer. Les deux autorisations sont maintenant déclarées et demandées ensemble, et le guide
rappelle que la localisation doit être active sur ces versions.

### Appels Wi‑Fi Direct sans contrôle d'autorisation

Les appels au système — création de groupe, recherche, connexion, liste des pairs — étaient
faits sans vérifier l'autorisation. Un refus de l'utilisateur faisait lever une exception de
sécurité, donc tomber le processus en pleine session. Chaque point d'entrée vérifie désormais
l'autorisation et prévient par un message, et le récepteur d'événements est protégé : une
autorisation retirée en cours de session n'arrête plus l'application.

### Connexion terminée mais notification toujours au vert

Quand l'application était fermée, plus personne ne suivait l'état du lien : le partage ou le
tunnel continuait de tourner, notification verte, pour une connexion morte. Les deux services
surveillent maintenant le lien eux-mêmes, s'arrêtent proprement et affichent « connexion
terminée ». La surveillance ne s'active que pour une session née d'un Wi‑Fi Direct : le relais
du réseau local, utilisé depuis un PC, n'est jamais interrompu par un événement sans rapport.

### Autres erreurs supprimées

- Le tunnel VPN était relancé à chaque envoi de commande identique, ce qui coupait la
  connexion de toutes les applications en cours. Une signature de session empêche ce
  redémarrage.
- L'échec du tunnel était silencieux : aucune application utilisable, ou interface refusée par
  Android, et l'utilisateur ne voyait rien. Une notification explique désormais la cause.
- Les applications désinstallées depuis le dernier choix faisaient échouer la préparation du
  tunnel. Elles sont retirées de la liste avant préparation.
- La liste des applications n'est plus un obstacle : pendant son chargement, le choix ne bloque
  plus la connexion, et le service ignore de lui-même un paquet disparu.
- Sur Android 13 et plus, l'autorisation d'afficher les notifications est demandée : sans elle,
  la notification du partage restait invisible alors que le service tournait.
- Une couleur hors palette `#F0ECF7` traînait dans la carte de confiance : remplacée par le
  lavande de la charte.
- Erreurs de compilation corrigées : rôle `LinkRole` déclaré deux fois, thème du guide dupliqué,
  marque de l'application privée donc inaccessible depuis la page du guide.

### Le guide n'est plus une fenêtre de dialogue

La fenêtre géante est supprimée. Le guide est une page complète, avec sa barre du haut, sa
flèche de retour, son bouton bas et six sections : ce qu'il faut savoir, le téléphone qui
partage, le téléphone qui reçoit, si ça ne marche pas, depuis un ordinateur, signature. Deux
ajouts répondent directement aux pannes rencontrées : accepter une demande arrivée dans la barre
de notifications suffit, et sur Android 12 et avant la localisation doit être active.

### Arrière-plan et fluidité

Le partage comme la réception tournent dans des services de premier plan qui survivent à la
fermeture de l'application et au balayage de la tâche récente. La session en cours est retrouvée
à la réouverture, avec le bon code et le bon écran. Elle ne s'arrête que par le bouton Arrêter,
par l'action de la notification, ou quand le lien disparaît. Le statut change par un fondu au
lieu de sauter, et les blocs apparaissent et disparaissent en glissant.

---

## Ce qui a été vérifié ici, et ce qui reste à vérifier sur les téléphones

Vérifié dans cet environnement, sur le code réel :

- compilation Kotlin complète de l'application, puis assemblage de l'APK ;
- analyse lint : plus aucune erreur ;
- tests du module `core` : verts, réseau et application PC inchangés ;
- contenu de l'APK relu : les deux activités, les deux services avec leurs types de premier
  plan, les permissions, les classes nouvelles, et la bibliothèque native dans les quatre
  architectures ;
- palette et textes relus : aucune parenthèse dans un texte affiché, aucune couleur hors charte.

Reste à vérifier sur un vrai téléphone, et c'est la seule chose qui ne peut pas être testée
depuis un environnement sans appareil : la formation du lien Wi‑Fi Direct entre les deux
téléphones, et l'acceptation de l'invitation depuis la barre de notifications.

---

Metoushela Walker · <https://github.com/MeganAe/linkbridge> · licence MIT
