# VibeTGram specification index

This directory is the design source of truth until the six production
repositories have been bootstrapped. When a contract moves into its owning
repository, this index must retain a link to the authoritative copy.

Local checks:

```bash
python3 scripts/check-docs.py
PYTHONPATH=/path/to/jsonschema python3 scripts/validate-contracts.py
```

`validate-contracts.py` requires `jsonschema` with Draft 2020-12 support; CI will
pin that development dependency rather than relying on a global installation.

## Normative documents

1. [System architecture](architecture/system-architecture.md)
2. [Two-level Core/Mod API](api/two-level-api.md)
3. [Architecture decision records](architecture/adr/README.md)
4. [Capability matrix](modding/capability-matrix.md)
5. [Package formats](modding/package-formats.md)
6. [Addon registry](modding/addons-market.md)
7. [Feature parity](feature-parity.md)
8. [Roadmap](roadmap.md)
9. [Key management](security/key-management-runbook.md)
10. [Internal CI lanes](ci.md)

Machine-readable contracts and their semantic-validation boundary are indexed
in [`schemas/README.md`](../schemas/README.md). Primary-source evidence and the
AyuGram/exteraGram comparison are in
[`research/primary-sources.md`](research/primary-sources.md).

`MUST`, `MUST NOT`, `SHOULD`, and `MAY` are used in their RFC 2119 sense.
Architecture decision records override descriptive prose when the two conflict.
Machine-readable schemas override examples when validating a package.

## Ownership after repository split

| Document | Owning repository |
| --- | --- |
| System architecture, build BOM and release BOM | `app` |
| Semantic/raw API contracts | `core`, with Mod facades in `mods` |
| Core interface and TDLib policy | `core` |
| Mod API, capabilities and package formats | `mods` |
| GUI contract and extension slots | `gui` |
| Reviewed addon records | `addons-market` |
| Executable tutorials | `mods-example` |

The `mods/docs` directory will be the source for the public GitHub Wiki. Wiki
content is generated from versioned files and is never edited as an independent
source of truth.
