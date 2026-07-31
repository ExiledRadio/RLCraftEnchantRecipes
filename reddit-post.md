# r/RLCraft launch post — final

**Post type:** Image gallery. Upload 5–6 recipe screenshots from `docs/images/` as the gallery,
then paste the body below. Gallery posts outperform text posts on this sub, and the pixel-art
grids are the most immediately readable thing about the mod.

**Suggested image order** (first image is the thumbnail everyone sees in feed — lead with the
most visually interesting):
1. `SupremeSharpnessV.png` — dragon scales and a Dragon's Eye, most eye-catching
2. `ProtectionIV.png` — instantly recognisable, full diamond set
3. `StrengthenedVitalityV.png` — ender dragon head
4. `SubjectPEV.png` — hearts, visually distinct
5. `DragonSkullStageV.png` — the non-book recipe, good talking point
6. `Config.png` — shows it's configurable

---

## Title

> I got tired of rerolling librarians, so I made a mod that adds crafting recipes for max-level
> enchantment books

---

## Body

Every RLCraft run hits the same wall for me. You've done the hard part — killed the dragon,
geared up, got a base — and then progression turns into standing at a lectern breaking and
replacing it a hundred times waiting for a librarian to offer Protection IV. It's not difficult,
it's just tedious, and it's the part of the game I've quit runs over.

So I built a mod that turns those enchantments into crafting recipes instead.

**Every enchantment gets one recipe, always at max level.** Over a hundred of them, vanilla and
modded — including the Advanced and Supreme tiers and the Runes.

The recipes are themed to what the enchantment actually does, and they're priced as endgame
projects rather than shortcuts:

- **Fire Protection IV** — blaze rods and diamond armour
- **Viper V** — a hydra heart and a wither skull
- **Supreme Sharpness V** — black dragon scales, a Dragon's Eye, and two Advanced Sharpness books
- **Subject P.E. V** — heart containers and an enchanted medikit

The idea is that all the rare drops sitting unused in your chests become the path to a specific
enchantment you actually want, instead of hoping RNG eventually offers it.

**Some details:**

- Full JEI support — look up any enchanted book and the grid is right there
- Works on **both base RLCraft and Dregora**. There's a `PACK_MODE` config option you set to match
  your pack. This one matters: the two packs ship different versions of SoManyEnchantments and it
  renamed most of its enchantments between them, so if `PACK_MODE` is wrong most recipes won't
  show up
- `DIFFICULTY_MODE` (easy/normal/hard) shifts every recipe's cost up or down a tier, so you can
  rebalance the whole mod with one setting if you think I've priced things badly
- Also adds a stage 4 → stage 5 dragon skull upgrade, since stage 5 only spawns in rare
  underground structures and gates the Dragon's Eye

**CurseForge:** https://www.curseforge.com/minecraft/mc-mods/rlcraft-enchantment-recipes
**Source (MIT):** https://github.com/ExiledRadio/RLCraftEnchantRecipes

I balanced these against my own playthrough, which is a sample size of one — so I'd genuinely
like to hear if anything looks mispriced. If a recipe is way too cheap or way too expensive, tell
me and I'll adjust it. Same for enchantments I've missed.

---

## First comment (post immediately after, helps with the two questions everyone asks)

> Couple of things worth saying up front:
>
> **This is unofficial** — not affiliated with Shivaxi or the Dregora team, just a player-made
> addon.
>
> **If recipes aren't showing up, check `PACK_MODE` in the config first.** `dregora` for Dregora,
> `rlcraft` for base RLCraft. That's almost always the cause. If it's still broken after that,
> your log will have `Could not find ...` lines naming exactly what's missing — send me those and
> I'll take a look.

---

## Before posting

- [ ] Confirm the CurseForge page loads in a private/incognito window (if it doesn't, it isn't
      public yet)
- [ ] Confirm 1.0.1 is published, not just approved
- [ ] Flair the post — check what similar mod posts on the sub use
- [ ] Post when the sub is active, generally US evening
- [ ] Stay around for the first couple of hours. Early comment replies drive visibility more than
      anything else, and rule 1 means you only get one shot at this post
