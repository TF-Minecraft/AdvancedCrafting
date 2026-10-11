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
  access and experience rewards. Crafting XP scales with how well the piece
  was worked.
- **d20 craft roll** — like `/roll`, finishing a piece with less than 100% hits
  rolls a d20 plus a modifier from the crafter's base Strength. A roll of 12 or
  more gives a hint about the hit that was closest to right (more or less of
  it, never the count); a natural 20 always succeeds and a natural 1 always
  fails. The roll never changes the piece: 100% hits is always a Masterwork.

## Credits

Originally authored by **Drefvelin**.

## Documentation

[Project documentation](https://github.com/TF-Minecraft/Docs/blob/main/projects/AdvancedCrafting/README.md)

Technical documentation is maintained in [TF-Minecraft/Docs](https://github.com/TF-Minecraft/Docs).

## Tests and coverage

Use Java 21 and Maven, and prepare the shared and private dependencies in the
[build guide](https://github.com/TF-Minecraft/Docs/blob/main/projects/AdvancedCrafting/setup.md#build-from-source), then run:

```sh
mvn -B --no-transfer-progress clean verify -DskipTests=false -Dmaven.test.skip=false
```

The JUnit 5 suite uses MockBukkit for server state and Mockito at external plugin
boundaries. JaCoCo measures every production class; no production packages,
classes, or methods are excluded. `verify` requires **100% instruction, branch,
and line coverage**, and fails when any counter falls below that threshold.
Surefire test results are in `target/surefire-reports/`. The JaCoCo HTML report is
`target/site/jacoco/index.html`; XML/CSV are alongside it.
Build and release CI upload the coverage report as an artifact, including failed
runs when a report was generated.

Coverage proves the exercised Java behavior. It does not replace a live Paper
server integration check with the pinned ItemsAdder/MMOItems/TLibs versions.

## License

Copyright (c) 2026 TF-Minecraft contributors.

TF-Minecraft-authored material in this repository is licensed under the
[Artistic License 2.0](LICENSE). Third-party dependencies and bundled material
retain their own licenses.
