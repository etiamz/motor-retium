package com.mycompany.app.passes;

import com.mycompany.app.Primitives;
import com.mycompany.app.Program;
import com.mycompany.app.Term;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class OperatorSaturator {
    private OperatorSaturator() {
    }

    // Saturates all constructors & built-in operators, wrapping lambdas for yet-unavailable
    // operands: `f e1 ... eN` becomes `\x1, ..., xK-N -> f e1 ... eN x1 ... xK-N`, where `f` can be
    // either a user constructor or a built-in operator, `N >= 0` is the number of the applied
    // arguments, & `K > N` is the arity of `f`.
    // Does not throw any errors on over-saturation.
    public static Program saturate(final Program program) {
        final var main = saturate(program.main(), new LinkedHashSet<>());
        final var definitions = new LinkedHashMap<String, Term>();
        program.definitions()
                .forEach((name, t) -> definitions.put(name, saturate(t, new LinkedHashSet<>())));
        return new Program(main, definitions);
    }

    private static Term saturate(final Term term, final Set<String> banlist) {
        return switch (term) {
            case Term.Application _ -> {
                final var arguments = new ArrayList<Term>();
                Term head = term;
                while (head instanceof Term.Application(var rator, var rand)) {
                    arguments.add(0, saturate(rand, banlist));
                    head = rator;
                }
                final int applied;
                Term result;
                if (head instanceof Term.Constructor(var name, var provided, var missing)) {
                    if (!provided.isEmpty()) {
                        throw new IllegalStateException("Constructor arguments must be empty");
                    }
                    applied = Math.min(arguments.size(), missing);
                    result = wrap(
                            ts -> new Term.Constructor(name, ts, 0),
                            arguments.subList(0, applied),
                            missing - applied,
                            banlist);
                } else if (head instanceof Term.Operator(var op)) {
                    applied = Math.min(arguments.size(), op.arity());
                    result = wrap(
                            ts -> apply(op, ts),
                            arguments.subList(0, applied),
                            op.arity() - applied,
                            banlist);
                } else {
                    applied = 0;
                    result = saturate(head, banlist);
                }
                final var leftover = arguments.subList(applied, arguments.size());
                for (final var argument : leftover) {
                    result = new Term.Application(result, argument);
                }
                yield result;
            }
            case Term.StrictApplication(var t1, var t2) ->
                new Term.StrictApplication(saturate(t1, banlist), saturate(t2, banlist));
            case Term.Constructor(var name, var provided, var missing) -> {
                if (!provided.isEmpty()) {
                    throw new IllegalStateException("Constructor arguments must be empty");
                }
                yield wrap(ts -> new Term.Constructor(name, ts, 0), List.of(), missing, banlist);
            }
            case Term.Operator(var op) ->
                wrap(ts -> apply(op, ts), List.of(), op.arity(), banlist);
            case Term.Lambda(var x, var t) -> {
                final var banlistx = new LinkedHashSet<>(banlist);
                banlistx.add(x);
                yield new Term.Lambda(x, saturate(t, banlistx));
            }
            case Term.Match(var s, var cases) ->
                new Term.Match(
                        saturate(s, banlist),
                        cases.stream().map(myCase -> saturateCase(myCase, banlist)).toList());
            case Term.Fix(var t) ->
                new Term.Fix(saturate(t, banlist));
            case Term.IfThenElse(var t1, var t2, var t3) ->
                new Term.IfThenElse(
                        saturate(t1, banlist),
                        saturate(t2, banlist),
                        saturate(t3, banlist));
            case Term.Not(var t) ->
                new Term.Not(saturate(t, banlist));
            case Term.And(var t1, var t2) ->
                new Term.And(saturate(t1, banlist), saturate(t2, banlist));
            case Term.Or(var t1, var t2) ->
                new Term.Or(saturate(t1, banlist), saturate(t2, banlist));
            case Term.Range(var t1, var t2, var inclusive) ->
                new Term.Range(
                        t1.map(t -> saturate(t, banlist)),
                        t2.map(t -> saturate(t, banlist)),
                        inclusive);
            case Term.StrictOp1(var op, var t) ->
                new Term.StrictOp1(op, saturate(t, banlist));
            case Term.StrictOp2(var t1, var op, var t2) ->
                new Term.StrictOp2(saturate(t1, banlist), op, saturate(t2, banlist));
            case Term.Variable _,Term.Reference _,Term.BooleanLiteral _,Term.IntegerLiteral _,Term.BigIntegerLiteral _,Term.StringLiteral _ ->
                term;
        };
    }

    private static Term.Case saturateCase(final Term.Case myCase, final Set<String> banlist) {
        if (!myCase.guards().isEmpty()) {
            throw new IllegalStateException("`|`-guards must be already eliminated");
        }
        final var banlistx = new LinkedHashSet<>(banlist);
        banlistx.addAll(myCase.xs());
        return new Term.Case(
                myCase.name(),
                myCase.xs(),
                List.of(),
                saturate(myCase.t(), banlistx));
    }

    private static Term wrap(
            final Function<List<Term>, Term> build,
            final List<Term> provided,
            final int remaining,
            final Set<String> banlist) {
        final var parameters = Term.freshNames(remaining, banlist);
        final var arguments = new ArrayList<Term>(provided);
        for (final var x : parameters) {
            arguments.add(new Term.Variable(x));
        }
        Term result = build.apply(arguments);
        for (final var x : parameters.reversed()) {
            result = new Term.Lambda(x, result);
        }
        return result;
    }

    private static Term apply(final Primitives.Operator op, final List<Term> ts) {
        return switch (op) {
            case Primitives.Fix _ -> new Term.Fix(ts.get(0));
            case Primitives.Not _ -> new Term.Not(ts.get(0));
            case Primitives.And _ -> new Term.And(ts.get(0), ts.get(1));
            case Primitives.Or _ -> new Term.Or(ts.get(0), ts.get(1));
            case Primitives.StrictOp1 op1 -> new Term.StrictOp1(op1, ts.get(0));
            case Primitives.StrictOp2 op2 -> new Term.StrictOp2(ts.get(0), op2, ts.get(1));
        };
    }
}
