# Instructions pour les agents LLM

Ces instructions remplacent toutes les instructions `AGENTS.md` précédemment fournies.

## Finalité du projet

Ce dépôt contient **Memento Mori**, un widget Android d'écran d'accueil écrit en Kotlin.
L'application contient le widget et une activité Material 3 de réglages, sans service permanent.
L'activité permet de choisir la date cible et les couleurs du texte pour les modes clair et sombre.

Le widget affiche le temps restant jusqu'à une date cible, fixée par défaut au **1er janvier 2040**,
sous la forme de trois
intervalles indépendants et entiers :

```text
<années>Y
<mois>M
<semaines>W
```

Exemple : `10Y`, `120M`, `521W`. Chaque valeur est calculée directement entre la date
locale du jour et la date cible avec `ChronoUnit.YEARS`, `ChronoUnit.MONTHS` et
`ChronoUnit.WEEKS`. Les trois nombres ne sont donc pas les composantes successives d'une
durée décomposée. Aucun état de compteur n'est persisté ; seuls la date cible et les deux choix
de couleur sont conservés dans les préférences privées de l'application.

## Contraintes fonctionnelles

- La date cible par défaut est `2040-01-01` et doit rester centralisée dans `TARGET_DATE`.
- L'activité impose une date cible strictement postérieure à `LocalDate.now()`. Une valeur
  persistée devenue invalide est remplacée à la lecture par la date par défaut si elle reste
  future, sinon par le lendemain.
- Le calcul utilise `LocalDate.now()` : seule la date civile locale compte.
- Le rendu comporte exactement trois lignes, dans l'ordre années, mois, semaines.
- Les suffixes sont respectivement `Y`, `M` et `W`, précédés visuellement d'une espace fine
  Unicode `U+2009` : par exemple `10 Y`, `120 M`, `521 W`.
- Le widget est recalculé et redessiné :
  - lors de son ajout ou d'une mise à jour demandée par le lanceur ;
  - après le démarrage complet du téléphone ;
  - après le remplacement de l'APK ;
  - après une modification manuelle de l'heure ou du fuseau horaire ;
  - lorsque le processus reçoit un changement de configuration clair/sombre du système ;
  - immédiatement après une modification des réglages dans l'activité ;
  - chaque jour à la prochaine heure locale de 01:00 ;
  - immédiatement lorsque l'utilisateur touche le widget.
- Après chaque alarme ou événement système, la prochaine alarme locale de 01:00 est
  reprogrammée. Il s'agit d'une alarme ponctuelle, afin de rester aligné sur 01:00 malgré
  les changements d'heure saisonniers.
- L'alarme utilise `AlarmManager.setAndAllowWhileIdle`, sans permission d'alarme exacte.
  Android peut donc la différer légèrement pour économiser la batterie ; ne pas demander
  `SCHEDULE_EXACT_ALARM` sans demande explicite.
- Quand la dernière instance du widget est supprimée, l'alarme doit être annulée.

## Rendu et dimensions

- Taille cible et taille minimale : **2 colonnes × 2 lignes** (`2x2`).
- Fond entièrement transparent.
- Texte dessiné avec la police embarquée `Noto Mono Regular`, sans graisse forcée. Sa couleur
  provient du réglage correspondant au mode clair ou sombre courant ; les valeurs par défaut sont
  gris moyen (`#FF888888`) en mode clair et blanc en mode sombre. Le
  rendu réel est produit dans le processus de l'application par `Canvas` et `Paint`, puis transmis
  sous forme de bitmap à un `ImageView` `RemoteViews`. Le lanceur ne reçoit donc plus du texte et
  ne peut plus remplacer la police par une police proportionnelle.
  Les trois lignes sont alignées à droite dans toute la largeur disponible, espacées de 15 %
  entre lignes et centrées ensemble dans le widget. L'espace visuel avant chaque suffixe reste
  inférieur à une largeur de glyphe, tout en conservant Noto Mono pour tous les caractères.
- L'ensemble de la surface est cliquable et déclenche un recalcul suivi d'un nouveau
  rendu.
