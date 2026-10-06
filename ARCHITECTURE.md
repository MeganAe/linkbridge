# Architecture LinkBridge

## Flux réel

```text
┌───────────────────────────────┐
│ Téléphone B                   │
│ Chrome, WhatsApp, etc.        │
│          ↓                    │
│ Android VpnService / TUN      │
│          ↓                    │
│ HevSocks5Tunnel                │
│          ↓ SOCKS5 + code       │
└──────────┬────────────────────┘
           │ Wi‑Fi Direct WPA2
┌──────────▼────────────────────┐
│ Téléphone A                   │
│ Socks5Gateway                 │
│          ↓                    │
│ Socket TCP/UDP normal         │
│          ↓                    │
│ Internet de A                 │
└───────────────────────────────┘
```

## Téléphone A

`GatewayService` maintient un service au premier plan. Il démarre `Socks5Gateway` sur le port Wi‑Fi Direct `39876`.

Le serveur :

- exige la méthode SOCKS5 username/password ;
- utilise `linkbridge` comme nom d'utilisateur ;
- utilise le code de six chiffres comme mot de passe ;
- prend en charge `CONNECT` pour TCP ;
- prend en charge `UDP ASSOCIATE` pour DNS, QUIC et les applications UDP ;
- ouvre les sockets sortantes avec la connexion Internet normale de A ;
- se lie à l'adresse IP du groupe Wi‑Fi Direct, pas à toutes les interfaces du téléphone ;
- ne publie pas le port sur un serveur ou sur Internet.

## Téléphone B

`VpnTunnelService` :

1. demande le consentement Android avec `VpnService.prepare()` ;
2. crée une interface TUN IPv4/IPv6 ;
3. route tout le trafic vers cette interface ;
4. exclut uniquement le propre package LinkBridge pour éviter une boucle ;
5. donne le descripteur TUN à HevSocks5Tunnel ;
6. configure l'adresse et le code du gateway A ;
7. arrête proprement le moteur et ferme le TUN à la révocation ou à l'arrêt.

## Pourquoi utiliser HevSocks5Tunnel

Réécrire en Kotlin le traitement TCP/UDP, le DNS virtuel, la traduction des adresses et l'état des sessions serait fragile. HevSocks5Tunnel fournit un moteur natif largement utilisé, dual-stack, avec prise en charge TCP/UDP et une liaison JNI Android. LinkBridge ne lui donne que le descripteur TUN et une configuration SOCKS5 locale.

## Points à tester sur appareils réels

- téléphone A en données mobiles ;
- téléphone A connecté lui-même à un Wi‑Fi ;
- Android 10, 12, 13, 14 et 15 ;
- Samsung, Xiaomi, Tecno, Infinix et Pixel si disponibles ;
- verrouillage de l'écran ;
- économie d'énergie ;
- suppression du groupe Wi‑Fi Direct ;
- perte puis retour du signal ;
- navigation HTTPS, DNS, téléchargement et application UDP ;
- arrêt manuel depuis LinkBridge et depuis les réglages VPN.
