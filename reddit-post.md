# r/RLCraft launch post

## Title

I got tired of rerolling librarians, so I made a mod that adds crafting recipes for max-level enchantment books

---

## How to build the post

Reddit's composer drops uploaded images into a gallery strip at the top, and "move to body"
inserts them wherever the cursor happens to be. Fighting that is a waste of time.

Instead, **build the post in order**: paste a text block, add its image, paste the next block.
Each image lands at the end of what you've written so far, which is where you want it. Never
paste the whole post and then try to insert images into the middle.

Images are in `docs/images/`. Work on desktop, in one sitting — Reddit's draft saving is
unreliable.

---

## STEP 1 — paste this

Every RLCraft run hits the same wall for me. You've done the hard part — killed the dragon, geared up, got a base — and then progression turns into standing at a lectern breaking and replacing it a hundred times, waiting for a librarian to offer Protection IV. It's not difficult, it's just tedious, and it's the part of the game I've quit runs over.

So I built a mod that turns those enchantments into crafting recipes instead.

**Every enchantment gets one recipe, always at max level.** Over a hundred of them, vanilla and modded — including the Advanced and Supreme tiers and the Runes.

## STEP 2 — add image: `SupremeSharpnessV.png`

## STEP 3 — paste this

That's Supreme Sharpness V — two Advanced Sharpness books, black dragon scales, and a Dragon's Eye in the middle.

The recipes are themed to what the enchantment actually does, and they're priced as endgame projects rather than shortcuts. Fire Protection wants blaze rods. Viper wants a hydra heart and a wither skull. Subject P.E. wants heart containers and an enchanted medikit.

## STEP 4 — add image: `ProtectionIV.png`

## STEP 5 — add image: `SubjectPEV.png`

## STEP 6 — paste this

The idea is that all the rare drops sitting unused in your chests become a path to a specific enchantment you actually want, instead of hoping RNG eventually offers it.

**Some details:**

* Full JEI support — look up any enchanted book and the grid is right there
* Works on **both base RLCraft and Dregora**. There's a `PACK_MODE` config option you set to match your pack. This one matters: the two packs ship different versions of SoManyEnchantments and it renamed most of its enchantments between them, so if `PACK_MODE` is wrong most recipes won't show up
* `DIFFICULTY_MODE` (easy/normal/hard) shifts every recipe's cost up or down a tier, so you can rebalance the whole mod with one setting if you think I've priced things badly

## STEP 7 — add image: `Config.png`

## STEP 8 — paste this

It also adds a stage 4 → stage 5 dragon skull upgrade, since stage 5 only spawns in rare underground structures and gates the Dragon's Eye.

## STEP 9 — add image: `DragonSkullStageV.png`

## STEP 10 — paste this (last block)

**CurseForge:** https://www.curseforge.com/minecraft/mc-mods/rlcraft-enchantment-recipes

**Source (MIT):** https://github.com/ExiledRadio/RLCraftEnchantRecipes

I balanced these against my own playthrough, which is a sample size of one — so I'd genuinely like to hear if anything looks mispriced. If a recipe is way too cheap or way too expensive, tell me and I'll adjust it. Same for any enchantments I've missed.

---

## First comment — post immediately after the post goes live

Couple of things worth saying up front:

**This is unofficial** — not affiliated with Shivaxi or the Dregora team, just a player-made addon.

**If recipes aren't showing up, check `PACK_MODE` in the config first.** `dregora` for Dregora, `rlcraft` for base RLCraft. That's almost always the cause. If it's still broken after that, your log will have `Could not find ...` lines naming exactly what's missing — send me those and I'll take a look.

---

## If the editor keeps fighting you

Drop the inline images entirely. Make it a **gallery post** — title plus the five images in the
order above — and paste STEPS 1, 3, 6, 8 and 10 as one block in the **first comment**.

Slightly worse, because the writeup is one click away, but plenty of mod releases are posted that
way and it takes two minutes instead of twenty. Not worth losing an evening over.

---

## Before posting

- [ ] CurseForge page loads in a private/incognito window
- [ ] Decide 1.0.0 vs 1.0.1 — if 1.0.1 is approved and only needs the publish click, publish it
      first. The link doesn't change either way
- [ ] Flair the post — check what similar mod posts on the sub use
- [ ] Post when the sub is active, generally US evening
- [ ] Stay around the first couple of hours. Early replies drive visibility, and rule 1 means you
      only get one shot
