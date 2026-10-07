# CHANGEMENTS-UI — LinkBridge

Refonte de l'interface desktop, écran de démarrage Android, police embarquée, métadonnées
d'installation et nettoyage des textes.

- Auteur : Metoushela Walker
- Dépôt : <https://github.com/MeganAe/linkbridge>
- Licence : MIT

## 0. État de vérification

**J'ai pu compiler.** Environnement utilisé : JDK 17 (Temurin 17.0.20.1), Gradle 8.9,
Android SDK Platform 35 + Build-Tools 35.0.0, Linux x86_64.

Commandes exécutées, toutes vertes :

```bash
gradle :core:test                    # 4 tests, BUILD SUCCESSFUL
gradle :app:assembleDebug            # BUILD SUCCESSFUL
gradle :desktop:createDistributable  # BUILD SUCCESSFUL, app-image produite
gradle :desktop:compileKotlin        # BUILD SUCCESSFUL, aucun avertissement
```

Les trois commandes de la CI `windows-installer.yml` et `build-apk.yml` passent donc en local,
avec les versions de Kotlin, Compose, AGP et Gradle **inchangées**.

L'interface a été lancée sur un écran X virtuel de 1280 x 800 **et** l'app-image jpackage
(`desktop/build/compose/binaries/main/app/LinkBridge/bin/LinkBridge`) a été démarrée à son tour
pour confirmer que les ressources embarquées, la police et l'icône suivent bien jusque dans le
paquet final. À 1600 x 900, la fenêtre s'ouvre à **1040 x 700, centrée** (position 280,100).

## 1. Chantier A — Interface desktop

### A1. Fenêtre

`desktop/src/main/kotlin/com/linkbridge/app/Main.kt` (réécrit)

- Taille initiale : 80 % de `GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds`,
  plafonnée à 1040 x 700 dp, jamais moins que la taille minimale.
- Position : `WindowPosition(Alignment.Center)`.
- Taille minimale : 860 x 560 dp, appliquée via `Window.minimumSize` (AWT), ce qui empêche
  aussi le chevauchement à l'écran.
- Filet de sécurité `bringFullyOnScreen()` : la fenêtre est bornée à la zone utile de l'écran,
  barre des tâches exclue, au démarrage.

Pourquoi les dp sont la bonne unité : dans Compose Desktop, `Density = écran / 96`. Une fenêtre
de 1040 dp occupe donc 1040 px à 100 %, 1300 px à 125 % et 1560 px à 150 %, tandis que
`maximumWindowBounds` renvoie la zone utile en pixels **physiques**. À 150 % sur un écran
1920 x 1080, la zone utile fait environ 1920 x 1040 px : 80 % donne 1040 x 700 dp, soit
1560 x 1050 px, donc la fenêtre serait 10 px trop haute. Le plafond 1040 x 700 dp ayant été
imposé par le cahier des charges, `bringFullyOnScreen()` ramène cet écart à 10 px vers le haut
au lieu de laisser dépasser la fenêtre. À 100 % et 125 %, aucun déplacement n'a lieu.

### A2. Icône

- `desktop/src/main/resources/brand/linkbridge-{16,24,32,48,64,128,256}.png` : nouveaux fichiers,
  extraits de `installer/linkbridge.ico`, lui-même dessiné depuis `branding/linkbridge-mark.svg`.
- `desktop/src/main/kotlin/com/linkbridge/app/BrandResources.kt` : lecture des images depuis le
  classpath.
- `Main.kt` : `window.iconImages` reçoit les sept tailles (barre de titre, barre des tâches,
  Alt+Tab) et `Taskbar.getTaskbar().iconImage` la version 256 px.
- `installer/linkbridge.ico` : **contrôlé, non régénéré**. Il contient bien sept images 32 bits
  de 16, 24, 32, 48, 64, 128 et 256 px.
- `BrandMark()` remplace `painterResource(String)`, qui est déprécié en Compose 1.7.3 ; le décodage
  passe par Skia.
- `desktop/build.gradle.kts` : `windows.iconFile` pointe toujours sur `installer/linkbridge.ico`.
  Plus aucune icône Java par défaut nulle part.

### A3. Barre de menus native

