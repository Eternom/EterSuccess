# EterSuccess

Les **succès du réseau** (ceux de Minecraft sont désactivés par EterSync) : **Premiers pas**, une suite d'étapes qui
guide les nouveaux joueurs, puis plus d'une centaine de succès à 5 niveaux, faits pour être longs (jusqu'à un million de
pierres). Document développeur, à tenir à jour avec le code.

## Prérequis

- **EterLib 1.14.0+** (`depend`) : base, langues, menus, `Numbers`, `RankingSource` et `PlacedBlocks` (seuls les blocs naturels comptent).
- **EterEconomy** (`depend`, son API `EconomyApi`) : l'argent des récompenses, et l'état « argent ».
- Facultatifs (`softdepend`), chacun lu dans sa propre classe, chargée seulement s'il est là (`States`, `NetworkListeners`) :
  - **EterMarket 1.5.0+** : `JobQuestCompleteEvent` (succès de quêtes) et l'état « métier » (`MarketApi`) ;
  - **EterReward 1.3.0+** : `ChallengeCompleteEvent` (succès de défis), palier du pass et `/daily` (`RewardApi`) ;
  - **EterResource** : un monde ressource (arrivées) ;
  - **EterHome** : nombre de homes ; **EterClan** : membre d'un clan.

## Fonctionnement

**Catalogue** (`AchievementCatalog`, `achievements.yml` copié au premier démarrage) : les catégories dans l'ordre du menu,
puis les succès. Un succès = ce qu'il compte (`kind` + `match`/`tag`), 1 à 5 niveaux (`tiers`), une icône, et
éventuellement ses récompenses (sinon `config.yml > tiers`). Le fichier fourni est généré (120 succès, 563 niveaux) : pour
en ajouter, suivre le même modèle. **Ne jamais renommer un succès** : sa progression est gardée sous son identifiant.

**Comptage** (`SuccessListener`, en survie) :
- `break` : blocs **naturels** seulement (un bloc posé par un joueur ne compte pas, EterLib `PlacedBlocks`) ;
- `harvest` : cultures mûres ; `kill` : créatures tuées par le joueur (`#monsters` = les `Enemy`) ; `fish` (tag : `fish`,
  `treasure`, `junk`) ; `breed` ; `enchant` ; `death` ;
- `playtime` : minutes où le joueur a bougé, agi ou parlé ; `resource` : arrivées sur un monde ressource ;
- `quest` et `challenge` : événements d'EterMarket et d'EterReward ; `sell` et `buy` : `AuctionSaleEvent` d'EterMarket (le vendeur
  est compté par son UUID, même hors ligne) ; `daily-series` : `DailyClaimEvent` d'EterReward (jour 7).

Chaque action s'ajoute en mémoire (`compute` atomique avec la vidange), puis part en base toutes les 20 s et au départ
(une requête relative par succès, `SuccessStore#add`). Les **états** (`job`, `homes`, `money`, `clan`, `clan-land`
(chunks du clan), `pass-tier`, `daily`) sont relus chaque minute pour les joueurs connectés : la plus haute valeur vue est gardée (`raise`).

**Débloquer** (`SuccessService#check`) : chaque niveau atteint est inscrit par `INSERT IGNORE` dans `etersuccess_tiers` :
récompense donnée une seule fois, même avec plusieurs serveurs. L'argent part tout de suite en tâche de fond, puis les
commandes, le message et le son sur le thread principal. Chaque niveau débloqué (sauf les étapes des Premiers pas) est annoncé à tous, à la place des progrès de Minecraft : `announce` (`network` par le bus `etersuccess`, type `unlock`, `server` ou `none`) ; le niveau V a son message (`unlock.legend`). Événement
`AchievementUnlockEvent` pour les autres plugins.

**Premiers pas** (catégorie `guided`) : 9 étapes (métier, home, `/daily`, quête, monde ressource, défi, 1 000 Heloks, clan,
palier 3 du pass), chacune avec un indice (`hint.<id>`). À l'arrivée et après chaque étape, l'indice de la prochaine est
envoyé, avec un bouton vers `/succes`. La dernière donne 2 clés de ressource (commande `eterresource givekey`).

**Sidebar** (EterTab, étiquette `success`) : l'étape des Premiers pas tant qu'ils ne sont pas finis, puis les points de
succès ; à l'arrivée et à chaque déblocage.

**Menus** : `/succes` (le joueur avec ses points, les catégories avec leur avancée), puis une catégorie (28 succès par
page : étoiles du niveau, description et barre d'avancée vers le prochain niveau, sa récompense ; indices pour Premiers
pas). Le nom d'un succès est celui du bloc ou de la créature, **traduit par le jeu dans la langue du joueur**, sinon
`name.<id>` ou `subject.<sujet>` ; sa description vient de `kind.<kind>`.

## Commandes et permissions

| Commande | Permission | Par défaut |
|---|---|---|
| `/succes` (`success`, `achievements`) | `etersuccess.use` | tous |

`etersuccess.admin` regroupe tout.

## Données

- `etersuccess_progress` : `uuid`, `achievement`, `amount` (compteur, ou plus haute valeur vue pour un état).
- `etersuccess_tiers` : `uuid`, `achievement`, `tier`, `points`, `unlocked_at` (clé : les trois premiers).

## API (pour les autres plugins)

`fr.eternom.eterSuccess.api.SuccessApi` (`SuccessApi.get()`) : personne d'autre ne lit les tables `etersuccess_*`.

- `points(uuid)`, `tier(uuid, succès)`, `top(n)`, `rank(uuid)` (bloquants) ; `openMenu(joueur)` : le menu `/succes` ;
- source de classement `success` (EterLib `RankingSource`) : les points, pour `/classement` d'EterReward ;
- événement `AchievementUnlockEvent` (joueur, succès, niveau, points).
