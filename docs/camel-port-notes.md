# CaMeL Port Notes — Interpreter-level Fidelity Reference

This is the implementation-level companion to
[`docs/architecture.md`](architecture.md) §12 (Trust layer — CaMeL). The
architecture doc places the building blocks; this doc captures the
per-AST-node evaluation rules and call-site mechanics so the eventual
Kotlin port has implementation fidelity to the reference at
`~/Downloads/pixel-backups/backup_git/git/home/adk-samples/python/agents/camel/`.

This is **not** a tutorial. It's a port spec.

---

## 1. Reference entry point

```python
parse_and_interpret_code(
    code: str,
    namespace: Namespace,
    tool_calls_chain: Sequence[FunctionCall],
    dependencies: Iterable[Value],
    eval_args: EvalArgs,
) -> tuple[CaMeLResult, Namespace, Sequence[FunctionCall], Iterable[Value]]
```

`code` is parsed via `ast.parse(code, mode="exec")`. The top-level
`ast.Module` is dispatched to `_eval_module`, which evaluates its body as
a statement list. The interpreter is recursive — every AST-node-handler
function returns `EvalResult = (CaMeLResult, Namespace, ToolCallsChain,
Dependencies)` and re-threads those four values to the next call.

`EvalArgs` is fixed for an entire execution: it holds the
`SecurityPolicyEngine` and the `DependenciesPropagationMode` (`NORMAL` or
`STRICT`).

---

## 2. AST nodes the reference handles

The reference is a **subset Python interpreter**. From `interpreter.py`,
the explicit handlers are:

**Expressions:**
- `Constant` (string / int / float / bytes / None / True / False)
- `FormattedValue`, `JoinedStr` (f-strings; `_eval_formatted_value`, `_eval_joined_str`)
- `Starred` (in iterables and call args)
- `List`, `Tuple`, `Set`, `Dict`
- `Name` (load)
- `Attribute` (load + store)
- `Subscript` (load + store)
- `NamedExpr` (walrus `:=`)
- `UnaryOp` (`+`, `-`, `not`, `~`)
- `BinOp` (`+`, `-`, `*`, `/`, `//`, `%`, `**`, `<<`, `>>`, `|`, `^`, `&`, `@`)
- `BoolOp` (`and`, `or` — short-circuits with capability propagation)
- `Compare` (`==`, `!=`, `<`, `<=`, `>`, `>=`, `is`, `is not`, `in`, `not in`; supports chained comparisons)
- `Call` (the key node — see §6)
- `IfExp` (`x if cond else y`)
- `ListComp`, `SetComp`, `DictComp` (comprehensions with multi-generator + filter support)

**Statements:**
- `Expr` (bare expression as statement)
- `Assign`, `AugAssign` (`+=` etc.), `AnnAssign` (typed assignment)
- `If`, `For` (control flow)