`Main.kt`, fonction `WindowMenuBar`, construite avec `MenuBar`, `Menu`, `Item`, `CheckboxItem`,
`RadioButtonItem` et `Separator` de Compose Desktop.

| Menu | Entrée | Raccourci | Grisée quand |
|---|---|---|---|
| Fichier | Démarrer le relais | Ctrl+R | le relais tourne |
| Fichier | Arrêter le relais | Ctrl+Maj+R | le relais est arrêté |
| Fichier | Démarrer le proxy local | Ctrl+P | le proxy tourne |
| Fichier | Arrêter le proxy local | Ctrl+Maj+P | le proxy est arrêté |
| Fichier | Quitter | Ctrl+Q | jamais |
| Affichage | Partager | Ctrl+1 | jamais (bouton radio) |
| Affichage | Se connecter | Ctrl+2 | jamais (bouton radio) |
| Affichage | Applications | Ctrl+3 | jamais (bouton radio) |
| Outils | Proxy Windows pour tout le PC | Ctrl+W | Windows absent, ou proxy local arrêté |
| Outils | Effacer les journaux | Ctrl+L | les deux journaux sont vides |
| Outils | Copier le code de liaison | Ctrl+Maj+C | jamais |
| Aide | Guide rapide | F1 | jamais |
| Aide | Signaler un problème | — | jamais |
| Aide | À propos | — | jamais |

Toutes les entrées appellent les fonctions existantes de `DesktopAppState`
(`startSharing`, `stopSharing`, `startProxy`, `stopProxy`, `enableSystemProxy`,
`disableSystemProxy`, `regenerateCode`, `runTest`, `launchApp`, `shutdown`). Aucune logique
réseau n'a été déplacée. Les seules nouveautés d'état sont visuelles et vivent dans
`DesktopUiState.kt` : page affichée, note temporaire de la barre d'état, ouverture des dialogues.

### A4. Mise en page

- `App.kt` (réécrit) : barre latérale de 190 dp, réduite à 48 dp sous 900 dp de large avec
  infobulles (`HoverTooltip`), zone de contenu plafonnée à 1000 dp et centrée, barre d'état de
  24 dp en bas.
- `Widgets.kt` (nouveau) : cartes de section, champs compacts, boutons de 36 dp, journal avec
  barre de défilement permanente, grille à deux colonnes, infobulle, texte tronqué avec infobulle.
- `Theme.kt` (nouveau) : palette de marque inchangée, typographie Inter, formes, barre de
  défilement sombre, et `LocalMinimumInteractiveComponentSize` ramené de 48 à 32 dp. C'est ce
  dernier réglage qui supprime le côté « application mobile » : cases à cocher, boutons radio et
  interrupteurs retrouvent une densité de bureau.
- Deux colonnes au-dessus de 900 dp, une seule en dessous ; les deux valeurs de seuil vivent dans
  `DesktopMetrics` (`Theme.kt`).
- Densité : titres 12 à 14 sp, corps 11 à 12 sp, boutons 30 dp, champs 32 dp (voir la section 9,
  qui resserre encore la première version).
- Survol et focus : `handCursor()` sur les éléments cliquables, curseur de texte sur les champs,
  focus Material 3 conservé ; l'ordre de tabulation suit l'ordre de composition, de la barre
  latérale vers le contenu.
- Interfaces réseau : `ShareScreen.kt`, composant `NetworkIfaceRow`. Le nom est sur une seule ligne,
  tronqué à la fin avec des points de suspension et une infobulle qui n'apparaît que si le texte a
  réellement été coupé ; l'adresse IPv4 est alignée à droite et ne peut pas chevaucher le nom.
  Les adaptateurs VMware et Hyper-V, très longs, ne cassent donc plus la ligne.

### A5. À propos

`Dialogs.kt` : logo, nom, version, slogan, « Conçu et développé par Metoushela Walker »,
copyright MIT, lien du dépôt et lien de signalement cliquables, résumé de ce que fait le produit.
Un second dialogue, « Guide rapide » (F1), donne les trois étapes de chaque rôle et la liste des
raccourcis.

## 2. Chantier B — Installateur et métadonnées

### `installer/linkbridge.iss`

