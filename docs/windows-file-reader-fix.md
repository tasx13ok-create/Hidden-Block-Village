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

## Regression coverage

Eleven new reader tests cover rewritten JSON, executable filename extensions,
read-only executable files, mismatched Windows ctime views, old Python creation
time behavior, real changes during reads, changed handle and path identities,
reparse handles and retained POSIX checks. Native-file cases execute in every
CI job alongside the complete preparation, assembly and Java 8 bootstrap suite.

CI now runs the full Python suite on Ubuntu / Python 3.12 and Windows / Python
3.8, 3.12 and 3.14. Each job has Java 8. The Windows jobs attempt real symlink
coverage when supported; the case-sensitive duplicate-file fixture remains on
Linux because the normal Windows filesystem cannot represent that fixture.
The source-builder's suffix handling also uses Python 3.8-compatible syntax,
and the output-corruption fixture now matches Windows paths correctly.

These are packaging checks with synthetic data. They do not establish a
completed real Windows candidate, a saved-world start/restart, graphical client
acceptance or hosted Bisect acceptance. Consult the workflow run associated
with the repair commit for actual CI results.

## Retry boundary

The original source, world, ZIP and rollback are not changed by this repair.
If both proposed output directories are absent, the corrected handoff may use
the same reviewed contract and candidate ID. If either directory exists, keep
it for inspection and prepare a new reviewed candidate ID. Never resume,
overwrite or delete an incomplete candidate to conceal the failed attempt.
