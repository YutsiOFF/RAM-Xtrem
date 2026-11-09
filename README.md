# RAM Xtreme

[![Android Build](https://github.com/YutsiOFF/RAM-Xtrem/actions/workflows/android-build.yml/badge.svg)](https://github.com/YutsiOFF/RAM-Xtrem/actions/workflows/android-build.yml)

Application Android native qui **mesure** la mémoire réelle du téléphone et **configure la mémoire virtuelle quand le système l'autorise**, sans root. Aucune fausse statistique, aucun faux fichier de swap : chaque valeur vient du noyau, chaque réglage est relu après écriture.

## Ce que l'application fait vraiment

| Fonction | Sans rien | Avec Shizuku (droits ADB) |
|---|---|---|
| RAM physique, swap total/utilisé, stockage | Oui | Oui |
| Détail ZRAM, périphériques de swap, pression mémoire | Souvent masqué par Android | Oui |
| Samsung RAM Plus : écrire une taille, y compris hors liste | Non | Oui, effet mesuré après redémarrage |
| Limite d'apps gardées en cache (profils) | Non | Oui |
| Mémoire par application | Non | Oui |

## Limites d'Android (non contournables sans root)

- **ZRAM** : la taille et l'algorithme ne sont modifiables que par `init`/root (SELinux).
- **Fichier de swap** : `swapon` exige `CAP_SYS_ADMIN`, que ni une app ni ADB Shell ne possèdent.
- **Paramètres noyau** (`swappiness`, etc.) : lecture seule.
- **Xiaomi, Motorola, OnePlus, OPPO, Realme, Pixel** : aucun réglage d'extension modifiable connu via ADB. L'app affiche l'état et renvoie vers les paramètres système.
- **Samsung** : une valeur au-delà de la liste constructeur peut être écrite, mais One UI peut l'ignorer. L'app compare le swap réel avant/après redémarrage et le dit clairement.
- Aucun profil ne peut forcer Android à garder toutes les apps en mémoire.

Shizuku n'est pas du root. L'app n'envoie que les commandes d'une liste blanche fixe : [`CommandPolicy.kt`](app/src/main/java/com/ramxtreme/app/core/CommandPolicy.kt).

## Écrans

Accueil (tableau de bord temps réel) · RAM Extension · Optimisation (profils) · Applications · Compatibilité · Shizuku · Paramètres.

## Technologies

Kotlin, Jetpack Compose, Material 3, Coroutines, API Shizuku 13.1.5. minSdk 26, targetSdk 35. Pas de NDK.

```
app/src/main/java/com/ramxtreme/app/
├── core/      Logique pure et testée : parseurs, planification, liste blanche
├── data/      Lecture système, moteurs d'extension et de profils (sauvegarde, relecture, restauration)
├── shizuku/   Connexion Shizuku et service privilégié
└── ui/        Écrans Compose
```

## Installer l'application

1. Ouvrez **Releases** sur GitHub et téléchargez `RAM-Xtreme.apk`.
2. Ouvrez le fichier et autorisez l'installation depuis votre navigateur.

Sans Release : onglet **Actions** → dernière exécution réussie → artefact `RAM-Xtreme-apk` (fichier zip contenant l'APK ; connexion GitHub requise).

> Tant que la signature Release n'est pas configurée, l'APK est un build Debug signé avec une clé de test propre à chaque compilation : pour mettre à jour, il faut désinstaller la version précédente.

## Compilation automatique

Le workflow [`android-build.yml`](.github/workflows/android-build.yml) se lance :

- à chaque push sur `main` ;
- manuellement : **Actions → Android Build → Run workflow** ;
- à la création d'un tag `v*`.

Il exécute lint et tests unitaires, compile l'APK, vérifie sa signature, puis le publie en artefact (conservé 30 jours).

## Publier une version

Créez un tag `v1.0.0` (sur mobile : **Releases → Draft a new release → Choose a tag → saisir `v1.0.1` → Publish** crée le tag ; ou via git : `git tag v1.0.1 && git push origin v1.0.1`). Le workflow compile, teste, crée la Release avec ses notes et y joint `RAM-Xtreme.apk`.

## Signature Release (facultative)

Dans **Settings → Secrets and variables → Actions**, ajoutez :

| Secret | Contenu |
|---|---|
| `RX_KEYSTORE_BASE64` | Le fichier keystore encodé en base64 |
| `RX_KEYSTORE_PASSWORD` | Mot de passe du keystore |
| `RX_KEY_ALIAS` | Alias de la clé |
| `RX_KEY_PASSWORD` | Mot de passe de la clé |

Sans ces quatre secrets, seul l'APK Debug est produit et la Release l'indique. Ne versionnez jamais de keystore : `.gitignore` les exclut.

## État des tests

- Tests unitaires (parseurs, liste blanche, planification) exécutés à chaque compilation.
- **Non testé sur appareil physique** : l'app a été compilée en intégration continue, pas exécutée sur un téléphone. Le comportement réel de RAM Plus hors liste, de Shizuku et de chaque marque reste à valider sur vos appareils.
- Les textes sont en français dans le code ; le passage à `strings.xml` pour d'autres langues reste à faire.