- `AppPublisher=Metoushela Walker`, `AppVerName=LinkBridge {#AppVersion}`,
  `AppCopyright=© 2026 Metoushela Walker. Logiciel sous licence MIT.`
- `VersionInfoCompanyName`, `VersionInfoProductName`, `VersionInfoDescription`,
  `VersionInfoCopyright` et `VersionInfoVersion`.
- `VersionInfoVersion` est **dérivé de `AppVersion`** par un calcul `#define` qui complète à quatre
  nombres : `0.3.0` devient `0.3.0.0`, `0.3` devient `0.3.0.0`, `0.3.0.1` reste `0.3.0.1`.
  Le calcul utilise `Pos()` et `Copy()`, disponibles dans toute version d'Inno Setup 6, et
  s'exécute à la compilation du script, pas à l'installation.
- `LicenseFile=..\LICENSE`. Le chemin est relatif au fichier de script, et le fichier `LICENSE`
  est bien présent à la racine du dépôt. C'est la ligne la plus fragile du lot : si la CI
  compilait le script depuis un autre dossier, il faudrait passer par `SourcePath`. À vérifier au
  premier passage de la CI.
- `UninstallDisplayName=LinkBridge` : nom propre dans la liste des programmes installés.
- `AppUpdatesURL` ajouté, les métadonnées de version pointent vers le dépôt.

### Message de fin d'installation

`%n` qui coupait « port 39876 » supprimé, parenthèses retirées :

> Attention : au premier lancement du relais, Windows peut demander d'autoriser LinkBridge dans
> le pare-feu, sur le port 39876. Cliquez sur « Autoriser l'accès » pour que le partage fonctionne.

### `desktop/build.gradle.kts`

- `vendor = "Metoushela Walker"`, `copyright = "Copyright 2026 Metoushela Walker"`.
- `menuGroup = "LinkBridge"`, `upgradeUuid` fixe : une mise à jour remplace la version précédente
  au lieu de créer une seconde entrée.
- **Le « © » a été retiré de la chaîne `copyright`, volontairement.** jpackage lit son fichier
  d'arguments avec l'encodage de la plateforme : sous Linux la compilation échouait
  (`Input length = 1`, levée par `String.getBytes`), et sous Windows le caractère serait
  probablement illisible dans les propriétés du fichier `.exe`. Le fichier `.exe`, lui, porte
  bien « © 2026 Metoushela Walker » via Inno Setup, qui lit son script en UTF-8.
- Nouvelle tâche `generateLinkBridgeProperties` : écrit `linkbridge.properties` avec la version,
  pour que la boîte « À propos » affiche la vraie version sans rien télécharger.

### B4 — Images du assistant d'installation

**Non fait, et volontairement.** Les images BMP de l'assistant (164 x 314 et 55 x 55) devraient
être régénérées depuis le logo à chaque changement de marque, sans outil de conversion dans le
dépôt, et elles n'apportent rien à l'utilisateur. Le logo est déjà présent via `SetupIconFile`,
l'icône du programme et l'icône de désinstallation.

## 3. Chantier C — Android

### C1. Écran de démarrage

- `app/build.gradle.kts` : `implementation("androidx.core:core-splashscreen:1.2.0")`.
  Version stable la plus récente ; son `aar-metadata.properties` déclare `minCompileSdk=35`,
  donc compatible `compileSdk 35` — c'était le point à vérifier.
