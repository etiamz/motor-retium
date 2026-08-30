package com.mycompany.app;

import com.mycompany.app.Template.Builder.Consumer;
import com.mycompany.app.Template.Builder.Producer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
        final var main = compile(program.main());
        final var book = new HashMap<String, Template>();
        for (final var entry : program.definitions().entrySet()) {
            book.put(entry.getKey(), compile(entry.getValue()));
        }
        return new Compilation(main, book);
    }

    private static Template compile(final Term term) {
        final var builder = new Template.Builder();
        final var root = builder.mkRoot().a();
        final var fvSet = compile(builder, term, root);
        if (!fvSet.isEmpty()) {
            throw new IllegalStateException("Cannot resolve these variable(s): " + fvSet.keySet());
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
                if (fvSet.isEmpty()) {
                    final var agent = builder.mkLambda();
                    output.setProducer(agent.a());
                    bind(builder, agent.b(), usages == null ? List.of() : usages);
                    agent.c().setProducer(result.producer());
                    yield fvSet;
                }
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
                if (fvSet.isEmpty()) {
                    final var agent = builder.mkConstructor(name, arity);
                    output.setProducer(agent.a());
                    for (int i = 0; i < arity; i++) {
                        agent.argument(i).setProducer(results[i].producer());
                    }
                    yield fvSet;
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
                final var agent = builder.mkMatch(names);
                output.setProducer(agent.b());
                final var fvSet = compile(builder, s, agent.a());
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
                    Term handler = t;
                    for (final var x : xs.reversed()) {
                        handler = new Term.Lambda(x, handler);
                    }
                    merge(fvSet, expand(builder, handler, agent.handler(i)));
                }
                yield fvSet;
            }
            case Term.IfThenElse(var t1, var t2, var t3) -> {
                final var agent = builder.mkIfThenElse();
                output.setProducer(agent.b());
                final var fvSet = compile(builder, t1, agent.a());
                merge(fvSet, expand(builder, t2, agent.d()));
                merge(fvSet, expand(builder, t3, agent.c()));
                yield fvSet;
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
