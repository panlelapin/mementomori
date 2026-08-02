# Instructions pour les agents LLM

Ces instructions remplacent toutes les instructions `AGENTS.md` précédemment fournies.

## Finalité du projet

Ce dépôt contient **Memento Mori**, un widget Android d'écran d'accueil écrit en Kotlin.
L'application est volontairement limitée au widget : elle ne contient aucune activité de
lancement, aucun écran de configuration et aucun service permanent.

Le widget affiche le temps restant jusqu'au **17 mars 2036** sous la forme de trois
intervalles indépendants et entiers :

```text
<années>a
<mois>m
<semaines>s
```

Exemple : `10a`, `120m`, `521s`. Chaque valeur est calculée directement entre la date
locale du jour et la date cible avec `ChronoUnit.YEARS`, `ChronoUnit.MONTHS` et
`ChronoUnit.WEEKS`. Les trois nombres ne sont donc pas les composantes successives d'une
durée décomposée. Aucun état de compteur n'est persisté.

## Contraintes fonctionnelles

- La date cible est `2036-03-17` et doit rester centralisée dans `TARGET_DATE`.
- Le calcul utilise `LocalDate.now()` : seule la date civile locale compte.
- Le rendu comporte exactement trois lignes, dans l'ordre années, mois, semaines.
- Les suffixes sont respectivement `a`, `m` et `s`, précédés visuellement d'une espace fine
  Unicode `U+2009` : par exemple `10 a`, `120 m`, `521 s`.
- Le widget est recalculé et redessiné :
  - lors de son ajout ou d'une mise à jour demandée par le lanceur ;
  - après le démarrage complet du téléphone ;
  - après le remplacement de l'APK ;
  - après une modification manuelle de l'heure ou du fuseau horaire ;
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

- Taille cible et taille minimale : **1 colonne × 2 lignes** (`1x2`).
- Fond entièrement transparent.
- Texte blanc, en police monospace explicitement demandée par Glance, sans graisse forcée.
  Avec Glance/RemoteViews, demander une graisse moyenne peut sélectionner une police de
  remplacement proportionnelle selon le lanceur ; la priorité est donc l'espacement strictement
  monospace.
  Les trois lignes sont alignées à droite dans toute la largeur disponible et centrées
  verticalement dans le widget.
- L'ensemble de la surface est cliquable et déclenche un recalcul suivi d'un nouveau
  rendu.
- La taille de police n'est jamais une constante visuelle. Elle est calculée depuis la
  taille réelle fournie par `LocalSize`, le facteur de police Android, les marges et la
  longueur de la ligne la plus longue, puis agrandie par un facteur visuel de 1,5.
- Le calcul doit employer des coefficients conservateurs pour la largeur d'un glyphe
  monospace et la hauteur d'une ligne. Les trois lignes doivent toujours rester visibles,
  complètes, sur une seule ligne chacune, sans coupure ni retour à la ligne.
- Le widget peut être agrandi jusqu'à `4x4` environ ; la police doit alors s'agrandir avec
  lui. `SizeMode.Exact` est requis pour recomposer selon ses dimensions réelles.
- L'aperçu statique du sélecteur ne réalise aucun calcul, mais doit rester visuellement
  fidèle au rendu réel : police monospace, alignement à droite, espace fine et valeurs
  représentatives `10 a`, `120 m`, `521 s`. Il ne doit pas utiliser `...` ni de faux zéros.

## Icône

L'icône officielle est le sablier blanc sur fond anthracite. Le manifeste doit utiliser
la même ressource adaptative `@mipmap/app_icon` pour `android:icon` et
`android:roundIcon`, afin que l'icône affichée dans le sélecteur de widgets soit la même
que celle de l'application. Ne pas revenir à un simple tracé blanc sur fond transparent :
il devient invisible ou entièrement blanc sur certains lanceurs.

## Architecture et fichiers importants

- `app/src/main/java/com/github/panlelapin/mementomori/BasicWidget.kt` contient le rendu
  Glance, le receiver AppWidget et l'action exécutée au toucher.
- `app/src/main/java/com/github/panlelapin/mementomori/DailyUpdateReceiver.kt` contient le
  receiver des événements temporels, la gestion de l'alarme et le calcul pur de la
  prochaine heure locale de 01:00.
- `app/src/main/java/com/github/panlelapin/mementomori/CountdownCalculator.kt` contient la
  date cible et le calcul pur des intervalles.