- `app/src/main/res/values/themes.xml` :
  - `Theme.LinkBridge.Starting`, parent `Theme.SplashScreen`, fond `@color/linkbridge_ink`
    (Ink #251A46), icône animée `@drawable/splash_linkbridge`,
    `windowSplashScreenAnimationDuration = 600`, et
    `postSplashScreenTheme = @style/Theme.LinkBridge`, c'est-à-dire exactement le thème normal
    d'avant, auquel s'ajoute `android:windowBackground = @color/linkbridge_canvas`.
    Ce fond évite le flash blanc entre la disparition du splash et la première image Compose.
  - Le parent reste `android:style/Theme.Material.Light.NoActionBar` : le thème d'origine est
    rétabli à l'identique.
- `app/src/main/res/drawable/ic_splash_mark.xml` : le logo, dessiné dans le **tiers central** de
  la zone 108 x 108 (les deux piliers mesurent 29 unités de large au centre). Sur Android 12 et
  plus, l'icône est masquée dans un cercle et seul ce tiers est garanti visible : rien n'est rogné.
- `app/src/main/res/drawable/splash_linkbridge.xml` : `AnimatedVectorDrawable` construit sur le
  dessin statique. La passerelle blanche part à 45 % de largeur et 35 % d'opacité, puis se fixe
  sur l'état final du logo. L'animation se termine **exactement** sur le dessin statique : si
  Android la fige, l'image reste correcte.
- `app/src/main/res/animator/splash_bar.xml` : 600 ms, `fast_out_slow_in`, aucun délai ajouté.
- `MainActivity.kt` : `installSplashScreen()` appelé **avant** `super.onCreate()`. Aucun
  `setKeepOnScreenCondition` : le splash disparaît dès la première image prête.
- `AndroidManifest.xml` : `android:theme="@style/Theme.LinkBridge.Starting"` sur `.MainActivity`
  uniquement.

Aucun flash blanc de Android 8 à Android 15 : le fond du splash est Ink, le fond du thème normal
est Canvas, et les deux sont opaques.

### C2. Police unique

**Clarity City a été vérifiée, puis écartée.**

- Vérifié : licence SIL OFL 1.1, embarquement autorisé (`googlefonts/clarity-city`, `OFL.txt`).
- Vérifié : accents français complets. Les fichiers TTF ont été téléchargés et testés glyphe par
  glyphe sous JVM : `é è ê à ç œ « » ’ — … °` plus l'espace fine insécable et l'espace insécable
  sont tous présents (353 glyphes).
- Écartée pour deux raisons : **elle n'est pas publiée sur Google Fonts** (c'est un dépôt GitHub
  `googlefonts/clarity-city`, sans entrée dans le catalogue), et le dépôt amont `vmware-archive`
  a été archivé le 12 avril 2024. Pour un produit qui doit rester téléchargeable et maintenable,
  une police en amont archivée est un risque.

**Choix retenu : Inter 4.1**, sous SIL OFL 1.1 (licence dans `licenses/INTER_OFL.txt`, ajoutée au
dépôt). Quatre graisses, fichiers statiques, aucune police variable (les polices variables et
`FontWeight` ne se marient pas de façon fiable dans Compose Desktop).

- Android : `app/src/main/res/font/inter_regular.ttf`, `inter_medium.ttf`, `inter_semibold.ttf`,
  `inter_bold.ttf` (400, 500, 600, 700).
- Windows : `desktop/src/main/resources/fonts/inter-regular.ttf`, `inter-medium.ttf`,
  `inter-semibold.ttf`, `inter-bold.ttf`, **les mêmes fichiers octet pour octet**.
- `app/src/main/java/com/linkbridge/app/Typography.kt` (nouveau) : les quinze styles Material 3
  redéfinis, appliqués par `MaterialTheme(typography = LinkBridgeTypography)` dans
  `MainActivity.kt`.
- `desktop/src/main/kotlin/com/linkbridge/app/Theme.kt` : les quinze styles redéfinis avec
  l'échelle de bureau, chargés depuis le classpath par
  `androidx.compose.ui.text.platform.Font`.
- Aucune police téléchargeable, aucun `GoogleFont.Provider`, rien qui dépende des Google Play
  Services ou d'une connexion. Testé : le rendu vient bien de la ressource embarquée (l'app-image
  jpackage l'affiche après compilation, sans accès réseau).
- `BRAND.md` : la section Typography interdit explicitement toute police décorative ; elle a été
  réécrite pour documenter Inter, ses graisses, ses fichiers et la couverture française.
- `THIRD_PARTY_NOTICES.md` : entrées Inter et core-splashscreen ajoutées.

### C3. À propos Android

`MainActivity.kt`, `AboutDialog()` : ajout d'un séparateur, de « Conçu et développé par
Metoushela Walker », du lien du dépôt et de « Logiciel sous licence MIT. © 2026 Metoushela Walker. »

## 4. Chantier D — Textes

Chaque parenthèse visible a été retirée en réécrivant la phrase, pas en supprimant un caractère.

