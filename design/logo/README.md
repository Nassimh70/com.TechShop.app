# TechShop Manager — identité visuelle

Logo et icône d'application de **TechShop Manager**. Le système est volontairement
sobre : un monogramme unique, une palette indigo, une typographie Inter. Il reprend
les jetons déjà utilisés par l'interface (`#4F46E5`, `#0F172A`, Inter) pour que la
marque et le produit ne fassent qu'un.

Planche de présentation : [`preview.png`](preview.png).

---

## 1. Le symbole

La tuile est un **squircle indigo** (rayon de 116 sur 512, soit ≈ 22,7 %) qui reprend
le langage des icônes macOS/Windows modernes. À l'intérieur, un **T blanc** construit
géométriquement :

- barre horizontale de 280 × 76 à extrémités pleinement arrondies (rayon 38) ;
- hampe de 80 × 248, coins inférieurs arrondis (rayon 30) ;
- à partir de 128 px, un **trou en forme d'indicateur d'accueil** (30 × 6) laisse
  voir la tuile indigo et creuse le bas de la hampe : le T devient discrètement un
  smartphone. En dessous de 128 px, ce détail est omis pour rester net.

Le T se lit immédiatement à toutes les tailles ; le téléphone n'apparaît qu'au
second regard. C'est ce qui distingue le logo d'un simple lettermark générique.

La tuile reçoit trois couches : dégradé `#7477F7 → #4F46E5 → #3730A3`, halo lumineux
en haut à gauche, ombre interne en bas, plus un liseré blanc à 18 % pour détacher
l'icône d'un fond indigo (écran de connexion).

## 2. Palette

| Rôle | Valeur | Usage |
| --- | --- | --- |
| Indigo 400 | `#7477F7` | Haut du dégradé, accents lumineux |
| **Indigo 500** | **`#4F46E5`** | Couleur primaire : tuile, « Manager », actions |
| Indigo 700 | `#3730A3` | Bas du dégradé, profondeur |
| Indigo 300 | `#A5B4FC` | « Manager » sur fond sombre |
| Encre | `#0F172A` | « TechShop », texte principal |
| Ardoise | `#64748B` | Sous-titre, texte secondaire |
| Papier | `#F8FAFC` | « TechShop » sur fond sombre |
| Nuit | `#0B1120` | Fonds sombres de la planche |

Le dégradé de la tuile est le seul dégradé de la marque. Ne pas en inventer
d'autres, ne pas modifier l'angle ni l'ordre des couleurs.

## 3. Typographie

**Inter** (fichiers embarqués dans `src/main/resources/fonts/`).

| Élément | Graisse | Taille (lockup 915 px) | Détail |
| --- | --- | --- | --- |
| `TechShop` | Bold 700 | 68 px | Encre, approche normale |
| `Manager` | Medium 500 | 68 px | Indigo 500, 15 px après « TechShop » |
| `GESTION COMMERCIALE` | Medium 500 | 17 px | Interlettrage 3,4 px (0,2 em), ardoise |

Le mot « Manager » commence toujours à la même distance du « p » de « TechShop » :
la césure est optique, pas mécanique. Le sous-titre est calé à 2 px près sur le
`T` de « TechShop » (et non sur le bord de la tuile).

## 4. Fichiers livrés

### Sources vectorielles (édition, impression, web)

| Fichier | Dimensions | Usage |
| --- | --- | --- |
| `techshop-mark.svg` | 512 × 512 | Icône seule, favicon, avatar, impression |
| `techshop-logo-light.svg` | 915 × 232 | Lockup complet sur fond clair |
| `techshop-logo-dark.svg` | 915 × 232 | Lockup complet sur fond sombre |

### Exports PNG

| Fichier | Taille | Usage |
| --- | --- | --- |
| `png/techshop-icon-{16,20,24,32,40,48,64,96,128,180,256,512,1024}.png` | 1:1 | Icônes Windows, web, store, documentation |
| `png/techshop-logo-light.png` | 915 × 232 | Lockup clair, 1× |
| `png/techshop-logo-light@2x.png` | 1830 × 464 | Lockup clair, HiDPI |
| `png/techshop-logo-dark.png` | 915 × 232 | Lockup sombre, 1× |
| `png/techshop-logo-dark@2x.png` | 1830 × 464 | Lockup sombre, HiDPI |

### Windows

| Fichier | Contenu |
| --- | --- |
| `techshop.ico` | 9 résolutions : 16, 20, 24, 32, 40, 48, 64, 128 (BMP) + 256 (PNG) |

### Divers

