# AdvancedCrafting

> Materials, smithing, and alloy discovery for TF-Minecraft.

AdvancedCrafting turns equipment crafting into a hands-on workshop activity.
Players prepare ingredients, combine materials, and work recipes at crafting
stations. The materials they choose and the quality of their work shape the
finished item.

## Features

- **Crafting stations** — make items through recipes with material requirements
  and sequences of tool hits.
- **Ingredient conversion** — turn supported items into crafting materials with
  their own properties.
- **Alloy discovery** — experiment with ingredients and catalysts, name newly
  discovered alloys, and reproduce known combinations.
- **Material-driven equipment** — carry ingredient stats and crafting quality
  into the finished item's attributes through MMOItems.
- **Crafting identity** — retain the recipe, materials, and quality behind a
  crafted item, with support for naming, appearance, and branding.
- **Profession progression** — connect recipes and materials to profession
  access and experience rewards.

## Staff equipment grants

`/ac give equipment <recipe> <ingredient.id|alloy.id> [player] [quality]`
gives one finished item using a configured recipe and compatible main material.
For example, `/ac give equipment heavy_chestplate ingredient.steel_ingot Alex 100`.
Tab completion lists loaded recipes, compatible ingredients/alloys and online players.
Omit the player to give to yourself; console must specify an online player.
Quality defaults to 100 and accepts finite values from 0 to 100 (specify the player
before quality). Alloy IDs are the loaded discovery IDs, not display names.

Secondary recipe ingredients use the lowest configured tier, then ingredient ID
alphabetically to break ties. Their stats and appearance participate normally.
The item retains normal stats, quality sockets, appearance, tier and crafting
provenance; no materials are consumed and no XP or activity rewards are granted.
Full inventories drop the item at the recipient's location.

Set `give-equipment-permission` in `config.yml` and run `/ac reload` to change
access. Missing or blank settings default to `advancedcrafting.admin` (operators).
Grant a custom permission only to staff; it does not grant other admin commands.

## Credits

Originally authored by **Drefvelin**.

## Documentation

[Project documentation](https://github.com/TF-Minecraft/Docs/blob/main/projects/AdvancedCrafting/README.md)

Technical documentation is maintained in [TF-Minecraft/Docs](https://github.com/TF-Minecraft/Docs).

## Tests and coverage

Use Java 21 and Maven 3.9. Install the pinned plugin dependencies with the
repository's existing `.github/scripts/prepare-release.sh` workflow, then run:

```sh
mvn -B --no-transfer-progress clean verify -DskipTests=false -Dmaven.test.skip=false
```

The JUnit 5 suite uses MockBukkit for server state and Mockito at external plugin
boundaries. JaCoCo measures every production class; no production packages,
classes, or methods are excluded. `verify` requires **100% instruction, branch,
and line coverage**, and fails when any counter falls below that threshold.
The HTML report is `target/site/jacoco/index.html`; XML/CSV are alongside it.
Build and release CI upload the coverage report as an artifact, including failed
runs when a report was generated.

Coverage proves the exercised Java behavior. It does not replace a live Paper
server integration check with the pinned ItemsAdder/MMOItems/TLibs versions.

## License

Copyright (c) 2026 TF-Minecraft contributors.

TF-Minecraft-authored material in this repository is licensed under the
[Artistic License 2.0](LICENSE). Third-party dependencies and bundled material
retain their own licenses.
