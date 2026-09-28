# Type-layout query profiling for KT-55266

This experimental branch instruments the existing Kotlin/Native implementation;
it does not implement precomputed layouts. The profile changes are contained in
one commit on top of an imported source snapshot. The snapshot tree is identical
to Kotlin revision `b6b58b31d68dccfa5d6f1e98842246da366c2dd2`, without inheriting
its commit history.

## Coverage

- 54 backend query sites: 42 numerical layout queries, 10 type-shape queries,
  and 2 pointer-integer type construction queries.
- 12 cinterop query sites, reported separately from backend compilation.
- 11 backend caches and 6 cinterop declaration registries.
- A companion LLVM/Clang patch covering 31 C API entry points and the actual
  lookup branches of StructLayoutMap, MemoizedTypeInfo, ASTRecordLayouts, and
  ObjCLayouts.

`query-sites.json` maps JVM site IDs to calls and cache lookups. `native-sites.json`
maps native IDs. Labels retain source line numbers from the measured baseline.

## Companion LLVM/Clang instrumentation

Apply `llvm.patch` to LLVM revision
`c2e420df942a21f93c15248b4fb9259737fa58c1`. This patch is included here so that
both measurement layers are available from the same branch. It is not applied
automatically by the Kotlin build.

Build the affected LLVM/Clang objects and relink the libraries consumed by
Kotlin/Native, including libclang and the JNI stubs. Compatible artifacts from
the same LLVM revision can be reused; a full LLVM rebuild is not intrinsically
required. An unmodified prebuilt LLVM will not produce native cache statistics.
Build the Kotlin/Native distribution using the resulting libraries.

## Profiling

Create a separate output directory for each invocation, then set:

```sh
export KN_LAYOUT_PROFILE=time
export KN_LAYOUT_NATIVE_PROFILE=time
export KN_LAYOUT_PROFILE_DIR="$PWD/layout-profile-output"
mkdir -p "$KN_LAYOUT_PROFILE_DIR"
```

`time` records durations, calls, and cache hits/misses together. `counts` records
calls and hits/misses without timers. Unset the mode variables to disable the
instrumentation. Set the environment before starting the compiler JVM, use a
fresh JVM for each sample, and wait for process exit so shutdown hooks flush all
TSV files. Do not combine different compiler invocations in one output directory.

For a Release-link experiment, prepare dependencies first, disable Native binary
caches, force the selected link task to execute, and retain the existing
in-process caches. Record task duration independently and verify that no other
Native compilation tasks execute in the measured process. Use one warm-up and
five measured repetitions with the same timed condition. Measure cinterop in a
separate invocation and report it separately.

## Reading the output

Backend and cinterop TSVs include calls, inclusive/exclusive nanoseconds,
hits/misses, errors, and `query_root_calls` / `query_root_ns`. Sum
`query_root_ns` to obtain the outermost query windows without counting nested
queries twice. This sum is accumulated query time across threads, not a measure
of critical-path wall-clock time. Cache timing and query timing overlap.

Native TSVs separate `llvm-c-api`, `clang-c-api`, and `internal` origins. Internal
calls include optimizer/parser work and cannot all be attributed to work that
Kotlin precomputation would eliminate. Never add Kotlin and native durations.

Cache lookups are `hits + misses`; an unvisited cache has an undefined hit rate.
Some caches only record the actual lookup branch, so zero timed calls does not
mean zero lookups or measured zero duration. Type mapping and declaration
registries are not numerical size caches. DebugInfo fallback values are already
computed; their misses do not imply additional LLVM queries.

These measurements include instrumentation overhead and are not measured
speedups. Compute each sample's query/task ratio before taking the median.

## Validation fixtures

The `tests` directory contains the standalone JVM helper self-test and native
LLVM/Clang smoke fixtures used to validate the counters. Compile the JVM test
with the backend `LayoutQueryProfile.kt`, then run it in disabled, `counts`, and
`time` modes. It checks nesting, exception recovery, failed lazy initialization,
and synchronized lazy access from eight threads. The native smoke programs
must link against the instrumented libraries; pass `layout-fixture.h` as the
argument to the Clang smoke program. The fixture also supports cinterop through
`layout-fixture.def`.

The measured implementation passed integrated compilation/runtime smoke tests,
normalized Codegen/LTO IR comparisons across modes, and LLVM IR verification.
