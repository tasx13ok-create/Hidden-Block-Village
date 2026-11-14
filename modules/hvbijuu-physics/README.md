# Native Bijuu tail provenance and runtime baseline

The original privately installed Hidden Village Bijuu Armor0.16.2rc1 JAR remains the runtime baseline. This directory provides no replacement class and is not a production build component.

The external main merge at `f1b0f8f34d16a8d8388574e7aadf538d093e6a96` brought modified tail source and benchmarks. Its README explicitly identified the implementation as recovered by decompiling the supplied upstream JAR, then edited because upstream source was absent. Claude authored the edits, but that does not establish reviewed public redistribution permission for the recovered implementation. No recovered body was read, copied into another implementation or used in the original runtime adaptations during this review.

All four input files were retained privately with exact byte/hash verification before the recovered implementation and its two dependent benchmarks were removed from the current public tree. This README is an original disposition record. Safe identities and review scope are recorded in `../../collaboration/fable/TAIL-PHYSICS-PROVENANCE-DISPOSITION.json`.

The external Git history already exposed those files. Current-tree removal does not erase that history; no shared history was rewritten. No license or complete history purge is claimed.

The proposed momentum, separation, experimental body capsule and swing-benchmark enhancements are not adopted production features. Keeping the original provider does not prove those enhancements. Future work must use reviewed original source or an independently authored optional public-API adapter, with actual native body/world tests. Original worlds, native assets and the private baseline JAR remain unchanged.