| Fichier | Rôle |
| --- | --- |
| `preview.png` | Planche de présentation (fonds clair/sombre, gammes d'icônes, palette) |
| `generate/LogoGenerator.java` | Générateur de tous les fichiers ci-dessus |

Les PNG sont **la référence** : chaque taille est dessinée à sa résolution finale
(détail adapté, suréchantillonnage), et non redimensionnée depuis un master.

## 5. Règles d'usage

### Taille minimale

| Version | Numérique | Impression |
| --- | --- | --- |
| Monogramme seul | 16 px | 8 mm |
| Lockup complet | 480 px de large | 55 mm |

En dessous de 480 px, le sous-titre n'est plus lisible : utiliser le monogramme
seul. Le lockup n'est jamais réduit pour « tenir » dans un espace trop petit.

### Espace de respiration

Le fichier lockup intègre 16 px de marge. En composition, réserver au minimum
**32 px** (≈ 0,2 × la hauteur de la tuile, soit 35 px à taille native) entre le
logo et tout autre élément : bord, texte, bouton, filet. Pour l'icône seule,
prévoir **25 % de sa hauteur** de chaque côté.

### Fonds autorisés

| Variante | Fonds |
| --- | --- |
| Claire | blanc, `#F8FAFC`, gris très clairs, photos peu contrastées |
| Sombre | `#0B1120`, `#0F172A`, `#111C2E` |
| Icône | indifférent : la tuile possède son propre fond |

Sur un fond indigo proche de `#4F46E5`, utiliser l'icône (le liseré et le dégradé
la détachent), pas le monogramme nu.

### À faire

- Conserver les proportions et l'alignement du fichier d'origine.
- Utiliser la variante claire ou sombre selon le fond, sans la recolorer.
- Laisser le T blanc sur sa tuile : c'est un bloc indissociable.

### À éviter

- Étirer, incliner, faire pivoter ou ajouter une ombre portée au logo.
- Changer les couleurs de la tuile ou du T, ou remplacer le T par une police.
- Poser le lockup clair sur un fond sombre (et inversement).
- Recomposer « TechShop Manager » dans une autre police ou avec un autre espacement.
- Utiliser le détail « smartphone » sous 128 px : les exports le gèrent déjà.

## 6. Intégration dans l'application

Les ressources d'exécution sont copiées dans
`src/main/resources/images/brand/` :

- `app-icon-{16,24,32,48,64,128,256}.png` — icônes de fenêtre et barre des tâches ;
- `techshop-mark-96.png` — monogramme T simple pour les badges (≤ 48 px) ;
- `techshop-mark-256.png` — monogramme avec indicateur d'accueil (splash, grands affichages) ;
- `techshop.ico` — icône de l'installeur MSI.

Points d'accroche :

| Emplacement | Code |
| --- | --- |
| Chargement du monogramme / icônes | `com.techshop.ui.Branding` |
| Barre latérale (36 px) | `MainController#initialize` → `Branding.mount(logoBadge, 36)` |
| Connexion (36 px) | `LoginController` → `Branding.mount(brandIcon, 36)` |
| Changement de mot de passe (36 px) | `ChangePasswordController` → `Branding.mount(brandIcon, 36)` |
| Splash (72 px) + icônes de fenêtre | `AppShell` → `Branding.mark(72)`, `Branding.applyIcons(stage)` |
| Installeur Windows | `pom.xml`, profil jpackage : `<icon>…/images/brand/techshop.ico</icon>` |

`Branding.mount` rend le badge transparent : le PNG contient déjà sa tuile indigo,
il ne faut pas de fond CSS derrière (sinon double arrondi visible). `Branding.mark`
choisit automatiquement le master au T simple pour les petits badges et le master
détaillé pour les grands affichages.

## 7. Régénérer les fichiers

Depuis la racine du projet, avec un JDK 17+ :

```bash
java -Djava.awt.headless=true design/logo/generate/LogoGenerator.java
```

Le générateur n'a **aucune dépendance** : Java2D uniquement. Il écrit les SVG, les
PNG, le `.ico`, la planche, puis copie les ressources dans
`src/main/resources/images/brand/`.

Après régénération :

```bash
mvn -q -DskipTests compile
```

### Notes techniques

- Les SVG contiennent du **texte réel** (`<text>`) : pour un rendu identique au
  PNG, Inter doit être installée sur la machine (les TTF sont fournis dans
  `src/main/resources/fonts/`). Sinon le navigateur/éditeur substitue une police
  et la largeur du lockup varie légèrement. Les PNG restent la référence.
- Les coordonnées SVG sont écrites avec `Locale.ROOT`, jamais avec la locale
  système : un `,` décimal français casserait les attributs.
- Le `.ico` est en little-endian et mélange BMP (≤ 128 px) et PNG (256 px), comme
  attendu par Windows.
- Le détail de l'icône dépend de la taille : T simple dans les exports jusqu'à
  96 px, indicateur d'accueil à partir de 128 px. Dans l'interface, le badge
  36 px utilise le master au T simple et le splash 72 px le master détaillé
  (rendu en 2×).
