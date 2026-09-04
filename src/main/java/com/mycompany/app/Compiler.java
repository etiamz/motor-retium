package com.mycompany.app;

import com.mycompany.app.Template.Builder.Consumer;
import com.mycompany.app.Template.Builder.Producer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Compiler {
    private Compiler() {
    }

    public record Compilation(Template main, Map<String, Template> book) {
    }

    @SuppressWarnings("serial")
    private static final class TermInterface extends LinkedHashMap<String, List<Consumer>> {
    }

    public static Compilation compile(final Program program) {
        final var main = compile(program.main(), "main");
        final var book = new HashMap<String, Template>();
        program.definitions().forEach((name, t) -> book.put(name, compile(t, name)));
        return new Compilation(main, book);
    }

    private static Template compile(final Term term, final String where) {
        final var builder = new Template.Builder();
        final var root = builder.mkRoot().a();
        final var fvSet = compile(builder, term, root);
        if (!fvSet.isEmpty()) {
            throw new IllegalStateException(
                    String.format(
                            "Cannot resolve these variable(s) in `%s`: %s",
                            where,
                            fvSet.keySet()));
        }
        return builder.build();
    }

    private static TermInterface compile(
            final Template.Builder builder,
            final Term term,
            final Consumer output) {
        return switch (term) {
            case Term.Variable(var x) -> {
                final var fvSet = new TermInterface();
                fvSet.put(x, new ArrayList<>(List.of(output)));
                yield fvSet;
            }
            case Term.Reference(var name) -> {
                output.setProducer(builder.mkReference(name).a());
                yield new TermInterface();
            }
            case Term.Lambda(var x, var t) -> {
                if (t instanceof Term.Variable(var y) && y.equals(x)) {
                    output.setProducer(builder.mkIdentity().a());
                    yield new TermInterface();
                }
                final var result = new Consumer(null);
                final var fvSet = compile(builder, t, result);
                final var usages = fvSet.remove(x);
                final var agent = builder.mkResolver();
                // This line must goe before setting `agent.d()`'s producer, because it guarantees
                // `result`'s producer to be set.
                final var captures = capture(builder, agent.a(), fvSet);
                output.setProducer(agent.b());
                bind(builder, agent.c(), usages == null ? List.of() : usages);
                agent.d().setProducer(result.producer());
                yield captures;
            }
            case Term.Let(var x, var e, var t) -> {
                final var fvSet = compile(builder, t, output);
                final var usages = fvSet.remove(x);
                if (usages == null) {
                    yield fvSet;
                }
                // Build a duplicator tree of `e` & connect it to `t`'s wires requesting `e`.
                merge(fvSet, compile(builder, e, share(builder, usages)));
                yield fvSet;
            }
            case Term.StrictApplication(var t1, var t2, var source) -> {
                final var agent = builder.mkStrictApplicator(source);
                output.setProducer(agent.b());
                final var fvSet = compile(builder, t1, agent.a());
                merge(fvSet, compile(builder, t2, agent.c()));
                yield fvSet;
            }
            case Term.Application(var t1, var t2) -> {
                final var agent = builder.mkApplicator();
                output.setProducer(agent.b());
                final var fvSet = compile(builder, t1, agent.a());
                merge(fvSet, compile(builder, t2, agent.c()));
                yield fvSet;
            }
            case Term.Operator(var op) ->
                throw new IllegalStateException("Unsaturated operator: `" + op + "`");
            case Term.Constructor(var name, var ts, var missing) -> {
                if (missing != 0) {
                    throw new IllegalStateException("Unsaturated constructor: `" + name + "`");
                }
                final int arity = ts.size();
                final var results = new Consumer[arity];
                Arrays.setAll(results, _ -> new Consumer(null));
                final var fvSet = new TermInterface();
                for (int i = 0; i < arity; i++) {
                    merge(fvSet, compile(builder, ts.get(i), results[i]));
                }
                final var agent = builder.mkConstructorResolver(name, arity);
                // See the same line in the lambda case for the ordering constraint.
                final var captures = capture(builder, agent.a(), fvSet);
                output.setProducer(agent.b());
                for (int i = 0; i < arity; i++) {
                    agent.argument(i).setProducer(results[i].producer());
                }
                yield captures;
            }
            case Term.Match(var s, var cases) -> {
                final var names = cases.stream().map(Term.Case::name).toArray(String[]::new);
                final var arities = cases.stream().mapToInt(myCase -> myCase.xs().size()).toArray();
                final var results = new Consumer[cases.size()];
                final var branches = new ArrayList<TermInterface>();
                // `parameters.get(i).get(j)` holds the usages of the `j`th pattern variable of the
                // `i`th case.
                final var parameters = new ArrayList<List<List<Consumer>>>();
                for (int i = 0; i < cases.size(); i++) {
                    final var myCase = cases.get(i);
                    final var name = myCase.name();
                    final var xs = myCase.xs();
                    final var guards = myCase.guards();
                    final var t = myCase.t();
                    if (!guards.isEmpty()) {
                        throw new IllegalStateException(
                                String.format("Uneliminated `|`-guard for `%s`", name));
                    }
                    results[i] = new Consumer(null);
                    final var branch = expand(builder, t, results[i]);
                    final var myParameters = new ArrayList<List<Consumer>>();
                    for (final var x : xs) {
                        final var usages = branch.remove(x);
                        myParameters.add(usages == null ? List.of() : usages);
                    }
                    parameters.add(myParameters);
                    branches.add(branch);
                }
                final var shared = sharedSlots(branches);
                final var agent = builder.mkMatch(names, arities, shared.size());
                output.setProducer(agent.b());
                for (int i = 0; i < cases.size(); i++) {
                    agent.handler(i).setProducer(results[i].producer());
                    final var myParameters = parameters.get(i);
                    for (int j = 0; j < myParameters.size(); j++) {
                        bind(builder, agent.parameter(i, j), myParameters.get(j));
                    }
                }
                final var fvSet = compile(builder, s, agent.a());
                merge(fvSet, mergeBranches(builder, agent, shared, branches));
                yield fvSet;
            }
            case Term.IfThenElse(var t1, var t2, var t3) -> {
                final var thenResult = new Consumer(null);
                final var elseResult = new Consumer(null);
                final var branches = List
                        .of(expand(builder, t2, thenResult), expand(builder, t3, elseResult));
                final var shared = sharedSlots(branches);
                final var agent = builder.mkIfThenElse(shared.size());
                output.setProducer(agent.b());
                agent.d().setProducer(thenResult.producer());
                agent.c().setProducer(elseResult.producer());
                final var fvSet = compile(builder, t1, agent.a());
                merge(fvSet, mergeBranches(builder, agent, shared, branches));
                yield fvSet;
            }
            case Term.Select(var name, var index, var t) -> {
                final var agent = builder.mkSelect(name, index);
                output.setProducer(agent.b());
                yield compile(builder, t, agent.a());
            }
            case Term.Not(var t) -> {
                final var agent = builder.mkNot();
                output.setProducer(agent.b());
                yield compile(builder, t, agent.a());
            }
            case Term.And(var t1, var t2) -> {
                final var agent = builder.mkAnd();
                output.setProducer(agent.b());
                final var fvSet = compile(builder, t1, agent.a());
                merge(fvSet, expand(builder, t2, agent.c()));
                yield fvSet;
            }
            case Term.Or(var t1, var t2) -> {
                final var agent = builder.mkOr();
                output.setProducer(agent.b());
                final var fvSet = compile(builder, t1, agent.a());
                merge(fvSet, expand(builder, t2, agent.c()));
                yield fvSet;
            }
            case Term.Range(var t1, var t2, var inclusive) when t1.isPresent()
                    && t2.isPresent() -> {
                final var agent = builder.mkDoRange(inclusive);
                output.setProducer(agent.b());
                final var fvSet = compile(builder, t1.get(), agent.a());
                merge(fvSet, compile(builder, t2.get(), agent.c()));
                yield fvSet;
            }
            case Term.Range(var t1, var _, var _) when t1.isPresent() -> {
                final var agent = builder.mkDoRangeFrom();
                output.setProducer(agent.b());
                yield compile(builder, t1.get(), agent.a());
            }
            case Term.Range(var _, var t2, var inclusive) when t2.isPresent() -> {
                final var agent = builder.mkDoRangeTo(inclusive);
                output.setProducer(agent.b());
                yield compile(builder, t2.get(), agent.a());
            }
            case Term.Range(var _, var _, var _) -> {
                output.setProducer(builder.mkRangeFull().a());
                yield new TermInterface();
            }
            case Term.StrictOp1(var op, var t) -> {
                final var agent = builder.mkStrictOp1(op);
                output.setProducer(agent.b());
                yield compile(builder, t, agent.a());
            }
            case Term.StrictOp2(var t1, var op, var t2) -> {
                final var agent = builder.mkStrictOp2(op);
                output.setProducer(agent.b());
                final var fvSet = compile(builder, t1, agent.a());
                merge(fvSet, compile(builder, t2, agent.c()));
                yield fvSet;
            }
            case Term.NullLiteral() -> {
                output.setProducer(builder.mkNull().a());
                yield new TermInterface();
            }
            case Term.BooleanLiteral(var b) -> {
                output.setProducer(b ? builder.mkTrue().a() : builder.mkFalse().a());
                yield new TermInterface();
            }
            case Term.IntegerLiteral(var i) -> {
                output.setProducer(builder.mkInteger(i).a());
                yield new TermInterface();
            }
            case Term.BigIntegerLiteral(var i) -> {
                output.setProducer(builder.mkBigInteger(i).a());
                yield new TermInterface();
            }
            case Term.StringLiteral(var s) -> {
                output.setProducer(builder.mkString(s).a());
                yield new TermInterface();
            }
        };
    }

    // Same interface as `compile`, but builds a term into an expansion that materializes on demand
    // at run-time. This is used to avoid allocating possibly uselesse agents, such as untaken
    // branches of an if-then-else or case-of.
    private static TermInterface expand(
            final Template.Builder builder,
            final Term term,
            final Consumer output) {
        final var inner = new Template.Builder();
        final var fvSet = compile(inner, term, inner.mkRoot().a());
        fvSet.forEach((x, usages) -> bind(inner, inner.mkImport(x), usages));
        final var agent = builder.mkExpansion(inner);
        output.setProducer(agent.a());
        final var imports = new TermInterface();
        for (final var x : fvSet.keySet()) {
            imports.put(x, new ArrayList<>(List.of(agent.imported(x))));
        }
        return imports;
    }

    // Takes the interfaces of mutually exclusive branches, returnes a map from the shared variable
    // names to their slot indices.
    private static Map<String, Integer> sharedSlots(final List<TermInterface> branches) {
        final var seen = new HashSet<String>();
        final var slots = new LinkedHashMap<String, Integer>();
        for (final var branch : branches) {
            for (final var x : branch.keySet()) {
                if (!seen.add(x)) {
                    slots.computeIfAbsent(x, _ -> slots.size());
                }
            }
        }
        return slots;
    }

    // Routes every variable shared among the branches through its corresponding binder slot in
    // `selector`, thus eliding redundant duplicators.
    private static TermInterface mergeBranches(
            final Template.Builder builder,
            final Template.Builder.Branching selector,
            final Map<String, Integer> sharedSlots,
            final List<TermInterface> branches) {
        final var result = new TermInterface();
        for (int i = 0; i < branches.size(); i++) {
            for (final var entry : branches.get(i).entrySet()) {
                final var x = entry.getKey();
                final var usages = entry.getValue();
                final var slot = sharedSlots.get(x);
                if (slot == null) {
                    // `x` is used in exactly one branch, so its usages remain unchanged.
                    result.put(x, new ArrayList<>(usages));
                } else {
                    // `x` is used in two or more branches: route through the binder.
                    final var binder = selector.binder(slot, i);
                    final var value = selector.value(slot);
                    result.computeIfAbsent(x, _ -> new ArrayList<>(List.of(value)));
                    bind(builder, binder, usages);
                }
            }
        }
        return result;
    }

    private static TermInterface capture(
            final Template.Builder builder,
            final Consumer head,
            final TermInterface captures) {
        Consumer tail = head;
        final var myCaptures = new TermInterface();
        for (final var entry : captures.entrySet()) {
            final var cap = builder.mkCapture();
            myCaptures.put(entry.getKey(), new ArrayList<>(List.of(cap.a())));
            tail.setProducer(cap.b());
            bind(builder, cap.c(), entry.getValue());
            tail = cap.d();
        }
        tail.setProducer(builder.mkEndOfList().a());
        return myCaptures;
    }

    private static Consumer share(final Template.Builder builder, final List<Consumer> usages) {
        if (usages.size() == 1) {
            return usages.getFirst();
        }
        final var dup = builder.mkDuplicator();
        usages.getFirst().setProducer(dup.b());
        bind(builder, dup.c(), usages.subList(1, usages.size()));
        return dup.a();
    }

    private static void bind(
            final Template.Builder builder,
            final Producer binder,
            final List<Consumer> usages) {
        if (usages.isEmpty()) {
            return;
        }
        Producer cursor = binder;
        int i = 0;
        while (i < usages.size() - 1) {
            final var dup = builder.mkDuplicator();
            dup.a().setProducer(cursor);
            usages.get(i).setProducer(dup.b());
            cursor = dup.c();
            i++;
        }
        usages.get(i).setProducer(cursor);
    }

    private static <K, V> void merge(final Map<K, List<V>> into, final Map<K, List<V>> from) {
        from.forEach(
                (key, values) -> into.computeIfAbsent(key, _ -> new ArrayList<>()).addAll(values));
    }
}