- La taille de police n'est jamais une constante visuelle. Elle est calculée depuis les
  dimensions réelles fournies par les options AppWidget, le facteur de police Android, les marges et la
  longueur de la ligne la plus longue, puis agrandie par un facteur visuel de 1,35.
- Le calcul doit employer des coefficients conservateurs pour la largeur d'un glyphe
  monospace et la hauteur d'une ligne. Les trois lignes doivent toujours rester visibles,
  complètes, sur une seule ligne chacune, sans coupure ni retour à la ligne.
- Le widget peut être agrandi jusqu'à `4x4` environ ; le bitmap et la police doivent alors être
  recalculés selon ses dimensions réelles.
- L'aperçu statique du sélecteur ne réalise aucun calcul, mais doit rester visuellement
  fidèle au rendu réel : police monospace, alignement à droite, espace fine et valeurs
  représentatives `10 Y`, `120 M`, `521 W`. Il ne doit pas utiliser `...` ni de faux zéros. Sa
  police utilise l'auto-dimensionnement Android pour occuper le cadre 2x2 sans troncature.
  Ses ressources suivent automatiquement le mode système : texte blanc sur fond noir en mode
  clair et texte noir sur fond blanc en mode sombre.

## Activité de réglages

- L'activité utilise Material Components 1.14.0, le thème `Theme.Material3.DayNight.NoActionBar`
  et force explicitement `MODE_NIGHT_FOLLOW_SYSTEM` afin de suivre automatiquement le mode clair
  ou sombre du système et de recréer son interface avec les ressources correspondantes.
- Les éléments sont affichés verticalement : titre `Memento Mori widget`, paragraphe expliquant
  les trois intervalles affichés, section `Target date` avec `MaterialDatePicker`, puis section
  `Font color` avec les sous-sections `Light mode` et `Dark mode`. Le contenu ajoute les insets
  des barres système à son padding afin que le titre ne soit pas collé en haut en mode bord à
  bord.
- Le sélecteur de date bloque toutes les dates antérieures ou égales à la date locale courante.
- Chaque couleur est choisie dans un dialogue Material avec aperçu et curseurs rouge, vert et
  bleu. Les couleurs enregistrées sont opaques.
- Toute modification est persistée dans les préférences privées de l'application et provoque un
  rafraîchissement immédiat de toutes les instances du widget.
- `MementoMoriApplication.onConfigurationChanged` rafraîchit les widgets lorsque le processus
  reçoit le changement de mode. Si le processus n'est pas vivant, le prochain événement normal
  du widget ou un toucher relit toujours le mode système courant avant le rendu.

## Icône

L'icône officielle est un sablier contrasté sur un fond opaque. Ses ressources suivent le mode
système : fond noir et sablier blanc en mode clair, fond blanc et sablier noir en mode sombre. Le
manifeste doit utiliser la même ressource adaptative `@mipmap/app_icon` pour `android:icon` et
`android:roundIcon`, afin que l'icône affichée dans le sélecteur de widgets soit la même que celle
de l'application. Ne pas revenir à un simple tracé sur fond transparent : il devient invisible
sur certains lanceurs.

## Architecture et fichiers importants

- `app/src/main/java/com/github/panlelapin/mementomori/MainActivity.kt` contient l'activité
  Material de réglages affichée depuis le tiroir d'applications.
- `app/src/main/java/com/github/panlelapin/mementomori/MaterialColorPicker.kt` contient le
  dialogue Material de sélection RVB.
- `app/src/main/java/com/github/panlelapin/mementomori/WidgetSettings.kt` contient les règles
  pures de validation/sélection et la persistance privée des réglages.
- `app/src/main/java/com/github/panlelapin/mementomori/MementoMoriApplication.kt` relaie les
  changements de configuration reçus par le processus vers les widgets actifs.
- `app/src/main/java/com/github/panlelapin/mementomori/BasicWidget.kt` contient le rendu
  `RemoteViews`, le receiver AppWidget et l'action exécutée au toucher.
- `app/src/main/java/com/github/panlelapin/mementomori/DailyUpdateReceiver.kt` contient le
  receiver des événements temporels, la gestion de l'alarme et le calcul pur de la
  prochaine heure locale de 01:00.
