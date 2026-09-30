# Reprendre le projet sur une autre machine

## Commande de départ

Après avoir installé Git et Python 3.9 ou plus récent, cloner le dépôt de confiance,
entrer dans son dossier et lancer :

```bash
scripts/restore-workspace
```

Ne pas utiliser `sudo`, ne pas sourcer ce fichier, ne pas le lancer avec `bash`, et ne
pas rediriger sa session interactive vers un journal. C'est un exécutable Python,
sans dépendance Python externe pour la restauration elle-même. Linux est la cible
validée ; sous Windows, utiliser WSL sur un système de fichiers Linux. La restauration
emploie des permissions POSIX et des liens physiques : ne pas utiliser un volume FAT
ou un partage qui ne fournit pas ces garanties. macOS n'a pas été validé de bout en bout.

Pour commencer sans aucune restauration ni modification :

```bash
scripts/restore-workspace --diagnose
scripts/restore-workspace --help
```

Le diagnostic ne lit aucun secret local et ne demande aucune phrase secrète. Il
peut contacter GitHub via `gh auth status` pour vérifier la connexion existante,
avec un délai maximal de 30 secondes. Il ne connecte pas un compte, ne modifie pas
Git et ne produit pas de `check-local.log`.

## Ce que fait le mode interactif

1. Identifie le dépôt depuis le dossier courant et lit `config/recovery.json`.
2. Vérifie la forme de la configuration, les chemins, le SHA-256 exact du fichier
   chiffré et la forme de l'empreinte publique du certificat.
3. Affiche les outils manquants, l'état de la connexion GitHub, l'identité Git et
   la présence de la skill versionnée et de son lien de découverte.
4. Si `gh` existe mais n'est pas connecté, propose `gh auth login`. Seulement après
   confirmation et succès, propose `gh auth setup-git`, en avertissant que celui-ci
   modifie la configuration Git de l'utilisateur. Aucune connexion Codex automatique.
5. Demande si les fichiers privés doivent être récupérés. **Entrée seule signifie non.**
6. En cas d'accord, GnuPG demande la phrase secrète dans le terminal. Le script ne
   reçoit jamais cette phrase en argument, variable d'environnement ou fichier.
7. Vérifie tous les fichiers et le certificat avant de publier les fichiers restaurés
   dans `.signing/`. Il affiche uniquement leurs chemins, jamais leur contenu.
8. Propose séparément `scripts/check-local`. Un échec interdit la suite et renvoie
   vers `check-local.log`. Ce contrôle ne produit pas d'APK.
9. Seulement après un contrôle réussi, propose `scripts/make-remote`, après un
   avertissement explicite concernant commit/push, compilation distante et ADB.

Le script n'installe aucun paquet, ne modifie pas les profils shell, n'accepte aucune
licence Android, ne crée pas de dépôt distant, ne change pas `origin`, ne génère pas
de nouvelle clé et ne remplace aucun secret GitHub. Les opérations explicitement
déléguées à `check-local` ou `make-remote` gardent leur comportement documenté.

**Attention ADB :** `make-remote` peut désinstaller puis réinstaller l'application
sur l'appareil sélectionné. Les réglages de l'application et ses widgets placés
sont alors supprimés. Débrancher le téléphone si ce comportement n'est pas voulu.

## Outils et comptes à préparer

Pour récupérer les fichiers : Python 3.9+, Git, GnuPG et le JDK déclaré dans la
configuration (actuellement 17, avec `java`, `javac` et `keytool`). Le JDK sert à
contrôler la clé ; aucun APK n'est construit localement.

Pour poursuivre les tests et la livraison : Bash, GitHub CLI, ShellCheck, ripgrep,
GnuPG/gpgconf, Python 3 avec PyYAML, les outils Unix mentionnés par le diagnostic et
le SDK Android déclaré dans la configuration. Installer les command-line tools
Android avec `apkanalyzer` sous `cmdline-tools/latest/bin`, puis :

```bash
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
sdkmanager --licenses
```

Lire et accepter soi-même les licences. Le diagnostic affiche les versions de la
configuration courante ; ces exemples correspondent à ce dépôt. Fournir `JAVA_HOME`
et `ANDROID_HOME` si la détection échoue. Un `local.properties` ignoré avec `sdk.dir=`
est également accepté. Les chemins détectés sont transmis aux scripts enfants,
sans modifier durablement la configuration de la machine.

Une connexion GitHub avec accès au dépôt est nécessaire pour la livraison. Si besoin :

```bash
gh auth login
gh auth setup-git
git config user.name "Votre nom"
git config user.email "Votre adresse de commit"
```

Les deux dernières commandes règlent uniquement l'identité du dépôt courant.
Installer et connecter Codex séparément si utilisé. `adb` est facultatif.

## Fichiers récupérés et reprise du contexte

| Fichier | Contenu et rôle |
| --- | --- |
| `.signing/release.p12` | Clé de distribution originale, au format PKCS12 |
| `.signing/password` | Mot de passe original de cette clé, différent de la phrase de sauvegarde |
| `.signing/conversation.json` | Instantané des messages visibles disponibles au moment de l'export |

La conversation n'est ni une session Codex réimportable, ni une synchronisation
continue. Les messages ultérieurs, pièces jointes et résultats d'outils n'y figurent
pas. Pour reprendre, demander à l'agent de lire `AGENTS.md`, la skill versionnée et
le seul fichier `.signing/conversation.json`. Ne pas lui demander de lire la clé ou
son fichier de mot de passe. Le code et les instructions actuels priment sur les
anciennes décisions présentes dans la conversation.

