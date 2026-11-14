# Naruto bridge tools

`propose_moves.py` turns a list of real registry names into a proposed `bridge-moves.json` and a review CSV. The input is the owner's private `runtime-registry-names.json`, a JSON file with `items` and `entities` lists.

Every entry is a keyword proposal marked unverified. The bridge ignores the proposal file until an entry is copied into `config/valeorders/bridge-moves.json` or confirmed in game:

```
/narutobridge test <style>     # preview the style
/narutobridge map <style>      # map the item you are holding (writes bridge-moves.json)
/narutobridge status           # chakra link status, held item and mapping
```

The registry list and the generated proposal stay private in `local/` (gitignored):

```
python3 tools/bridge/propose_moves.py <private>/runtime-registry-names.json local/naruto-bridge
```

The last private run proposed styles for 194 of 660 licensed IDs and skipped 113 as non-moves (armour, eyes, blocks, food and eggs). The rest need a manual pick.