- `app/src/main/java/com/github/panlelapin/mementomori/CountdownCalculator.kt` contient la
  date cible et le calcul pur des intervalles.
- `app/src/main/java/com/github/panlelapin/mementomori/WidgetFontSizeCalculator.kt` contient
  le calcul pur de la taille de police.
- `app/src/main/AndroidManifest.xml` déclare l'application, l'activité, les deux receivers et la
  permission `RECEIVE_BOOT_COMPLETED`. Les receivers restent non exportés.
- `app/src/main/res/xml/basic_widget_info.xml` décrit les dimensions, le redimensionnement
  et l'aperçu du widget.
- `app/src/main/res/layout/widget_preview.xml` est uniquement l'aperçu statique du
  sélecteur ; il ne réalise aucun calcul.
- `app/src/main/res/mipmap-anydpi-v26/app_icon.xml` est l'icône adaptative Android.
- `app/src/test/` contient les tests JVM du calcul de date et de la taille de police.

## Socle Android

- Kotlin, Material Components 1.14.0, les APIs Android AppWidget/RemoteViews et une activité
  Material 3 DayNight.
- `applicationId` et namespace : `com.github.panlelapin.mementomori`.
- `minSdk = 34` (Android 14), `targetSdk = 36`, `compileSdk = 36`.
- Java/Kotlin JVM 17.
- Le rendu ne dépend pas de Glance ni de WorkManager. Un bitmap transparent est dessiné avec la
  ressource `@font/noto_mono_regular`, puis transmis dans un `RemoteViews` natif. Le nombre et
  son suffixe sont dessinés séparément afin de réduire l'espace visuel sans repasser en police
  proportionnelle.
- Un filtre `arm64-v8a` est configuré pour d'éventuelles dépendances natives. Tant que
  l'application reste entièrement Kotlin et ne contient aucun fichier `.so`, l'APK demeure
  en pratique indépendant de l'ABI ; ne pas prétendre le contraire.
- Le dépôt utilise un verrouillage strict des dépendances et la vérification des sommes de
  contrôle Gradle. Toute nouvelle dépendance exige une mise à jour contrôlée des fichiers
  de vérification.
- AGP 9.2.1 n'expose ici que les tests JVM de la variante debug. `qualityCheck` utilise
  donc `testDebugUnitTest` et `koverVerifyDebug`, tout en conservant `detektRelease` et
  `lintRelease` pour contrôler le code destiné à l'APK.
- Kover mesure uniquement les classes de calcul pur. Le contrôle impose au minimum 90 %
  de lignes et 80 % de branches couvertes ; le code Android et le code `RemoteViews` ne
  doivent pas servir à produire une revendication de couverture artificielle.

## Règles de modification

- Préserver l'architecture du widget et les trois réglages définis pour l'activité.
- Ne pas ajouter de réseau, de télémétrie ou de travail périodique en arrière-plan sans demande
  explicite. La persistance autorisée reste limitée à la date cible et aux deux couleurs.
- Garder le calcul de date et le calcul typographique purs, déterministes et couverts par
  des tests JVM.
- Garder également pures et testées la validation de la date cible et la sélection de couleur
  selon le mode système.
- Toute modification du comportement doit mettre à jour les tests et ce document si ses
  garanties changent.
- Ne jamais considérer l'aperçu XML comme le rendu réel : seul `BasicWidgetRenderer` produit les
  valeurs calculées.
- `basic_widget_info.xml` utilise `@layout/widget_content` pour le chargement initial ;
  `widget_preview.xml` sert à l'aperçu statique et ne doit jamais être confondu avec le rendu
  calculé.
- Ne jamais compiler d'APK Android sur la machine locale.

## Validation et livraison obligatoires

Ce dépôt est suivi avec la skill Codex `make-android-widget`.

1. Après une modification, demander l'accord explicite de l'utilisateur avant d'exécuter
   `scripts/check-local`, car cette commande répond aux exigences de ce fichier.