- `app/src/main/java/com/github/panlelapin/mementomori/WidgetFontSizeCalculator.kt` contient
  le calcul pur de la taille de police.
- `app/src/main/AndroidManifest.xml` déclare les deux receivers et la permission
  `RECEIVE_BOOT_COMPLETED`. Les receivers restent non exportés.
- `app/src/main/res/xml/basic_widget_info.xml` décrit les dimensions, le redimensionnement
  et l'aperçu du widget.
- `app/src/main/res/layout/widget_preview.xml` est uniquement l'aperçu statique du
  sélecteur ; il ne réalise aucun calcul.
- `app/src/main/res/mipmap-anydpi-v26/app_icon.xml` est l'icône adaptative Android.
- `app/src/test/` contient les tests JVM du calcul de date et de la taille de police.

## Socle Android

- Kotlin et Jetpack Glance, sans activité.
- `applicationId` et namespace : `com.github.panlelapin.mementomori`.
- `minSdk = 34` (Android 14), `targetSdk = 36`, `compileSdk = 36`.
- Java/Kotlin JVM 17.
- `androidx.glance:glance-appwidget:1.1.1` est utilisé avec un forçage explicite de
  `androidx.work:work-runtime:2.11.2`. La version transitive 2.7.1 provoquait sur le
  téléphone de validation un crash `Failed to create an instance of
  androidx.work.impl.WorkDatabase`, laissant Glance afficher indéfiniment son cercle de
  chargement. Toute modification de cette version exige la mise à jour contrôlée de la
  vérification Gradle.
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
  de lignes et 80 % de branches couvertes ; le code Android et le code Glance généré ne
  doivent pas servir à produire une revendication de couverture artificielle.

## Règles de modification

- Préserver l'architecture sans activité et sans interface supplémentaire.
- Ne pas ajouter de réseau, de télémétrie, de stockage persistant ou de travail périodique
  en arrière-plan sans demande explicite.
- Garder le calcul de date et le calcul typographique purs, déterministes et couverts par
  des tests JVM.
- Toute modification du comportement doit mettre à jour les tests et ce document si ses
  garanties changent.
- Ne jamais considérer l'aperçu XML comme le rendu réel : seul Glance produit les valeurs
  calculées.
- `basic_widget_info.xml` utilise `@layout/glance_default_loading_layout` pour le chargement
  initial ; `widget_preview.xml` sert à l'aperçu statique et ne doit jamais être confondu
  avec le rendu Glance.
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
   la vérification de l'artefact. En fin de script, s'il trouve exactement un appareil ADB
   autorisé, il désinstalle l'ancienne application, installe le nouvel APK et vérifie le
   package, les versions et le SHA-256 exact de l'APK installé. Utiliser `ADB_SERIAL` pour
   sélectionner explicitement un appareil quand plusieurs sont connectés.
5. Ne pas remplacer ce flux par un `git commit`, `git push`, `gh workflow run` ou une
   construction Gradle locale exécutés directement.
6. Une CI réussie prouve la compilation de l'APK, pas son comportement réel sur appareil.
   Pour la validation finale, installer l'APK sur Android 14 ou plus récent et vérifier :
   l'icône du sélecteur, la taille initiale 1x2, l'absence de troncature, le toucher, le
   redémarrage et la présence de la prochaine alarme de 01:00. Si un appareil ADB autorisé
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

- Un écran contenant le cercle fléché de chargement Glance signifie que la composition ne
  s'est pas terminée ; ce n'est pas un aperçu acceptable du widget. Sur le téléphone de
  validation, la cause était le crash de WorkManager 2.7.1 lors de la création de
  `WorkDatabase`, corrigé par le forçage de WorkManager 2.11.2.
- Une ancienne installation peut conserver le layout de chargement ou un ancien rendu.
  Il faut installer l'APK distant correspondant aux changements, puis vérifier l'instance
  posée sur le bureau ; le succès de `check-local` seul ne prouve pas le rendu sur appareil.
- Le manifeste utilise `android:supportsRtl="true"` pour rendre valides les attributs de
  fin (`TextAlign.End`, alignement `viewEnd`) utilisés par le rendu et son aperçu.
- Le compte rendu doit distinguer le rendu Glance réel, l'aperçu XML, l'APK distant et les
  essais effectivement réalisés sur le téléphone.