**NOT handled** (return `_make_not_implemented_error` if encountered):
- `While` loops
- `Try`/`Except`/`Finally`/`Raise` (exceptions arise from interpreter machinery, not user code)
- `Lambda` definitions
- `FunctionDef`, `AsyncFunctionDef`, `ClassDef` (the user code can't define new functions or classes — only call pre-registered ones)
- `Import`, `ImportFrom` (no module imports)
- `Global`, `Nonlocal`
- `Yield`, `YieldFrom`, `Await`
- `Delete`, `Pass` (latter is no-op in reality but not formally handled in the AST switch)
- `With` (context managers)
- Generator expressions (`(x for x in xs)` standalone — only the comprehension variants are supported)

The P-LLM prompt teaches the model which subset to emit. The Kotlin port
needs the same subset; supporting strictly less is acceptable for the
first cut, supporting strictly more is forbidden (every added node is a
new attack surface).

---

## 3. The `Value[T]` envelope and its subclasses

Every runtime value the interpreter handles is a `Value[T]`:

```python
Value[T]:
    raw: T                        # the Python value
    capabilities: Capabilities    # sources + readers + metadata
    dependencies: tuple[Value, ...]   # contributing Values
```

The reference has a typed subclass per Python primitive (`camel_value.py`):

- `CaMeLNone` — wraps `None`
- `CaMeLTrue` / `CaMeLFalse` (under shared `_Bool` parent)
- `CaMeLInt`, `CaMeLFloat`, `CaMeLStr`
- `CaMeLTuple`, `CaMeLList`, `CaMeLSet`, `CaMeLDict`
- `CaMeLIterable` (Sequence/MutableSequence/Iterator variants)
- `CaMeLMapping` (Mutable variant)
- `CaMeLCallable` (with `CaMeLFunction`, `CaMeLBuiltin`, `CaMeLClass`, `CaMeLClassInstance` subclasses)
- `HasAttrs` (mixin for `.attr` access; `CaMeLClass`, `CaMeLClassInstance` mix it in)
- `HasSetField` (mixin for mutable `.attr =` assignment)

**Why typed wrappers and not a single `Value[Any]`:**
- Type-specific methods (`.upper()` on `CaMeLStr`, `.append(...)` on
  `CaMeLList`) are dispatched as `CaMeLCallable`s bound to the receiver
  — so attribute access through `HasAttrs` returns a method-`Value`,
  not a raw Python bound method. This is what makes `string.format(...)`
  routable through the security policy if the implementer wanted to.
- Capability propagation differs by operation: `str` concatenation, `list`
  comprehension, `dict` `.update()` each combine capabilities in subtly
  different ways. Encoding that as polymorphism is cleaner than a giant
  switch on `type(raw)`.

**Kotlin port:** mirror this hierarchy. Sealed class
`CamelValue<T>` with subclasses per primitive. Wrap Kotlin types: `Long`
(int), `Double` (float), `String`, `List<CamelValue<*>>`,
`Set<CamelValue<*>>`, `Map<CamelValue<*>, CamelValue<*>>`,
`CamelCallable`, `Unit`/`null` as `CamelNone`. Booleans need their own
class because Kotlin `Boolean` can't be sealed-parented.

---

## 4. Capability propagation rules

The fundamental rule: when an operation combines `Value`s, the result's
`Capabilities` is the **intersection of readers** ∪ **union of sources**.

```python
result.sources_set = a.sources_set | b.sources_set | ...
result.readers_set = a.readers_set & b.readers_set & ...  # uses Public.__and__ / __rand__
```

`Public()` is the identity element under `&`: `Public() & frozenset({r1, r2}) == frozenset({r1, r2})`. So combining a Public value with a Restricted value yields the Restricted reader set (intersection narrows readability). Combining two Restricted values intersects their reader sets — fewer readers, never more.

Sources accumulate monotonically: any source that touched a value stays
in its `sources_set` forever.

Specific propagation cases (from `interpreter.py`):

- **Constants** (`_eval_constant`): `Capabilities.default()` =
  `(sources={USER}, readers=Public())`. Literals are user-trusted.
- **Variable load** (`_eval_name_load`): returns the `Value` already in
  the namespace; no change.
- **f-strings** (`_eval_formatted_value`, `_eval_joined_str`): result has
  `Capabilities.camel()` (sources={CAMEL}, readers=Public). Dependencies
  are the evaluated parts. **Note** — interpreter loses character-level
  capability tracking; the whole formatted string takes one capability
  envelope. TODO in the reference.
- **Binary / unary / boolean / compare ops** (`_eval_bin_op`, `_eval_unary_op`,
  `_eval_bool_op`, `_eval_compare`): wrap operands' raw values, run the
  Python operation on the raw side, combine capabilities per the rule
  above. Each subclass overrides combine logic where needed (e.g.,
  `CaMeLStr.from_raw(text, caps, deps)`).
- **Subscript / attribute access** (`_eval_subscript_load`,
  `_eval_attribute_load`): result inherits the container's capabilities
  combined with the indexer/key's capabilities (the key matters because
  a tainted key on a public dict means the lookup pattern itself
  encodes taint).
