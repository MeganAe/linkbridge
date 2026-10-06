# LinkBridge

> Un pont, pas un hotspot.

LinkBridge est une application Android qui relie deux téléphones à proximité sans demander l'activation du hotspot Wi‑Fi classique.

- **Téléphone A** : possède Internet et devient la passerelle.
- **Téléphone B** : rejoint A par Wi‑Fi Direct et reçoit Internet dans toutes ses applications grâce à `VpnService`.

## Ce qui est réellement implémenté

Cette version 0.2.0 contient le chemin réseau complet du prototype :

1. découverte et connexion Wi‑Fi Direct ;
2. code de liaison à six chiffres ;
3. passerelle SOCKS5 authentifiée sur le téléphone A ;
4. VPN local sur le téléphone B ;
5. moteur `HevSocks5Tunnel` intégré pour TCP, UDP, DNS et IPv4/IPv6 ;
6. notification Android et arrêt explicite ;
7. interface Jetpack Compose avec Material 3 stable et une direction expressive faite de couleurs, formes, motion et grandes actions ;
8. identité visuelle vectorielle sans image générée par IA ;
9. test unitaire du handshake et du relais TCP ;
10. workflow GitHub Actions manuel pour tester puis fabriquer l'APK.

Le relais est conçu pour rester transparent : Android affiche l'autorisation VPN et une notification pendant que le service fonctionne. L'application ne tente pas de masquer une activité réseau au propriétaire du téléphone.

## Important sur le statut « solide »

Le code compile localement dans l'environnement de vérification du projet jusqu'à la phase Kotlin ; la compilation complète est destinée à GitHub Actions, qui dispose de plus de mémoire. Le test sur deux appareils physiques reste indispensable : Wi‑Fi Direct dépend parfois du fabricant, de la version Android, du mode économie d'énergie et de l'état du Wi‑Fi.

Il ne s'agit pas encore d'une version Play Store :

- le code est signé en debug par le build actuel ;
- il faut tester plusieurs marques de téléphones ;
- le service peut être arrêté par certains gestionnaires agressifs de batterie ;
- le débit et la latence dépendent fortement du Wi‑Fi Direct ;
- la passerelle actuelle est prévue pour un lien local entre les deux téléphones, pas pour exposer un proxy sur Internet.

## Utilisation

### Téléphone A — partager

1. Installe le même APK.
2. Vérifie que le téléphone possède Internet.
3. Ouvre LinkBridge et choisis **Partager Internet**.
4. Laisse le Wi‑Fi activé et accepte les permissions de proximité.
5. Lis le code à six chiffres affiché.

### Téléphone B — recevoir

1. Installe le même APK.
2. Choisis **Recevoir Internet**.
3. Accepte la demande Android pour le VPN.
4. Saisis le code du téléphone A.
5. Sélectionne le téléphone A dans la liste Wi‑Fi Direct.
6. Accepte la demande de connexion Android si elle apparaît.

Quand le lien est actif, le téléphone B doit afficher l'icône VPN et les applications du téléphone B peuvent utiliser le tunnel.

### Arrêter

Appuie sur **Arrêter le partage** ou **Annuler** dans LinkBridge. La notification et le VPN doivent disparaître.

## Compiler sans Android Studio

1. Décompresse `linkbridge-android.zip`.
2. Crée un dépôt GitHub vide.
3. Envoie le contenu du dossier dans le dépôt en conservant le dossier caché `.github`.
4. Ouvre **Actions**.
5. Sélectionne **Construire l'APK Android**.
6. Clique sur **Run workflow**.
7. Attends les tests et la compilation.
8. Télécharge l'artifact **LinkBridge-debug-apk**.

Le workflow installe Java 17, le SDK Android, Gradle et les composants nécessaires sur la machine GitHub. Il exécute d'abord les tests unitaires puis produit `app-debug.apk`.

## Architecture

```text
Applications du téléphone B
            |
       Android VpnService
            |
    HevSocks5Tunnel / TUN
            |
    SOCKS5 authentifié
            |
      Wi‑Fi Direct WPA2
            |
  Socks5Gateway sur le téléphone A
            |
      Internet de A
```

Le téléphone B exclut le propre trafic de LinkBridge du VPN afin d'éviter une boucle : le moteur doit pouvoir atteindre le SOCKS5 du téléphone A pour transporter le trafic des autres applications.

## Choix techniques

- Kotlin et Android natif
- Jetpack Compose
- Material 3 stable avec une direction expressive compatible avec les appareils ciblés
- Wi‑Fi Direct via `WifiP2pManager`
- VPN via `VpnService`
- SOCKS5 local avec authentification par code
- `HevSocks5Tunnel` 2.18.0 sous licence MIT pour la conversion TUN vers SOCKS5
- API minimale : Android 10 / API 29
- `compileSdk` : 35
- Package temporaire : `com.linkbridge.app`

## Recherche et références techniques

- Wi‑Fi Direct Android : https://developer.android.com/develop/connectivity/wifi/wifi-direct
- VPN Android : https://developer.android.com/develop/connectivity/vpn
- Material 3 Compose : https://m3.material.io/develop/android/jetpack-compose
- HevSocks5Tunnel : https://github.com/heiher/hev-socks5-tunnel
- Licence et attribution de la bibliothèque : `THIRD_PARTY_NOTICES.md`

## Sécurité

Le code de six chiffres est utilisé comme authentification SOCKS5. Wi‑Fi Direct apporte déjà une protection de liaison, mais le code n'est pas un remplacement d'une cryptographie applicative forte. Avant toute distribution publique, il faudra ajouter une identité de session éphémère, un échange de clés et des tests de menace plus complets.