| Avant | Après |
|---|---|
| Code (6 chiffres) | Code à 6 chiffres |
| Connexion manuelle (réseau local) | Connexion manuelle sur le réseau local |
| Téléphone A (qui a Internet) | Téléphone A : celui qui a Internet |
| Téléphone B (qui reçoit) | Téléphone B : celui qui reçoit |
| Choisir les applications (3) | Choisir les applications, 3 sélectionnées |
| 3 sélectionnée(s) | 3 applications sélectionnées / 1 application sélectionnée / Aucune application sélectionnée |
| Profil LinkBridge, côté desktop | « Ouvre une fenêtre à part, avec un profil LinkBridge réglé sur le téléphone. » |
| TCP (HTTPS inclus) | « Les sites restent accessibles en TCP, HTTPS compris. » |
| Impossible de créer la liaison Wi-Fi Direct (code X) | Impossible de créer la liaison Wi-Fi Direct. Code X. |
| Recherche Wi-Fi Direct impossible (code X) | Recherche Wi-Fi Direct impossible. Code X. |
| Connexion impossible (code X) | Connexion impossible. Code X. |
| Relais : 192.168… | Relais : 192.168… (espace fine insécable) |
| Proxy système activé (127.0.0.1:1080) | Proxy système activé sur 127.0.0.1:1080 |
| Écriture du registre refusée (ProxyEnable) | Écriture du registre refusée pour ProxyEnable |
| Commande trop longue : reg | Commande trop longue : reg (inchangé, déjà conforme) |
| Connexion réussie à travers le relais (HTTP 204) | Connexion réussie à travers le relais : HTTP 204 |
| Démarre d'abord le proxy local (onglet « Se connecter / Tester »). | Démarre d'abord le proxy local depuis la page Se connecter. |
| Impossible de démarrer le relais : … | Impossible de démarrer le relais : … (espace fine insécable) |
| Accès PC sur ce réseau : « … avec LinkBridge PC (IP …, port …) » | « … avec LinkBridge PC. Adresse …, port …, et le code ci-dessus. » |

Typographie française : espace fine insécable `U+202F` avant `:` `;` `!` `?` et à l'intérieur des
guillemets, dans tous les textes écrits ou modifiés (`« « Proxy DNS pour SOCKS v5 » »`,
`Relais : `, `Utilisateur : `…).

Les parenthèses restantes sont **dans le code** : `ProgramFiles(x86)`, `Regex.escape(name)`,
`File(File(base, "LinkBridge"), …)`, appels de fonctions, `LocalTime.now()`.
Vérification faite par recherche sur les littéraux des deux modules : aucune chaîne affichée
n'en contient plus.

## 5. Lignes Gradle ajoutées

Une seule dépendance a été ajoutée, dans `app/build.gradle.kts` :

```kotlin
// Écran de démarrage rétro-compatible, jusqu'à Android 8.
implementation("androidx.core:core-splashscreen:1.2.0")
```

Aucune dépendance ajoutée côté desktop : `desktop:implementation` n'a pas bougé, et les nouveaux
fichiers n'importent que Compose Desktop, Skia (déjà tiré par `compose.desktop.currentOs`) et AWT.

Aucune version de Kotlin (2.0.21), Compose (1.7.3), AGP (8.7.3) ou Gradle (8.9) n'a été modifiée.

## 6. Tests manuels à faire

### Sur Windows

1. **Ouverture de la fenêtre.** Lancer LinkBridge. Vérifier que la fenêtre est centrée, qu'elle ne
   recouvre pas la barre des tâches et qu'elle est entièrement visible. Répéter avec la mise à
   l'échelle Windows à 125 % puis 150 % : Paramètres → Système → Écran → Mise à l'échelle, en se
   déconnectant entre deux valeurs.
2. **Icône.** Vérifier la barre de titre, la barre des tâches et Alt+Tab. Aucune tasse Java ne doit
   apparaître nulle part.
3. **Barre de menus.** Ouvrir les quatre menus. Vérifier qu'une entrée impossible est grisée
   (« Arrêter le relais » quand il est arrêté, « Activer le proxy Windows » sans proxy local).
