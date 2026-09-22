# AdvancedCrafting

Minecraft crafting and alloy plugin for TF-Minecraft, with ingredient conversion,
crafting stations, smithing hits, alloy discovery, MMOItems stat calculation and
item provenance. Source originally authored by Drefvelin.

- [Technical documentation](https://github.com/TF-Minecraft/docs/tree/main/projects/AdvancedCrafting)
- [Build and release conventions](https://github.com/TF-Minecraft/docs/blob/main/PIPELINES.md)
- [Source import provenance](SOURCE.md)

## Build

Requires Java 25, Maven, Python 3 and read access to the private ServerAssets
repository for the remaining third-party APIs. Clone TLibs next to this checkout.

```sh
python3 ../tlibs/tools/install-plugins.py --pom pom.xml
GH_TOKEN="$(gh auth token)" bash .github/scripts/prepare-release.sh
mvn clean verify
```

The JAR is `target/advancedcrafting-1.2.2.jar`. TLibs is a separate `provided`
plugin; it is not bundled. Packaging does not copy files to a server.

CI uses the latest verified TLibs release and records its exact version/checksum
alongside UTC-dated development builds. A tag matching the Maven version (for
example `v1.2.2`) builds and tests the source, then creates a draft release with
the JAR, `SHA256SUMS`, and source/dependency provenance in `build.json`.
The imported archive contains no unit tests; CI performs compilation and packaging,
and downstream plugins verify their use of its APIs.

## Runtime

Use Java 25 and a compatible Minecraft 1.21 server. The descriptor requires
TLibs, MMOCore, MMOItems and MythicLib. Configured ItemsAdder furniture also
requires the matching ItemsAdder installation and assets. Supply server-specific
recipes and schemes as described in the technical documentation before use.
Preserve `plugins/AdvancedCrafting/` when updating, replace the old JAR with one
version only, and restart the server. Release publication does not deploy it.

The supplied archive contained no license file; this import does not add a new
license grant.
