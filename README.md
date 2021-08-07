# discord-bot-role-attributor

Bot Discord servant à automatiquement attribuer les rôles sur un serveur.

À utiliser avec [Discord Bot Launcher](https://github.com/NastiouchaNakamura/discord-bot-launcher).

### Variables d'environnement à paramétrer

| Clé | Valeur |
|------|------|
| DISCORD_BOT_LAUNCHER_BOT_`X`_TEXT_CHANNEL_ID | `ID du canal textuel où se situe le message contenant les rôles proposés` |
| DISCORD_BOT_LAUNCHER_BOT_`X`_MESSAGE_ID | `ID du message contenant les rôles proposés` |

### Utilisation

Le message qui sera écouté par le Bot est de la forme `(.*\n)*--\n(\p{Emoji} .*\n)*`, c'est-à-dire visuellement :
```
Texte quelconque
--
{Emoji} {Nom du rôle associé}
.
.
.
```

 Par exemple, le message peut être :
 ```
Bonjour et bienvenue à tous :wave:
Pour vous attribuer automatiquement un rôle, réagissez à ce message avec l’emote correspondante :wink:
--
🥇 L1 Portail Sciences
🌿 Sciences de la Vie
🪨 Sciences de la Terre
🪴 SVT
⚛️ Physique
⚗️ Chimie
🧮 Mathématiques
🖥️ Informatique
📁 MIAGE
```