2. Exécuter `scripts/check-local` et corriger toutes les erreurs. Cette vérification lance
   le contrôle du formatage, ktlint, detekt, Android lint, les tests JVM, les seuils Kover et les
   contrôles Gradle stricts, mais ne produit pas d'APK.
3. Ne lancer une compilation distante qu'après un `check-local` réussi et correspondant
   exactement aux changements à livrer.
4. Pour compiler et récupérer l'APK, utiliser uniquement `scripts/make-remote`. Ce script
   gère le commit, le push, le déclenchement manuel de GitHub Actions, le téléchargement et
   la vérification de l'artefact. Si une exécution a été interrompue après le succès de sa CI,
   `scripts/make-remote --resume <run-id>` reprend uniquement cet artefact après avoir vérifié
   son succès et son SHA de commit. En fin de script, s'il trouve exactement un appareil ADB
   autorisé, il désinstalle l'ancienne application, installe le nouvel APK et vérifie le
   package, les versions et le SHA-256 exact de l'APK installé. Utiliser `ADB_SERIAL` pour
   sélectionner explicitement un appareil quand plusieurs sont connectés.
5. Ne pas remplacer ce flux par un `git commit`, `git push`, `gh workflow run` ou une
   construction Gradle locale exécutés directement.
6. Une CI réussie prouve la compilation de l'APK, pas son comportement réel sur appareil.
   Pour la validation finale, installer l'APK sur Android 14 ou plus récent et vérifier :
    l'icône du sélecteur, la taille initiale 2x2, l'absence de troncature, le toucher, le
   redémarrage, la prochaine alarme de 01:00, la persistance des réglages et le basculement
   clair/sombre de l'activité et du widget. Si un appareil ADB autorisé
   est connecté, `make-remote` effectue automatiquement l'installation et la vérification
   cryptographique, mais les essais fonctionnels du widget restent à faire et à rapporter
   séparément.

## Synchronisation de la skill et des scripts

- Tout script modifié dans ce dépôt de widget doit rester neutre et réutilisable : aucun
  identifiant, nom ou comportement propre à Memento Mori ne doit être figé dans une ressource
  réutilisable. Les valeurs propres au projet sont déduites de sa configuration ou fournies par
  variable d'environnement ou argument documenté.
- Après toute modification d'un script du dépôt, la ressource homonyme de la skill
  `make-android-widget` doit être mise à jour à l'octet près, permissions d'exécution comprises.
- Après toute modification, quelle qu'elle soit, de la skill `make-android-widget`, sa totalité
  doit être copiée à l'identique dans `skill/make-android-widget/` dans ce dépôt, sans exclure de
  fichier et en préservant les permissions. Cette copie est versionnée avec le projet afin de
  documenter exactement la skill utilisée.
- `scripts/check-local` vérifie la présence de cette copie, qu'elle n'est pas ignorée par Git et
  que ses scripts sont identiques à ceux du dépôt. Avant livraison, l'agent vérifie aussi la
  totalité de l'arborescence copiée contre la source de la skill.

## État attendu à la fin d'une intervention

Le compte rendu doit distinguer clairement :

- les fichiers modifiés ;
- les contrôles statiques et tests réellement exécutés ;
- le résultat de GitHub Actions, si elle a été lancée ;
- le résultat des essais sur appareil, s'ils ont été effectués ;
- tout point restant non vérifié.

## Constats de validation propres à ce dépôt

- Un rendu qui ne contient pas les trois valeurs calculées n'est pas acceptable. Le widget
  utilise désormais `RemoteViews` directement : il ne dépend plus de la composition ni du
  chargement Glance.
- Une ancienne installation peut conserver le layout de chargement ou un ancien rendu.
  Il faut installer l'APK distant correspondant aux changements, puis vérifier l'instance
  posée sur le bureau ; le succès de `check-local` seul ne prouve pas le rendu sur appareil.
- Le manifeste utilise `android:supportsRtl="true"` pour rendre valides les attributs de
  fin (`TextAlign.End`, alignement `viewEnd`) utilisés par le rendu et son aperçu.
- Le compte rendu doit distinguer le rendu RemoteViews réel, l'aperçu XML, l'APK distant et les
  essais effectivement réalisés sur le téléphone.