- **Function call return** (`_eval_call`): the called function's
  `capabilities` (declared at tool registration) become the return's
  capabilities. Dependencies include the function itself + all args +
  all kwargs.

**Dependencies tuple ≠ source tracking.** The `dependencies` field on
each `Value` is a flat list of contributing `Value`s. It's used by
`SecurityPolicyEngine.check_policy` to find non-public values in the
**transitive** dependency graph at a tool-call boundary, even when the
immediate args have been laundered to `Public()` (e.g., a Q-LLM result).

---

## 5. `PropagationMode.NORMAL` vs `STRICT`

`DependenciesPropagationMode` is set on `EvalArgs` and read at one site:
`_eval_call` line 3131. In **STRICT** mode, calling `query_ai_assistant`
appends all its args + kwargs to the running `dependencies` chain — so
the Q-LLM call's downstream consequences carry the taint forward.

In **NORMAL** mode, `query_ai_assistant` does the partial laundering
described above (return value gets `Capabilities.camel()`, but its
`dependencies` tuple still references the args). NORMAL is the default
because STRICT can cause over-blocking — once any tainted value touches
the Q-LLM, every later side-effect tool call would deny.

**For the genesis port:** ship NORMAL as the default; expose STRICT as a
debug/audit mode setting (`isCamelStrictMode`) gated behind the same
build flag as `isCamelEnabled`. The architectural primitive is the
flag, not the policy choice.

---

## 6. The call site contract (`_eval_call`)

The single most important function in the interpreter. Order of operations:

1. **Evaluate the callable** (`node.func`). This is a `Value` lookup — the
   function being called is itself a `Value` (specifically a
   `CaMeLCallable` if call is legal).