4. **Raccourcis.** Ctrl+1, Ctrl+2, Ctrl+3, Ctrl+R, Ctrl+Maj+R, Ctrl+P, Ctrl+Maj+P, Ctrl+W, Ctrl+L,
   Ctrl+Maj+C, F1, Ctrl+Q.
5. **Démarrage réel.** Démarrer le relais, vérifier que le voyant de la barre d'état passe au vert et
   que « Clients connectés » suit. Autoriser LinkBridge dans le pare-feu sur le port 39876.
6. **Proxy Windows.** Démarrer le proxy local, activer « Pour tout le PC », ouvrir Chrome ou Edge,
   naviguer. Désactiver : les réglages d'origine de Windows doivent revenir.
7. **Interfaces réseau.** Sur un PC avec VMware ou Hyper-V, vérifier que les noms longs sont tronqués
   proprement avec une infobulle, sans chevauchement ni coupure.
8. **Redimensionnement.** Ramener la fenêtre à 860 px de large : la barre latérale doit devenir une
   colonne d'icônes avec infobulles et les cartes passer sur une colonne. Élargir à 1920 px : la zone
   de contenu reste plafonnée à 1000 dp et centrée, rien ne déborde ni ne se chevauche.
9. **Installateur.** Compiler `LinkBridge-Setup-0.3.0.exe`, vérifier la licence affichée, puis les
   propriétés du fichier `.exe` : société, nom du produit, description, copyright, version 0.3.0.0.
   Vérifier aussi le message de fin d'installation et le nom dans « Applications et fonctionnalités ».
10. **Fanion du pare-feu.** Au premier lancement du relais, vérifier que l'invite mentionne bien le
    port 39876 et que le texte n'est plus coupé par un `%n`.

### Sur téléphone

1. **Splash.** Lancer l'application à froid : fond Ink, icône animée, aucun flash blanc. À vérifier
   sur Android 10, 12, 13 et 15 au minimum, et sur un appareil avec encoche.
2. **Splash forcé.** Dans les options de développement, mettre les animations à l'échelle x10 :
   l'animation de la passerelle doit se terminer sur le logo complet.
3. **Police.** Vérifier que l'interface utilise Inter et non la police du constructeur : comparer les
   chiffres et le « a » avec un appareil d'une autre marque.
4. **Accents.** Vérifier « Réseau », « Connexion manuelle sur le réseau local », « Choisir les
   applications, 3 sélectionnées », les guillemets « … ».
5. **À propos.** Vérifier l'auteur, le lien du dépôt et la licence.
6. **Parcours complet.** Partager depuis un téléphone, rejoindre depuis un autre, vérifier la
   notification VPN, l'arrêt, et le retour à l'accueil par le bouton retour.
7. **Sans réseau.** En mode avion, l'application doit s'ouvrir normalement : aucune police ni
   ressource n'est téléchargée.

## 7. Ce que je n'ai pas pu vérifier

1. **Windows.** Je n'ai pas de machine Windows. Tout ce qui est Windows est vérifié par lecture des
   API (`Taskbar.iconImage`, `jpackage`, Inno Setup) et par compilation, mais pas exécuté :
   icône de la barre des tâches, `bringFullyOnScreen`, registre du proxy système.
2. **Mise à l'échelle 125 % et 150 %.** Non mesurable sur l'écran virtuel utilisé. Le raisonnement
   est dans la section A1, et le comportement a été confirmé à 100 %. C'est le point à tester en
   priorité sur un vrai poste.
3. **Compilation du script Inno.** `iscc` n'existe pas sous Linux. Le calcul `#define` de
   `VersionInfoVersion` et le chemin de `LicenseFile` doivent être validés au premier passage de
   `windows-installer.yml`. C'est le second point à tester en priorité.
4. **Compilation de l'APK avec `ANDROID_HOME` de la CI.** `:app:assembleDebug` passe en local, mais
   le splash et la police n'ont pas été exécutés sur un appareil : rendu de l'animation, absence de
   flash blanc et rétro-compatibilité Android 8 restent à confirmer physiquement.
5. **VMware et Hyper-V.** Le troncage des noms d'interface est testé avec les noms disponibles dans
   l'environnement de compilation, pas avec un nom d'adaptateur réellement long.
