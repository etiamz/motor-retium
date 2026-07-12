package com.mycompany.app;

import com.mycompany.app.Template.Builder.Consumer;
import com.mycompany.app.Template.Builder.Producer;
import java.util.ArrayList;
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
            case Term.Lambda(var x, var t) -> {
                if (t instanceof Term.Variable(var y) && y.equals(x)) {
                    output.setProducer(builder.mkIdentity().a());
                    yield new TermInterface();
                }
                final var result = new Consumer();
                final var fvSet = compile(builder, t, result);
                final var usages = fvSet.remove(x);
                final var captures = fvSet;
                if (captures.isEmpty()) {
                    final var agent = builder.mkLambda();
                    output.setProducer(agent.a());
                    bind(builder, agent.b(), usages == null ? List.of() : usages);
                    agent.c().setProducer(result.producer());
                    yield captures;
                }
                final var agent = builder.mkResolver();
                Consumer tail = agent.a();
                final var capturesx = new TermInterface();
                for (final var entry : captures.entrySet()) {
                    final var cap = builder.mkCapture();
                    capturesx.put(entry.getKey(), new ArrayList<>(List.of(cap.a())));
                    tail.setProducer(cap.b());
                    bind(builder, cap.c(), entry.getValue());
                    tail = cap.d();
                }
                tail.setProducer(builder.mkEndOfList().a());
                output.setProducer(agent.b());
                bind(builder, agent.c(), usages == null ? List.of() : usages);
                agent.d().setProducer(result.producer());
                yield capturesx;
            }
            case Term.Application(var t1, var t2) -> {
                final var agent = builder.mkApplicator();
                output.setProducer(agent.b());
                final var fvSet = compile(builder, t1, agent.a());
                merge(fvSet, compile(builder, t2, agent.c()));
                yield fvSet;
            }
            case Term.StrictApplication(var t1, var t2) -> {
                final var agent = builder.mkStrictApplicator();
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
                final var agent = builder.mkConstructor(name, ts.size());
                output.setProducer(agent.a());
                final var fvSet = new TermInterface();
                for (int i = 0; i < ts.size(); i++) {
                    merge(fvSet, compile(builder, ts.get(i), agent.argument(i)));
                }
                yield fvSet;
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
                    final var guard = myCase.guards();
                    if (!guard.isEmpty()) {
                        throw new IllegalStateException(
                                String.format("Uneliminated `|`-guard for `%s`", name));
                    }
                    Term handler = myCase.t();
                    for (int j = xs.size() - 1; j >= 0; j--) {
                        handler = new Term.Lambda(xs.get(j), handler);
                    }
                    merge(fvSet, compile(builder, handler, agent.handler(i)));
                }
                yield fvSet;
            }
            case Term.Fix(var t) -> {
                final var agent = builder.mkFix();
                output.setProducer(agent.b());
                yield compile(builder, t, agent.a());
            }
            case Term.Reference(var name) -> {
                output.setProducer(builder.mkReference(name).a());
                yield new TermInterface();
            }
            case Term.IfThenElse(var t1, var t2, var t3) -> {
                final var agent = builder.mkIfThenElse();
                output.setProducer(agent.b());
                final var fvSet = compile(builder, t1, agent.a());
                merge(fvSet, compile(builder, t2, agent.d()));
                merge(fvSet, compile(builder, t3, agent.c()));
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
                merge(fvSet, compile(builder, t2, agent.c()));
                yield fvSet;
            }
            case Term.Or(var t1, var t2) -> {
                final var agent = builder.mkOr();
                output.setProducer(agent.b());
                final var fvSet = compile(builder, t1, agent.a());
                merge(fvSet, compile(builder, t2, agent.c()));
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