2. **Evaluate positional args**, including starred unpacking (`*xs`).
3. **Method-call adjustment:** if the callable has a `receiver()` (i.e.,
   it's a bound method on a `Value`), prepend the receiver as the first
   positional arg.
4. **Evaluate keyword args**, including double-starred unpacking (`**kw`).
5. **Callability check:** if the callable isn't a `CaMeLCallable`, raise
   `TypeError`. (Python's deferred type check is preserved — args are
   evaluated even if the function isn't callable, which means
   side-effects in args run regardless. Genesis port can choose to
   short-circuit here if no Kotlin parity concerns matter.)
6. **Policy check:** call
   `eval_args.security_policy_engine.check_policy(
       fn_name, args_by_keyword, dependencies)`.
   This is the **only enforcement site**. The policy engine sees the
   tool name, the kwargs as `{name: Value}`, and the entire running
   dependencies chain.
7. **Deny bypass:** if the callable is a `CaMeLBuiltin` or `CaMeLClass`,
   `Denied` is **ignored** — built-ins (constructors, `query_ai_assistant`,
   `print`, etc.) always run. Tools registered via the
   `(callable, capabilities, dependencies)` triple are NOT bypassed.
8. **STRICT propagation:** if the callable is `query_ai_assistant` and
   mode is STRICT, append args + kwargs to dependencies (§5).
9. **Invoke the callable:** `evaled_fn.call(args, kwargs, namespace)`.
   Exceptions are caught:
   - `NotEnoughInformationError` (library exception from the Q-LLM
     subagent asking for more context) → wrap as `CaMeLException` with
     args+kwargs as dependencies, capabilities sourced from
     `Tool(fn_name)`, readers Public.
   - `RecursionError` → re-raise (don't silently swallow runaway
     recursion).
   - Any other exception → reconstruct it with `Value.raw`s for args,
     wrap as `CaMeLException` with fn+args+kwargs as dependencies, same
     `Tool(fn_name)` capability.
10. **Record the call:** create a `FunctionCall(function, object_type,
    args_by_keyword, output=raw_return, is_builtin)` and append to the
    `tool_calls_chain`. This is the audit log; the chain is returned
    from `parse_and_interpret_code` for telemetry.

**Kotlin port checklist for `_eval_call`:**
- [ ] Same 10-step order
- [ ] Same enforcement-only-at-step-6 invariant (no other code path can
      reach a `CamelCallable.invoke` without going through this gate)
- [ ] Same built-in bypass for the `Denied` result
- [ ] Same `NotEnoughInformationError` special-case
- [ ] Same `FunctionCall` audit-record emission

---

## 7. The `query_ai_assistant` built-in

The Q-LLM bridge. Signature:

```python
query_ai_assistant(query: str, output_schema: str) -> int | str | float | bool
```

`output_schema` is restricted to `"int"`, `"str"`, `"float"`, `"bool"`.
Any other value → `ValueError`. The function:

1. Runs the Q-LLM in a fresh stateless session (new
   `runner.session_service.create_session`).
2. Posts `{query}\n\n output_schema: {output_schema}` as the user
   message.
3. Streams responses; concatenates parts authored by the QLLM service
   (pattern-matched on `agent_name`).
4. Coerces the concatenated text via `int_validator` / `float_validator`
   / `bool_validator` / `str()`. Raises if coercion fails.
5. Deletes the session.

The returned primitive is wrapped by `_eval_call` as a `Value` with
`Capabilities.camel()` — sources={CAMEL}, readers=Public. The
dependency chain (in NORMAL mode) does NOT include the args, so the
laundering is real for the immediate args. But anything *constructed
from* `Value`s that themselves had tainted dependencies still carries
those upstream.

**Kotlin port:**

```kotlin
class QuarantinedLlmService(private val llm: LLM) {
    suspend fun query(query: String, outputSchema: QSchema): Any =
        when (outputSchema) {
            QSchema.INT -> coerceInt(llm.generateText("$query\n\noutput_schema: int"))
            QSchema.FLOAT -> coerceFloat(llm.generateText("$query\n\noutput_schema: float"))
            QSchema.BOOL -> coerceBool(llm.generateText("$query\n\noutput_schema: bool"))
            QSchema.STR -> llm.generateText("$query\n\noutput_schema: str").trim()
        }

    enum class QSchema { INT, STR, FLOAT, BOOL }
    class NotEnoughInformationError(msg: String) : Exception(msg)
}
```

Each `query` is a new call to the LLM. Genesis's `LLM` clients are
stateless per call, so the "stateless session" invariant comes for free.

The built-in itself is registered into the interpreter namespace at
`CamelInterpreterService.init` and wraps `QuarantinedLlmService.query`
with the auto-wrap-to-`Capabilities.camel()` post-processing.

---

## 8. `CaMeLException` — exceptions carry provenance too

Defined in `interpreter.py:48-81`:

```python
@dataclasses.dataclass(frozen=True)
class CaMeLException(Generic[_E]):
    exception: _E
    nodes: tuple[ExceptionASTNodes, ...]
    dependencies: tuple[Value, ...]
    capabilities: Capabilities = field(default_factory=Capabilities.camel)
```

Every error in the interpreter is wrapped in a `CaMeLException`. The
`nodes` field accumulates AST nodes as the exception bubbles up — so
the P-LLM (on retry) sees the syntactic location of the failure. The
`dependencies` field records the `Value`s that contributed to the
failure (useful for the P-LLM to understand which inputs caused the
issue).

When the interpreter catches an exception during a tool call, it
**reconstructs** the exception with the raw values of any `Value`-typed
args, so the resulting `e.args` are plain Python types. This is so the
P-LLM can read the error message without seeing the capability envelope
in stringified form.

**Kotlin port:** mirror with a sealed `CamelEvalException`. The
recursive `nodes` accumulation needs an `update_error_with_node` helper
(reference: `interpreter.py:95-100`).

---

## 9. `Namespace` and assignment

The namespace is a `dict[str, Value]` with read/write/delete. Assignment
handlers are split:

- `_eval_assign(node: ast.Assign)` — handles `target = value` and
  multiple `targets[0] = targets[1] = ... = value` (chained assignment).
- `_eval_ann_assign` — typed annotations (`x: int = 5`); the annotation
  is read but not enforced at runtime (Python semantics).
- `_eval_aug_assign` — `+=`, `-=`, etc. — combines op semantics with
  reassignment.
- `_assign_name` — `x = ...`
- `_assign_attribute` — `obj.attr = ...` (requires obj to have `HasSetField`)
- `_assign_subscript` — `obj[key] = ...` (requires obj to be a `CaMeLMutableMapping` or `CaMeLMutableSequence`)
- `_assign_tuple_list` — `(a, b, c) = ...` destructuring

There are no closures (no `FunctionDef`), so the namespace is flat per
interpretation run. Comprehensions create scoped sub-namespaces
(`_eval_comprehensions` line 1581) that get torn down via
`_restore_or_delete_variables`.

**Kotlin port:** flat `MutableMap<String, CamelValue<*>>` for the
top-level namespace; comprehension scopes use copy-on-write semantics
matching `_restore_or_delete_variables`.

---

## 10. The `Capabilities.default()` vs `.camel()` distinction

```python
Capabilities.default() = (sources={USER}, readers=Public(), metadata={})
Capabilities.camel()   = (sources={CAMEL}, readers=Public(), metadata={})
```

- `default()` is used for **literals from the P-LLM's code** (constants
  in the source). They're user-trusted because the user query is the
  source of truth for the P-LLM's program text.