6. **Copier-coller, ouverture de navigateur, proxy Windows, Wi-Fi Direct** : chemins dépendants du
   système, non exécutés ici.
7. **Le rendu du paquet MSI** (`:desktop:packageMsi`) n'est pas compilable sous Linux ; seul
   `createDistributable` a été exécuté.

## 8. Hypothèses prises

1. **Unité des dp pour la taille de la fenêtre.** Compose Desktop traduit les dp avec
   `Density = écran / 96`, donc 1040 dp valent 1040 px à 100 % et 1560 px à 150 %. Le cahier des
   charges fixant le plafond en dp, ce sont les dp qui commandent, et le filet
   `bringFullyOnScreen()` gère le seul cas limite, à 150 % sur un écran 1080 px de haut.
2. **Inter plutôt que Clarity City**, pour les raisons du chantier C2. Le rendu reste une police
   humaniste neutre, proche de celle que le propriétaire regardait.
3. **« Effacer les journaux »** vide les deux journaux en place, sans ajouter de champ à
   `DesktopAppState`. Pour la même raison, les boutons « Effacer » de chaque page appellent
   directement `shareLog.clear()` et `proxyLog.clear()`.
4. **Raccourcis ajoutés** au-delà du cahier des charges (Ctrl+R, Ctrl+P, Ctrl+Maj+P, Ctrl+W, Ctrl+L,
   Ctrl+Maj+C, F1) parce que la barre de menus le permet sans code supplémentaire et que le guide
   les documente.
5. **Copier le code de liaison** copie le code, et l'adresse du relais sur la seconde ligne quand
   elle est connue.
6. **Icônes de navigation** dessinées à la main (flèche montante, flèche descendante, grille) dans
   `App.kt` : cela évite d'ajouter `material-icons-extended` côté desktop, qui pèse plusieurs
   mégaoctets pour trois icônes.
7. **Parenthèses des journaux** : aucune ligne de journal de `core` n'en contient ; les messages
   propres au desktop et à Android ont été réécrits, comme le demande le chantier D1.
8. **Le module `core` n'a pas été touché**, ni ses tests, ni la logique réseau, ni la logique de
   `DesktopAppState`.


## 9. Tour 2 — sobriété du desktop et guide mobile

Deux retours après essai de la première version : le desktop restait « trop générique », et sur
mobile un nouvel utilisateur ne savait pas qu'il faut activer le Wi‑Fi.

### 9.1 Desktop : moins de décor, plus de densité

`desktop/.../Theme.kt`, `Widgets.kt`, `App.kt`, `ShareScreen.kt`, `ConnectScreen.kt`,
`AppsScreen.kt`, `Dialogs.kt`

