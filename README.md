# LinkBridge

> Un pont, pas un hotspot.

LinkBridge est une application Android qui relie deux téléphones à proximité sans demander l'activation du hotspot Wi‑Fi classique.

- **Téléphone A** : possède Internet et devient la passerelle.
- **Téléphone B** : rejoint A par Wi‑Fi Direct et reçoit Internet dans toutes ses applications grâce à `VpnService`.

## Version PC (Windows)

Le module `desktop` propose la même logique côté ordinateur :

- **Partager** : le PC devient le relais SOCKS5 (choix de l'interface réseau, code à 6 chiffres, journal des connexions).
- **Se connecter / Tester** : le PC utilise le relais d'un téléphone A, avec un proxy local `127.0.0.1:1080` sans mot de passe pour Chrome et Edge.

L'installateur `LinkBridge-Setup-<version>.exe` (runtime Java inclus) est produit par la CI
[`windows-installer.yml`](.github/workflows/windows-installer.yml) avec Inno Setup.

### Commandes de build locales

```bash
gradle :core:test              # tests JVM (relais SOCKS5, client, proxy)
gradle :app:assembleDebug      # APK Android
gradle :desktop:createDistributable   # app-image du bureau (jpackage)
```

## Licence

Code sous licence MIT — voir [LICENSE](LICENSE). Le moteur HevSocks5Tunnel est sous licence MIT ; voir [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
