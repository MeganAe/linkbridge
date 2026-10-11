# Comment envoyer ces correctifs sur GitHub

Tout est prêt. **Ce fichier-ci ne s'envoie pas** : il sert seulement d'explication.

## Ce qu'il y a dans ce dossier

Treize fichiers, exactement ceux qui changent. Le reste du dépôt n'est pas touché.

```
app/build.gradle.kts                                   version 0.5.1, code 7, signature stable
app/keystore/linkbridge-debug.keystore                 nouveau — certificat de signature
app/src/main/AndroidManifest.xml                       déclaration de la nouvelle page
app/src/main/java/com/linkbridge/app/MainActivity.kt   cœur des changements
app/src/main/java/com/linkbridge/app/AppPicker.kt      page des applications
app/src/main/java/com/linkbridge/app/AppPickerActivity.kt  nouveau — la page des applications
app/src/main/java/com/linkbridge/app/AppSelection.kt   icônes des applications
app/src/main/java/com/linkbridge/app/GuideActivity.kt  bouton batterie, étape du test
app/src/main/java/com/linkbridge/app/Notifier.kt       deux canaux de notification
app/src/main/java/com/linkbridge/app/SessionStore.kt   adresse du relais retenue
.github/workflows/build-apk.yml                        publication automatique de l'APK
CHANGELOG.md                                           journal des versions
README.md                                              section Android
```

---

## Envoi depuis le site GitHub, sans rien installer

1. **Décompresse ce dossier** sur ton ordinateur. Tu obtiens les dossiers `app`, `.github` et
   deux fichiers.

2. **Ouvre la page de dépôt** : <https://github.com/MeganAe/linkbridge>
   Vérifie que tu es bien sur la branche `main`.

3. **Fais glisser sur la page** les quatre éléments suivants, pris dans le dossier
   décompressé :
   - le dossier `app`
   - le fichier `CHANGELOG.md`
   - le fichier `README.md`

   Ne fais pas glisser le dossier `.github` maintenant : il se traite à l'étape 6, sinon
   GitHub le place parfois au mauvais endroit.

   Tout apparaît sur la page, et GitHub prévient si un fichier existant va être remplacé —
   c'est exactement ce qu'on veut.

4. **En bas de la page**, écris la description du changement :
   ```
   Version 0.5.1 : vérifier la connexion, applications, notifications, publication
   ```
   puis appuie sur **Commit changes**.

5. **Le dossier `.github`** : va directement à l'adresse
   <https://github.com/MeganAe/linkbridge/tree/main/.github/workflows>
   Appuie sur **Add file**, puis **Upload files**, et dépose le fichier `build-apk.yml` pris
   dans `.github/workflows/` du dossier décompressé. Il remplacera celui qui existe.
   Puis **Commit changes**.

6. **C'est fini.** Le dépôt contient la version 0.5.1.

---

## Vérifier que tout est passé

Sur GitHub, ouvre `app/build.gradle.kts` : les deux dernières lignes du bloc `defaultConfig`
doivent afficher

```
versionCode = 7
versionName = "0.5.1"
```

---

## Publier l'APK téléchargeable

Une fois les fichiers envoyés, deux façons de faire :

**Depuis le site** : page du dépôt → **Releases** dans la colonne de droite → **Draft a new
release** → dans *Choose a tag*, écris `v0.5.1` → **Create new tag** → titre `LinkBridge 0.5.1`
→ **Publish release**.

**Ou en ligne de commande**, si tu as le dépôt sur ton ordinateur :

```bash
git tag v0.5.1
git push origin v0.5.1
```

Dans les deux cas, GitHub construit l'APK et l'attache à la release au bout de quelques
minutes. Le lien de téléchargement reste valable, contrairement à un fichier posé dans une
conversation.

---

## Ce que contient cette mise à jour

- **La connexion se vérifie vraiment** : bouton Vérifier la connexion sur l'écran de
  réception, qui interroge le relais avec le code puis demande une page minuscule à travers
  lui. Jusqu'ici « connecté » voulait seulement dire « le lien Wi‑Fi Direct existe ».
- **Le partage sans Internet est annoncé** : avertissement dans le statut, sur la carte du
  partage, et par notification, avec un bouton Revérifier.
- **Le choix des applications est une vraie page** : plein écran, une icône par application,
  recherche toujours visible, boutons Tout et Rien.
- **Deux canaux de notification** : les bonnes nouvelles en silence, les problèmes avec son.
- **Économie de batterie** : rappel pendant un partage, avec un bouton vers le bon réglage,
  et le même bouton dans le guide.
- **La demande d'activation ne vient que sur Partager ou Recevoir** : plus rien ne surgit à
  l'ouverture de l'application ni au retour au premier plan. Et l'action demandée reprend
  toute seule une fois le Wi‑Fi activé.
- **Signature stable et publication automatique** : le certificat est livré avec le projet,
  donc les nouvelles versions s'installent par-dessus les anciennes.

---

Metoushela Walker · <https://github.com/MeganAe/linkbridge> · licence MIT
