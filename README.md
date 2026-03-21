# GPAPIAddon

Combined proof repository for:
- an API-extended `GriefPrevention` module
- a `GP3DAddon` module built on those APIs

The root project is a single Maven parent (`pom.xml`) that builds both jars in one run.

## Modules

- `griefprevention/`
  - Forked GriefPrevention code with extension APIs:
    - claim tool handler registry
    - visualization style registry/hooks
    - claim geometry + namespaced metadata
    - `/claim` subcommand/mode extension registry
- `gpapi-addon/`
  - Example addon that implements stacked 3D subdivisions using those APIs

## Build

From repository root:

```bash
mvn clean package
```

Output jars:
- `griefprevention/target/GriefPrevention.jar`
- `gpapi-addon/target/gp3d-addon-0.1.0-SNAPSHOT.jar`

## API Documentation

See [newapi.md](newapi.md) for the API surface and usage examples (including shaped-claim examples).

## License

This repository contains GPL-licensed GriefPrevention-derived code. See [LICENSE.txt](LICENSE.txt).
