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
    private final Set<String> banlist;

    public OperatorSaturator() {
        this(new LinkedHashSet<>());
    }

    private OperatorSaturator(final Set<String> banlist) {
        this.banlist = banlist;
    }

    // Saturates all constructors, wrapping lambdas for yet-unavailable operands: `C e1 ... eN`
    // becomes `\x1, ..., xK-N -> C e1 ... eN x1 ... xK-N`, where `C` is a constructor, `N >= 0` is
    // the number of the applied arguments, & `K > N` is the declared arity of `C`. Since
    // constructors are strict in captures, for immediately applied arguments onely their free
    // variables are evaluated; on the other hand, subsequent arguments applied at run-time will be
    // evaluated eagerly, because the constructor's captures will force them. For instance, if `Foo`
    // has arity 3, then `let foo = Foo a in foo b c` will evaluate the captures of `a`, then
    // evaluate `b` itself, then evaluate `c` itself.
    // All fully-applied unary & binary operators are rendered as primitive operations;
    // under-applied operators such as `negate` or `(+) a` are wrapped in as many lambdas as their
    // arity dictates: `\x -> negate x` & `(\x y -> x + y) a`, respectively. This behaviour respects
    // the termination property of operands: it would be incorrect to, say, render `(+) a` as `(\y
    // -> a + x)` over `(\x y -> x + y) a`, because unlike the latter term, the former one forces
    // onely the captures of `a` instead of `a` itself.
    // We doe not throw any errors on over-saturation.
    public Program saturate(final Program program) {
        final var main = saturate(program.main());
        final var definitions = new LinkedHashMap<String, Term>();
        program.definitions().forEach((name, t) -> definitions.put(name, saturate(t)));
        return new Program(main, definitions);
    }

    public Term saturate(final Term term) {
        return switch (term) {
            case Term.StrictApplication(var t1, var t2, var source) ->
                new Term.StrictApplication(saturate(t1), saturate(t2), source);
            case Term.Application _ -> {
                // We could use `Term.nonStrictSpine` here, but it would not be much simpler.
                final var arguments = new ArrayList<Term>();
                Term head = term;
                while (head instanceof Term.Application(var rator, var rand)) {
                    arguments.add(0, saturate(rand));
                    head = rator;
                }
                final int applied;
                Term result;
                switch (head) {
                    case Term.Constructor(var name, var provided, var missing) -> {
                        if (!provided.isEmpty()) {
                            throw new IllegalStateException("Constructor arguments must be empty");
                        }
                        applied = Math.min(arguments.size(), missing);
                        result = wrap(
                                ts -> new Term.Constructor(name, ts, 0),
                                arguments.subList(0, applied),
                                missing - applied);
                    }
                    case Term.Operator(var op) when arguments.size() >= op.arity() -> {
                        applied = op.arity();
                        result = apply(op, arguments.subList(0, applied));
                    }
                    default -> {
                        applied = 0;
                        result = saturate(head);
                    }
                }
                final var leftover = arguments.subList(applied, arguments.size());
                for (final var argument : leftover) {
                    result = new Term.Application(result, argument);
                }
                yield result;
            }
            case Term.Constructor(var name, var provided, var missing) -> {
                if (!provided.isEmpty()) {
                    throw new IllegalStateException("Constructor arguments must be empty");
                }
                yield wrap(ts -> new Term.Constructor(name, ts, 0), List.of(), missing);
            }
            case Term.Operator(var op) -> wrap(ts -> apply(op, ts), List.of(), op.arity());
            case Term.Lambda(var x, var t) -> new Term.Lambda(x, bind(List.of(x)).saturate(t));
            case Term.Let(var x, var e, var t) ->
                new Term.Let(x, saturate(e), bind(List.of(x)).saturate(t));
            case Term.Match(var s, var cases) ->
                new Term.Match(saturate(s), cases.stream().map(this::saturateCase).toList());
            case Term.IfThenElse(var t1, var t2, var t3) ->
                new Term.IfThenElse(saturate(t1), saturate(t2), saturate(t3));
            case Term.Select(var name, var index, var t) ->
                new Term.Select(name, index, saturate(t));
            case Term.Not(var t) -> new Term.Not(saturate(t));
            case Term.And(var t1, var t2) -> new Term.And(saturate(t1), saturate(t2));
            case Term.Or(var t1, var t2) -> new Term.Or(saturate(t1), saturate(t2));
            case Term.Range(var t1, var t2, var inclusive) ->
                new Term.Range(t1.map(this::saturate), t2.map(this::saturate), inclusive);
            case Term.StrictOp1(var op, var t) -> new Term.StrictOp1(op, saturate(t));
            case Term.StrictOp2(var t1, var op, var t2) ->
                new Term.StrictOp2(saturate(t1), op, saturate(t2));
            case Term.Variable _,Term.Reference _,Term.NullLiteral _,Term.BooleanLiteral _,Term.IntegerLiteral _,Term.BigIntegerLiteral _,Term.StringLiteral _ ->
                term;
        };
    }

    private Term.Case saturateCase(final Term.Case myCase) {
        if (!myCase.guards().isEmpty()) {
            throw new IllegalStateException("`|`-guards must be already eliminated");
        }
        return new Term.Case(
                myCase.name(),
                myCase.xs(),
                List.of(),
                bind(myCase.xs()).saturate(myCase.t()));
    }

    private OperatorSaturator bind(final List<String> xs) {
        final var myBanlist = new LinkedHashSet<>(banlist);
        myBanlist.addAll(xs);
        return new OperatorSaturator(myBanlist);
    }

    // Submits exactly `provided.size() + remaining` arguments to `build`, wrapping lambdas for
    // `remaining` arguments.
    private Term wrap(
            final Function<List<Term>, Term> build,
            final List<Term> provided,
            final int remaining) {
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
            case Primitives.Apply _ -> new Term.Application(ts.get(0), ts.get(1));
            case Primitives.StrictApply _ -> new Term.StrictApplication(
                    ts.get(0),
                    ts.get(1),
                    Term.StrictnessSource.USER_SPECIFIED);
            case Primitives.Not _ -> new Term.Not(ts.get(0));
            case Primitives.And _ -> new Term.And(ts.get(0), ts.get(1));
            case Primitives.Or _ -> new Term.Or(ts.get(0), ts.get(1));
            case Primitives.StrictOp1 op1 -> new Term.StrictOp1(op1, ts.get(0));
            case Primitives.StrictOp2 op2 -> new Term.StrictOp2(ts.get(0), op2, ts.get(1));
        };
    }
}