Le clone fournit déjà `skill/make-android-widget/` et son lien relatif
`.agents/skills/make-android-widget`. Aucune installation globale de cette skill
n'est nécessaire. La CI peut continuer à signer avec les secrets GitHub existants
**sans récupérer de clé sur le nouveau poste**. Ne jamais recréer une clé simplement
parce que la machine a changé : cela changerait l'identité de signature des mises à jour.

## Garanties de sécurité et limites

- Le répertoire `.signing/` doit être ignoré par Git, non suivi, sans lien symbolique,
  appartenir à l'utilisateur et être en mode `700`. Un dossier existant non conforme
  est refusé, pas corrigé silencieusement. Les fichiers privés sont créés en `600`.
- Aucun fichier cible existant, même un lien cassé, n'est écrasé. Une deuxième
  exécution peut refuser une récupération déjà effectuée : c'est volontaire.
- Un verrou créé exclusivement empêche deux instances coopérantes de restaurer
  simultanément. L'espace de travail temporaire est créé sous `.signing/`.
- L'archive chiffrée est bornée à 32 Mio. Le résultat GPG est également borné à
  32 Mio et le succès complet du déchiffrement est exigé avant toute extraction.
  Aucune archive TAR déchiffrée n'est écrite sur disque.
- Seuls les fichiers déclarés sont permis : clé et mot de passe, avec éventuellement
  `conversation.json`. Les traversées de chemins, chemins absolus, liens, fichiers
  spéciaux, doublons, entrées inattendues, fichiers manquants et métadonnées PAX sont
  refusés. Les limites par fichier sont 1 Mio pour la clé, 4 Kio pour le mot de passe,
  16 Mio pour la conversation. Son format JSON est contrôlé sans afficher les messages.
- `keytool` doit confirmer une entrée de clé privée pour l'alias configuré et un
  certificat dont le SHA-256 correspond à `config/release-certificate.sha256`.
  Ses sorties sont capturées et ne sont jamais reproduites dans un message d'erreur.
- Les fichiers ne sont publiés qu'après ces contrôles, par liens physiques sans
  écrasement. Sur erreur interceptable, seuls les nouveaux liens de cette transaction
  sont retirés ; les fichiers préexistants sont préservés.
- Les empreintes du dépôt détectent une corruption ou une substitution par rapport
  à cette configuration. Elles ne protègent pas d'un attaquant qui aurait aussi
  remplacé le script et la configuration : cloner une source de confiance.
- Ni Python ni une suppression ordinaire ne garantissent l'effacement physique des
  secrets en RAM, swap, sauvegardes du système ou SSD. Utiliser une machine de confiance
  et un disque chiffré. Le script ne prétend pas résister à un autre processus malveillant
  exécuté sous le même utilisateur, capable de remplacer les chemins pendant l'opération.
- Le script conserve l'archive chiffrée et les trois fichiers restaurés. Il ne supprime
  pas les originaux après succès. Leur éventuelle suppression est une décision séparée.

## Interruption ou erreur

Une mauvaise phrase secrète, un certificat incorrect ou une archive refusée ne publie
aucun fichier final. Corriger la cause puis relancer, sans modifier les empreintes pour
contourner un échec. Une phrase secrète perdue n'est pas récupérable depuis le chiffrement.

`Ctrl-C`, `SIGTERM` et `SIGHUP` déclenchent le nettoyage des fichiers temporaires de la
transaction. Une coupure de courant, `SIGKILL`, une erreur disque ou une deuxième interruption
pendant le nettoyage peut laisser un verrou, un répertoire `.restore-*` ou un jeu de fichiers
partiel sous `.signing/`. Ne pas les effacer automatiquement : vérifier qu'aucune restauration
n'est encore active, examiner les chemins exacts, puis déplacer les restes dans un dossier
privé de quarantaine ou demander une aide ciblée. Ne jamais effacer `.signing/` en bloc.

Codes de sortie : `0` = étapes choisies réussies (ou diagnostic sans manque) ; `1` =
échec ; `2` = diagnostic incomplet ou arguments invalides ; `128 + signal` = interruption.
Un code `0` après avoir refusé les tests ne signifie pas que le projet a été validé.
Seul un nouveau `check-local.log` réussi autorise `make-remote`.

## Configuration, maintenance et tests

`config/recovery.json` contient uniquement des informations publiques : version du format,
chemin et SHA-256 de l'archive, chemin de l'empreinte du certificat, alias, fichiers attendus,
versions JDK/SDK/Build Tools. Le script ne contient aucun nom de widget, paquet Android,
compte GitHub ou empreinte propre au projet. Après remplacement autorisé de la sauvegarde,
vérifier son téléchargement/déchiffrement avant de mettre à jour cette configuration.

`tests/test_workspace_recovery.py` est découvert par `qualityCheck`, donc par les contrôles
locaux et chaque compilation release. Ses clés, phrases secrètes et dépôts sont jetables.
Les tests ne lisent pas la sauvegarde privée réelle, n'appellent pas GitHub et ne modifient
pas les secrets de CI. Le contrat de `check-local` compare le script et ses tests à leur
copie dans la skill.

Références techniques : [GnuPG et saisie loopback](https://www.gnupg.org/documentation/manuals/gnupg/GPG-Esoteric-Options.html),
[keytool du JDK 17](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html),
[formats et risques des archives TAR Python](https://docs.python.org/3/library/tarfile.html).
