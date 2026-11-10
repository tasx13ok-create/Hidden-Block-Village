# Windows assembly file-reader correction

The first execution of the connected Core 0.3.8 / Standards 0.3.1 handoff
stopped with `SOURCE_CHANGED` while opening `connected-contract.json`.
A complete read through the PC connection confirmed that the contract still
matched its original 11,912-byte SHA-256. The output parent did not exist, so
this attempt stopped before creating preparation or candidate files.

## Cause and correction

`release_pack.stable_reader` compared all path-based `stat` fields with all
descriptor-based `fstat` fields. On Windows, these are not always equivalent
views of an unchanged file. In Python 3.12 through 3.14, path-based ctime can
remain creation time while handle-based ctime is the file's metadata change
time. The upstream [CPython issue 157671](https://github.com/python/cpython/issues/157671)
reproduces this behavior. Windows path stat also synthesizes executable mode
bits from `.exe`, `.cmd`, `.bat` and `.com` extensions; handle stat has no such
filename information.

The reader now uses two comparisons:

- Between path and handle: the same device/file identity, file type and
  read/write mode, byte length, modification time and creation time. Only the
  Windows-specific executable mode synthesis is normalized; birthtime provides
  consistent creation time where available.
- Over time: each path and handle result is compared with its own full initial
  signature, retaining ctime and mode change detection. The final path must
  still identify the original file. Symlink/reparse checks remain active, and
  the opened handle must itself be a regular file without a reparse attribute.

Non-Windows comparisons retain their complete original signatures. Contract,
source inventory, archive, transfer-piece and whole-artifact SHA-256 checks are
unchanged. This is a correction to file handling, not a new runtime release.
The existing contract continues to identify the original verified component
build; the tool repair has its own Git commit and delivery receipt.

The first native Windows CI runs exposed a second false positive in the full
directory inventory: cached `DirEntry.stat()` metadata can differ after the
same unchanged file is opened, and Windows directory enumeration omits the
device/inode fields. Tree snapshots now obtain fresh no-follow `lstat()` data
for every entry, retaining each actual file identity in both snapshots.

## Regression coverage

Twelve new reader tests cover rewritten JSON, executable filename extensions,
read-only executable files, mismatched Windows ctime views, old Python creation
time behavior, real changes during reads, changed handle and path identities,
reparse handles, fresh tree identities and retained POSIX checks. Native-file cases execute in every
CI job alongside the complete preparation, assembly and Java 8 bootstrap suite.

CI now runs the full Python suite on Ubuntu / Python 3.12 and Windows / Python
3.8, 3.12 and 3.14. Each job has Java 8. The Windows jobs attempt real symlink
coverage when supported; the case-sensitive duplicate-file fixture remains on
Linux because the normal Windows filesystem cannot represent that fixture.
The source-builder's suffix handling also uses Python 3.8-compatible syntax,
and the output-corruption fixture now matches Windows paths correctly.
The unsafe ZIP fixture preserves a literal backslash on Windows instead of
allowing `ZipInfo` to normalize the name before it can be tested.

The complete [repair workflow](https://github.com/tasx13ok-create/Hidden-Block-Village/actions/runs/42866191408)
at `ec7363f6ae8f845c3621f9b9db0c6a43493ef922` finished successfully on
10 November 2026. Ubuntu / Python 3.12 passed 123 tests; Windows / Python 3.8,
3.12 and 3.14 each passed 122 with the single case-sensitive filename skip.
Each Windows job passed the twelve reader/snapshot regressions, twenty
connected-preparation checks, twenty-four assembly checks and fifteen Java 8
bootstrap checks, including actual symlink rejection.

All eleven source components also built successfully. Both the original and
repaired-commit CI artifact ZIPs were downloaded and verified: all 36 component
files were byte-identical, including all thirteen runtime/support files already
delivered to the PC. Every ZIP/JAR CRC passed. The
[exact comparison receipt](../release/evidence/windows-repair-ci-rebuild-comparison.json)
records the two commit IDs, artifact IDs, archive hashes, all component hashes
and source-input differences. Only `tools/build_modules.py` changed among the
1,510 build inputs, for Python 3.8-compatible suffix handling. The two artifact
ZIPs have different hashes because their build reports identify different
commits; their component files match.

These are packaging and source-build results. They do not establish a completed
real Windows candidate, a saved-world start/restart, graphical client acceptance
or hosted Bisect acceptance. Runtime acceptance remains `NOT_RUN` for the new
candidate until those separate checks occur.

## Retry boundary

The original source, world, ZIP and rollback are not changed by this repair.
If both proposed output directories are absent, the corrected handoff may use
the same reviewed contract and candidate ID. If either directory exists, keep
it for inspection and prepare a new reviewed candidate ID. Never resume,
overwrite or delete an incomplete candidate to conceal the failed attempt.
