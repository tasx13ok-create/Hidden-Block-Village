# CrazyCrates: nonoperator right-click fix

Observed 2026-11-14. The owner reported that some players got no response when
right-clicking a crate. The supplied server has CrazyCrates1.10.2.2 and
`spawn-protection=16`; its NoBreak config is disabled. No hosted server files
or live session were changed by this worker.

The owner confirmed that an affected player could open a newly registered chest
outside spawn protection. This identifies spawn protection as the cause for
that tested interaction; it is not a complete crate/prize compatibility test.

## In-game workaround

As an operator, place a chest at least32blocks horizontally from world spawn,
look at it and register its intended type:

```text
/cc set Basic
```

Repeat for other chests using `Classic`, `Crazy` or `Galactic`. These are the
four actual crate filenames in the supplied server's CrazyCrates/Crates folder.
Registration outside the protected spawn square worked for the owner. It adds a
usable crate location without changing world spawn or granting operator status.

To retain crate access at spawn, stop/restart the server after setting
`spawn-protection=0` in server.properties. That file/restart change has not been
performed or reported by the owner here. The official CrazyCrates FAQ documents
spawn protection for the symptom where operators can open crates and other
players cannot: https://docs.crazycrew.us/mods/crazycrates/faq/ .

## Three physical keys of each type

The installed1.10.2.2 JAR's own help specifies
`/cc give <physical/virtual> <crate> [amount] [player]`. In game as operator:

```text
/cc give physical Basic 3
/cc give physical Classic 3
/cc give physical Crazy 3
/cc give physical Galactic 3
```

For console use, append the intended player name and omit the leading slash.
Key issuance was provided as instructions; this worker did not run those reward
commands against a server. Do not substitute `giveall`, which targets all online
players rather than all crate types.
