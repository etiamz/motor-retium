package com.mycompany.app.passes;

import com.mycompany.app.Definition;
import com.mycompany.app.Program;
import com.mycompany.app.Term;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

public final class SharedVariableHoister {
    private SharedVariableHoister() {
    }

    // Transformes:
    // (1) `if C then M else N` into `(if C then \S -> M else \S -> N) S`, and
    // (2) `case s of { Ci x1 ... xN -> E1; ...; Cn z1 ... zN -> En }` into `(case s of { Ci x1 ...
    // xN -> \S -> E1; ...; Cn z1 ... zN -> \S -> En }) S`,
    // where `S` is a non-empty spine of variables shared among the branches.
    // The rationale of this transformation is to trade duplicators, the most expensive agents in
    // our machine, for closures, before the reduction starts.
    public static Program hoist(final Program program) {
        final var main = hoist(program.main());
        final var definitions = new LinkedHashMap<String, Definition>();
        program.definitions().forEach(
                (name, d) -> {
                    final var result = hoist(d.body());
                    definitions.put(name, new Definition(d.parameters(), result));
                });
        return new Program(main, definitions);
    }

    private static Term hoist(final Term term) {
        return switch (term) {
            case Term.Call(var name, var arguments, var missing) ->
                new Term.Call(
                        name,
                        arguments.stream()
                                .map(argument -> {
                                    final var t = argument.t();
                                    final var strictness = argument.strictness();
                                    return new Term.Argument(hoist(t), strictness);
                                })
                                .toList(),
                        missing);
            case Term.Lambda(var x, var t) ->
                new Term.Lambda(x, hoist(t));
            case Term.Application(var t1, var t2, var strictness) ->
                new Term.Application(hoist(t1), hoist(t2), strictness);
            case Term.Constructor(var name, var ts, var missing) -> {
                if (missing != 0) {
                    throw new IllegalStateException("Constructors must be already saturated");
                }
                yield new Term.Constructor(
                        name,
                        ts.stream().map(SharedVariableHoister::hoist).toList(),
                        0);
            }
            case Term.Operator _ ->
                throw new IllegalStateException("Operators must be already saturated");
            case Term.Match(var s, var cases) -> {
                final var vars = sharedVariables(cases);
                Term result = new Term.Match(
                        hoist(s),
                        cases.stream().map(myCase -> hoistCase(myCase, vars)).toList());
                for (final var x : vars) {
                    result = new Term.Application(result, new Term.Variable(x));
                }
                yield result;
            }
            case Term.Fix(var t) ->
                new Term.Fix(hoist(t));
            case Term.Not(var t) ->
                new Term.Not(hoist(t));
            case Term.And(var t1, var t2) ->
                new Term.And(hoist(t1), hoist(t2));
            case Term.Or(var t1, var t2) ->
                new Term.Or(hoist(t1), hoist(t2));
            case Term.Range(var t1, var t2, var inclusive) ->
                new Term.Range(
                        t1.map(SharedVariableHoister::hoist),
                        t2.map(SharedVariableHoister::hoist),
                        inclusive);
            case Term.StrictOp1(var op, var t) ->
                new Term.StrictOp1(op, hoist(t));
            case Term.StrictOp2(var t1, var op, var t2) ->
                new Term.StrictOp2(hoist(t1), op, hoist(t2));
            case Term.IfThenElse(var t1, var t2, var t3) -> {
                final Term condition = hoist(t1);
                final Term consequent = hoist(t2);
                final Term alternative = hoist(t3);
                final var vars = sharedVariables(consequent, alternative);
                if (vars.isEmpty()) {
                    yield new Term.IfThenElse(condition, consequent, alternative);
                }
                Term myConsequent = consequent;
                Term myAlternative = alternative;
                for (final var x : vars.reversed()) {
                    myConsequent = new Term.Lambda(x, myConsequent);
                    myAlternative = new Term.Lambda(x, myAlternative);
                }
                Term result = new Term.IfThenElse(condition, myConsequent, myAlternative);
                for (final var x : vars) {
                    result = new Term.Application(result, new Term.Variable(x));
                }
                yield result;
            }
            case Term.Variable _,Term.NullLiteral _,Term.BooleanLiteral _,Term.IntegerLiteral _,Term.BigIntegerLiteral _,Term.StringLiteral _ ->
                term;
        };
    }

    private static Term.Case hoistCase(final Term.Case myCase, final List<String> vars) {
        if (!myCase.guards().isEmpty()) {
            throw new IllegalStateException("`|`-guards must be already eliminated");
        }
        Term t = hoist(myCase.t());
        for (final var x : vars.reversed()) {
            t = new Term.Lambda(x, t);
        }
        return new Term.Case(myCase.name(), myCase.xs(), List.of(), t);
    }

    private static List<String> sharedVariables(final Term t1, final Term t2) {
        final var t1FreeVars = t1.freeVariables();
        final var result = new ArrayList<String>();
        for (final var x : t2.freeVariables()) {
            if (t1FreeVars.contains(x)) {
                result.add(x);
            }
        }
        return result;
    }

    private static List<String> sharedVariables(final List<Term.Case> cases) {
        final var counts = new LinkedHashMap<String, Integer>();
        final var patternVars = new HashSet<String>();
        for (final var myCase : cases) {
            patternVars.addAll(myCase.xs());
            final var fvSet = myCase.t().freeVariables();
            myCase.xs().forEach(fvSet::remove);
            for (final var x : fvSet) {
                counts.merge(x, 1, Integer::sum);
            }
        }
        final var result = new ArrayList<String>();
        counts.forEach((x, n) -> {
            if (n > 1 && !patternVars.contains(x)) {
                result.add(x);
            }
        });
        return result;
    }
}
