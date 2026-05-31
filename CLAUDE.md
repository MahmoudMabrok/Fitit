# Project notes for Claude

## Kotlin gotchas

### Don't name a function `setX`/`getX` matching a property `x`
Kotlin auto-generates JVM setters/getters for properties (`var x` ->
`setX`/`getX`, `val x` -> `getX`). Declaring a function with that same name and
signature causes a **"Platform declaration clash: same JVM signature"** compile
error.

This bites often when adding explicit mutator functions for `private set`
properties. Name the function something else:

```kotlin
var splitMode by mutableStateOf(SplitMode.FIXED)
    private set

// BAD: clashes with the generated setSplitMode for the property
fun setSplitMode(mode: SplitMode) { ... }

// GOOD
fun updateSplitMode(mode: SplitMode) { ... }
```

Use `update*` / `on*Change` / `apply*` instead of `set*`/`get*` when the name
matches an existing property.