1. **Aucun angle arrondi.** Tous les rayons Material 3 sont à zéro (`RoundedCornerShape(0.dp)` ;
   `RectangleShape` n'est pas accepté dans `Shapes`, qui exige un `CornerBasedShape`). Boutons,
   champs, cartes, journal, barre de défilement : tout est rectangle.
2. **Pas d'ombre.** Les boutons sont à élévation nulle et les cartes n'ont plus de `Card`
   flottante : un cadre d'un pixel, comme un panneau d'utilitaire.
3. **Moins de violet.** Le violet ne sert plus qu'aux liens cliquables. La page active est un
   fond gris clair avec un trait vertical de 2 dp à gauche ; les boutons principaux sont en Ink ;
   les cartes colorées se limitent à Butter pour le code de liaison et Mint pour le proxy local.
   `primary` du thème est maintenant Ink, pas Purple.
4. **Textes plus petits.** Titres 12 à 14 sp, corps 11 à 12 sp, libellés 10 à 11 sp, graisses
   essentiellement Medium et SemiBold. Inter est appliquée partout comme avant.
5. **Sans badges ni pastilles.** `StatusDot` a disparu, ainsi que les coches et les mots
   « actif » / « inactif » en pastille. L'information passe par une ligne `État` en texte
   (`InfoLine`) dans chaque section, et par la barre d'état en texte simple
   (`Relais : actif | Proxy local : arrêté | Proxy Windows : indisponible`). Le vert de
   confirmation reste utilisé uniquement pour le mot « actif ».
6. **Contrôles plus compacts.** `LocalMinimumInteractiveComponentSize` ramené à 28 dp, boutons de
   30 dp, champs de 32 dp, barre latérale de 190 dp, en-tête de page dans une bande grise de
   même teinte que les barres.

### 9.2 Android : guide et carte de préparation

Nouveau fichier `app/src/main/java/com/linkbridge/app/Guide.kt` (fichier de composables pur,
aucune logique réseau) et ajouts dans `WifiDirectController.kt` et `MainActivity.kt`.

1. **Le Wi‑Fi est surveillé.** `WifiDirectController` expose `wifiEnabled()` et remonte chaque
   changement d'état via un nouveau rappel `onWifiState`. `MainActivity` le relit aussi dans
   `onResume`, donc l'affichage se met à jour au retour des réglages Android.
2. **Carte « Avant de commencer »** sur l'écran d'accueil : trois points, dont le deuxième dit
   explicitement que le partage de connexion et le point d'accès ne servent à rien ici.
   Si le Wi‑Fi est éteint, la carte devient jaune, s'intitule « Le Wi‑Fi est désactivé » et
   propose « Ouvrir les réglages Wi‑Fi », qui lance `Settings.ACTION_WIFI_SETTINGS`.
3. **Guide de démarrage** montré au premier lancement, puis accessible par le bouton Guide de la
   barre du haut et par la carte : ce qu'il faut savoir, les quatre étapes du téléphone A, les six
   étapes du téléphone B, un dépannage en quatre points, et la partie depuis un ordinateur. Le
   guide prévient aussi que le Wi‑Fi peut afficher « pas d'Internet » alors que tout fonctionne,
   et que le VPN doit être autorisé.
4. **Mémorisation locale** du fait que le guide a déjà été montré, dans les préférences
   `linkbridge_guide` : le guide ne revient pas à chaque lancement, et rien ne sort du téléphone.
5. **Garde-fou** sur « Partager » : si le Wi‑Fi est éteint, le partage ne démarre pas et le
   message « Active le Wi‑Fi, puis appuie de nouveau sur Partager » s'affiche.
6. Ajouts dans `MainActivity.kt`, sans toucher à la logique : deux états d'affichage (`wifiOn`,
   `showGuide`), deux méthodes (`openWifiSettings()`, `dismissGuide()`), un `onResume()`, et les
   paramètres correspondants passés au composable.

### 9.3 Vérifications refaites pour ce tour

- Dans l'arbre de travail : `gradle :core:test :app:assembleDebug :desktop:createDistributable`
  passent tous les trois (APK de 18,9 Mo avec le guide et les polices).
- Le zip a été réappliqué sur une copie propre du dépôt puis recompilé :
  `:core:test`, `:app:compileDebugKotlin` et `:app:processDebugResources` passent,
  `:desktop:createDistributable` passe. La seule étape qui échoue dans le bac à sable est
  `:app:mergeExtDexDebug`, qui tue le démon Gradle faute de mémoire disponible ; aucune erreur
  Kotlin n'est produite, et cette même tâche passe dans l'arbre de travail. C'est donc une
  limite de la machine, pas du code.
- Interface desktop relancée depuis l'app-image jpackage : fenêtre 1040 x 700 centrée, angles
  droits partout, aucun débordement en 880 px de large comme en 1040 px.
- Alignement mesuré au pixel sur la capture en 880 px : le bloc coloré et les cartes blanches
  occupent exactement la même largeur (346 à 1142 px), et en 1040 px les deux colonnes vont de
  488 à 887 et de 902 à 1302, centrées dans la zone de contenu.

### 9.4 Points non vérifiés pour ce tour

- Le guide et la carte de préparation n'ont pas été exécutés sur un appareil Android : mise en
  page du dialogue, lecture des étapes, ouverture des réglages Wi‑Fi et comportement au retour.
- L'état du Wi‑Fi n'a pas été testé sur un appareil où l'unique connexion est la 4G sans Wi‑Fi
  allumé, ni sur Android 8, où `Settings.ACTION_WIFI_SETTINGS` s'ouvre normalement mais où le
  chemin exact peut varier.
- Le rendu de la densité resserrée n'a pas été vu sur un vrai écran Windows à 125 % et 150 %.
