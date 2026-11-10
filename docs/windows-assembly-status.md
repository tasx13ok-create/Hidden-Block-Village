# Observe the Windows assembly

Use this when the assembly window stays on an early message and its progress is
unclear. The diagnostic reads process counters and a few small receipts. It does
not read the saved world or rehash the archives, and it leaves the assembly running.

Place `CHECK-VALE-ASSEMBLY.cmd` and `check_assembly_status.py` together beside
`connected-contract.json` in the extracted handoff's `Vale-Export\vale-038`
directory. **Open `CHECK-VALE-ASSEMBLY.cmd` in a second window and leave the original
assembly window open.** Existing Python 3.8+ and Windows PowerShell are used; no
software is installed and no execution policy is changed.

The command samples matching preparation/assembly processes twice, five seconds
apart. It checks the exact tool paths under that handoff; a process started using
a different copy or invocation may not match. It never saves raw command lines,
environment variables, or details about unrelated processes. Each PowerShell
query has a 12-second timeout. No assembly process is stopped or restarted.

| Observation | What it establishes |
|---|---|
| Activity observed | A matching process used CPU or transferred I/O during the short sample. It does not establish correct progress or completion. |
| No activity in sample | Matching counters did not increase during the sample. Waiting, a blocked operation, and a hang cannot be distinguished from this result alone. |
| Matching process set changed | A process began or ended; the original window may now show the result. |
| No matching process | No visible process matched these exact tool paths. Inspect the original window for completion, an error, or a different invocation. |
| Inspection unavailable or partial | Windows did not provide enough information; this is not evidence that assembly stopped. |

The report also inspects these exact local paths, without changing them:

```text
D:\Codex\2026-11-08\thi\work\actual-bisect-server-stage
D:\Codex\Vale-Export\vale-038-standards031-connected1-preparation
D:\Codex\Vale-Export\vale-038-standards031-connected1
```

These are the launcher's default absolute Windows paths. A selected File Link
folder is a browser alias and does not expose its absolute Windows path. This
diagnostic does not infer that mapping or inspect private command arguments; a
manual source/output override must be checked against the original command.

It records directory presence, the two completion receipts, incomplete-marker
presence and archive metadata. It hashes only the bounded contract and receipt
JSON files. The original reviewed contract must still match its recorded SHA-256.
Success receipts are observations of prior tool output; this diagnostic does not
independently verify the archive bytes or run Minecraft.

A new `ASSEMBLY-STATUS-<UTC timestamp>-<random suffix>.json` is written beside the
diagnostic files. Existing reports are never overwritten. File Link can read this
small report while assembly continues. Report creation is restricted to a directory
named `vale-038` directly under `Vale-Export`; there is no arbitrary output-path
option. `--print-only` is available for developer inspection without writing a
report. Non-Windows execution reports that Windows inspection is unavailable.

Process identity uses PID **and creation time** so a reused PID is not compared to
a different process. Missing or decreasing counters are inconclusive. CPU counters
use the Win32_Process 100-nanosecond qualifiers; read/write counters reflect process
I/O, not necessarily physical disk transfers or completed source verification.
See Microsoft's [Win32_Process reference](https://learn.microsoft.com/en-us/windows/win32/cimwin32prov/win32-process).

## Native verification

The [diagnostic CI run](https://github.com/tasx13ok-create/Hidden-Block-Village/actions/runs/42899244144)
at `4eb96814772927770b339228b23f0a037bc07dcc` exercised the actual PowerShell
query on Windows Server 2025 with Python 3.8, 3.12 and 3.14. All ten diagnostic
tests passed on each Windows job, including its isolated temporary process
fixture. Each full Windows suite passed 132 tests with the existing
case-sensitive filename fixture skipped. Ubuntu passed 132 tests and skipped
the Windows-only query. The [exact native result](../release/evidence/windows-assembly-status-ci.json)
records the job IDs, versions and observed output. These runner results do not
describe the process state or assembly outcome on the owner's PC.