- `camel()` is used for values the **interpreter itself synthesized** —
  f-string concatenations, Q-LLM return values, type-coerced derivatives.

This distinction matters for telemetry/audit. The base security policy
treats both as `Public()` so policy checks don't care, but a richer
policy could differentiate.

**Kotlin port:** identical `Capabilities.default()` and
`Capabilities.camel()` factory methods on the data class.

---

## 11. Open questions for the Kotlin port

1. **Parser:** how to parse the P-LLM's Python-subset code?
   - **Option A:** hand-written recursive-descent parser limited to the
     handled AST nodes (~1500 LOC, dependency-free). Most fidelity.
   - **Option B:** vendor a Python AST library for JVM (e.g., Jython AST
     module, RustPython AST bridge). Brings substantial native
     dependency.
   - **Option C:** swap the program language to Kotlin-subset; use
     Kotlin's embeddable compiler. P-LLM has to be retrained on
     Kotlin examples — bigger model-side cost than parser-side.

   The architecture.md §12.5 recommends **Plan DSL** (option D — not on
   this list) for the first cut: a JSON tool-call plan, simpler
   interpreter (~300 LOC), trades expressive power for landing speed.
   When the Python parser arrives, swap the `CamelProgram` IR; the
   `Value`/`SecurityPolicy`/`FunctionCall` machinery is unchanged.

2. **Datetime handling:** the reference excludes `datetime`,
   `timedelta`, `date`, `time`, `NaiveDatetime`, `timezone` from
   user-callable classes (`classes_to_exclude` in
   `CaMelInterpreterService`). Why? Because date arithmetic was
   producing unsoundness in capability propagation; not directly stated
   in source, inferred from the exclusion list. **Port mirror:** same
   exclusion until proven safe.

3. **`pydantic.BaseModel` instances:** the reference handles them as
   `CaMeLClassInstance` with `HasSetField` (mutable fields with
   per-field capability tracking). Kotlin port uses data classes which
   are immutable — easier semantically but means schema-typed values
   require `.copy(...)` semantics for mutation, which the Python-subset
   parser would have to translate.

4. **Recursion depth:** the reference re-raises `RecursionError` so the
   loop doesn't silently fail. The Kotlin JVM has a smaller default
   stack than CPython for the interpreter recursion. Either expand the
   stack via a thread with `-Xss` or rewrite the dispatcher iteratively.
   Default to thread-with-larger-stack approach for the first cut.

5. **Async tools:** the reference is fully synchronous. Genesis tools
   are `suspend` (coroutines). The Kotlin interpreter needs `suspend`
   dispatch in `_eval_call`. This is the only non-trivial divergence
   from the reference — straightforward but pervasive.

---

## 12. Suggested implementation order

When the workstream picks up:

1. **`Capabilities` + `Source` + `Readers` + `Public` sentinel + helper
   `&` / `|` combinators** (~80 LOC). Foundation.
2. **`CamelValue<T>` sealed hierarchy: `CamelNone`, `CamelBool`,
   `CamelInt`, `CamelFloat`, `CamelStr`, `CamelList`, `CamelDict`,
   `CamelTuple`, `CamelSet`** (~300 LOC). No callables yet.
3. **`CamelCallable` + `CamelFunction` + `CamelBuiltin`** (~150 LOC).
4. **`SecurityPolicy` + `SecurityPolicyResult` + `SecurityPolicyEngine`
   + `BaseSecurityPolicy` + `SecurityPolicyDeniedError`** (~150 LOC).
5. **`Namespace`** (~50 LOC).
6. **`CamelEvalException` + `CamelResult` + accumulator helpers**
   (~80 LOC).
7. **`EvalArgs` + `PropagationMode`** (~30 LOC).
8. **Plan DSL parser** (or Python-subset parser if option A chosen)
   (~300 / ~1500 LOC).
9. **Interpreter dispatch — one AST node type at a time, in the order:
   Constant → Name → Assign → BinOp → BoolOp → Compare → If → Subscript
   → Attribute → Call**. After each node, write a test that exercises
   it standalone before moving on. (~800 LOC dispatch + tests.)
10. **`QuarantinedLlmService` + `query_ai_assistant` built-in**
    (~150 LOC).
11. **`CamelInterpreterService.executeCode` + `CamelAgent` loop**
    (~200 LOC).
12. **Integration with genesis: wrap `LlmIntentClassifier` as a
    Q-LLM consumer; replace `FulfillmentRunner` LLM call site with a
    P-LLM call site.** (~200 LOC + test refactor.)

Total estimate: ~2400 LOC for Plan DSL variant, ~3500 LOC for
Python-subset variant. Tests roughly equal. Two-engineer-weeks for the
Plan DSL path; three to four for Python-subset.

---

## 13. Reference cross-index

| Concept | File | Lines |
|---|---|---|
| Entry point | `interpreter.py::parse_and_interpret_code` | 4006+ |
| Recursive dispatch | `interpreter.py::camel_eval` | 3605+ |
| Module evaluation | `interpreter.py::_eval_module` | 472+ |
| Call evaluation (the gate) | `interpreter.py::_eval_call` | 3020–3226 |
| Capabilities dataclass | `capabilities/capabilities.py` | 24–45 |
| Source / Tool source | `capabilities/sources.py` | 22–44 |
| Public / Readers | `capabilities/readers.py` | 21–45 |
| Security policy types | `security_policy.py` | 27–129 |
| Base policy rule | `security_policy.py::base_security_policy` | 61–84 |
| `SecurityPolicyEngine.check_policy` impl | `security_policy.py` | 97–129 |
| `Value` hierarchy | `interpreter/camel_value.py` | full file |
| `CaMeLFunction` / `CaMeLBuiltin` wrappers | `interpreter/camel_value.py` | 1792–1925 |
| `FunctionCall` audit type | `function_types.py` | 40–54 |
| `CaMeLException` | `interpreter.py` | 48–81 |
| `EvalArgs` + `PropagationMode` | `interpreter.py` | 116–139 |
| Q-LLM service | `camel_agent/camel_agent.py::QuarantinedLlmService` | 80–282 |
| `query_ai_assistant` factory | `camel_agent/camel_agent.py::get_query_ai_assistant_function` | 193–282 |
| Interpreter service + tool wrap | `camel_agent/camel_agent.py::CaMelInterpreterService` | 285–395 |
| Loop agent | `camel_agent/camel_agent.py::CaMeLAgent` | 483–587 |
| System prompts | `camel_agent/prompts.py` | full file |

Use these as the authoritative reference during the Kotlin port. When in
doubt about a specific case (e.g., what happens when a `Compare` chain
short-circuits with a `Denied` intermediate), open the file at the
referenced line.
