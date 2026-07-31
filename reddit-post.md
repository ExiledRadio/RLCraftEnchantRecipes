# r/RLCraft launch post — final (text post with inline images)

## How to post

1. New post → **Text** (not Images & Video)
2. Use the **Fancy Pants** editor, not Markdown Mode — you need the image button
3. Paste each text block below in order, and use the image icon in the toolbar to insert the
   named screenshot at each `[IMAGE: ...]` marker
4. Delete the markers themselves — they're instructions, not post content
5. Screenshots are in `docs/images/`

If pasting from this file mangles the formatting, switch to Markdown Mode, paste the text, then
switch back to Fancy Pants — the markdown converts, and then you can insert images.

---

## Title

> I got tired of rerolling librarians, so I made a mod that adds crafting recipes for max-level
> enchantment books

---

## Post body

Every RLCraft run hits the same wall for me. You've done the hard part — killed the dragon, geared up, got a base — and then progression turns into standing at a lectern breaking and replacing it a hundred times, waiting for a librarian to offer Protection IV. It's not difficult, it's just tedious, and it's the part of the game I've quit runs over.

So I built a mod that turns those enchantments into crafting recipes instead.

**Every enchantment gets one recipe, always at max level.** Over a hundred of them, vanilla and modded — including the Advanced and Supreme tiers and the Runes.

`[IMAGE: SupremeSharpnessV.png]`

That's Supreme Sharpness V — two Advanced Sharpness books, black dragon scales, and a Dragon's Eye in the middle.

The recipes are themed to what the enchantment actually does, and they're priced as endgame projects rather than shortcuts. Fire Protection wants blaze rods. Viper wants a hydra heart and a wither skull. Subject P.E. wants heart containers and an enchanted medikit.

`[IMAGE: ProtectionIV.png]`

`[IMAGE: SubjectPEV.png]`

The idea is that all the rare drops sitting unused in your chests become a path to a specific enchantment you actually want, instead of hoping RNG eventually offers it.

**Some details:**

* Full JEI support — look up any enchanted book and the grid is right there
* Works on **both base RLCraft and Dregora**. There's a `PACK_MODE` config option you set to match your pack. This one matters: the two packs ship different versions of SoManyEnchantments and it renamed most of its enchantments between them, so if `PACK_MODE` is wrong most recipes won't show up
* `DIFFICULTY_MODE` (easy/normal/hard) shifts every recipe's cost up or down a tier, so you can rebalance the whole mod with one setting if you think I've priced things badly

`[IMAGE: Config.png]`

It also adds a stage 4 → stage 5 dragon skull upgrade, since stage 5 only spawns in rare underground structures and gates the Dragon's Eye.

`[IMAGE: DragonSkullStageV.png]`

**CurseForge:** https://www.curseforge.com/minecraft/mc-mods/rlcraft-enchantment-recipes

**Source (MIT):** https://github.com/ExiledRadio/RLCraftEnchantRecipes

I balanced these against my own playthrough, which is a sample size of one — so I'd genuinely like to hear if anything looks mispriced. If a recipe is way too cheap or way too expensive, tell me and I'll adjust it. Same for any enchantments I've missed.

---

## First comment (post immediately after)

Couple of things worth saying up front:

**This is unofficial** — not affiliated with Shivaxi or the Dregora team, just a player-made addon.

**If recipes aren't showing up, check `PACK_MODE` in the config first.** `dregora` for Dregora, `rlcraft` for base RLCraft. That's almost always the cause. If it's still broken after that, your log will have `Could not find ...` lines naming exactly what's missing — send me those and I'll take a look.

---

## Before posting

- [ ] CurseForge page loads in a private/incognito window
- [ ] Decide 1.0.0 vs 1.0.1 — if 1.0.1 is approved and only needs the publish click, publish it
      first. The link doesn't change either way, and the page always serves the newest published
      file
- [ ] Flair the post — check what similar mod posts on the sub use
- [ ] Post when the sub is active, generally US evening
- [ ] Stay around the first couple of hours. Early comment replies drive visibility more than
      anything else, and rule 1 means you only get one shot
