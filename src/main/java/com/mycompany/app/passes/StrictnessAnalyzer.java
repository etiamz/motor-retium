package com.mycompany.app.passes;

import com.mycompany.app.Program;
import com.mycompany.app.Term;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class StrictnessAnalyzer {
    private StrictnessAnalyzer() {
    }

    @SuppressWarnings("serial")
    private static final class Environment extends LinkedHashMap<String, Set<Integer>> {
    }

    // Poor man's strictnesse analyzer: applications become strict when proven safe.
    // Recall two facts: (1) strict applicators are "duplex" in the engine, meaning that their right
    // operands are considered for parallel evaluation; (2) the majority of applications in a
    // typical functional program are strict in nature. Our incentive is thus to strictify as many
    // source program's applications as possible, inasmuch as doing so has a higher chance of
    // saturating all the target machine's cores with work.
    // Thanks to heartbeat scheduling, there is no concern of strictifying "too many" applications,
    // as long as each of them is strict in nature.
    public static Program analyze(final Program program) {
        final var phi = fix(program.definitions());
        final var main = annotate(phi, program.main());
        final var definitions = new LinkedHashMap<String, Term>();
        program.definitions().forEach((name, t) -> definitions.put(name, annotate(phi, t)));
        return new Program(main, definitions);
    }

    private static Environment fix(final Map<String, Term> definitions) {
        final var phi = new Environment();
        definitions.forEach((name, _) -> phi.put(name, Set.of()));
        boolean fix = false;
        while (!fix) {
            fix = true;
            for (final var entry : definitions.entrySet()) {
                final var name = entry.getKey();
                final var summary = strictParameters(phi, entry.getValue());
                if (!summary.equals(phi.put(name, summary))) {
                    fix = false;
                }
            }
        }
        return phi;
    }

    private static Set<Integer> strictPositions(final Environment phi, final Term head) {
        return switch (head) {
            case Term.Reference(var name) when phi.get(name) instanceof Set<Integer> summary ->
                summary;
            default ->
                strictParameters(phi, head);
        };
    }

    private static Set<Integer> strictParameters(final Environment phi, final Term term) {
        final var summary = new LinkedHashSet<Integer>();
        Term body = term;
        for (int i = 0; body instanceof Term.Lambda(var x, var t); i++) {
            if (demand(phi, t).contains(x)) {
                summary.add(i);
            }
            body = t;
        }
        return summary;
    }

    private static Set<String> demand(final Environment phi, final Term term) {
        return switch (term) {
            case Term.Variable(var x) ->
                new LinkedHashSet<>(List.of(x));
            case Term.Lambda _ ->
                // Unlike normal-order reduction, our closures are strict in captures.
                term.freeVariables();
            case Term.StrictApplication(var t1, var t2) -> {
                final var result = demand(phi, t1);
                result.addAll(demand(phi, t2));
                yield result;
            }
            case Term.Application _ -> {
                final var spine = spine(term);
                final var result = demand(phi, spine.head);
                final var positions = strictPositions(phi, spine.head);
                for (int i = 0; i < spine.arguments.size(); i++) {
                    if (positions.contains(i)) {
                        result.addAll(demand(phi, spine.arguments.get(i)));
                    }
                }
                yield result;
            }
            case Term.IfThenElse(var t1, var t2, var t3) -> {
                final var result = demand(phi, t1);
                final var branches = demand(phi, t2);
                branches.retainAll(demand(phi, t3));
                result.addAll(branches);
                yield result;
            }
            case Term.Match(var s, var cases) -> {
                final var result = demand(phi, s);
                final var common = caseDemand(cases.getFirst());
                for (final var myCase : cases.subList(1, cases.size())) {
                    common.retainAll(caseDemand(myCase));
                }
                result.addAll(common);
                yield result;
            }
            case Term.Not(var t) ->
                demand(phi, t);
            case Term.And(var t1, var _) ->
                demand(phi, t1);
            case Term.Or(var t1, var _) ->
                demand(phi, t1);
            case Term.Range(var t1, var t2, var _) -> {
                final var result = new LinkedHashSet<String>();
                t1.ifPresent(t -> result.addAll(demand(phi, t)));
                t2.ifPresent(t -> result.addAll(demand(phi, t)));
                yield result;
            }
            case Term.StrictOp1(var _, var t) ->
                demand(phi, t);
            case Term.StrictOp2(var t1, var _, var t2) -> {
                final var result = demand(phi, t1);
                result.addAll(demand(phi, t2));
                yield result;
            }
            case Term.Constructor(var _, var _, var missing) -> {
                if (missing != 0) {
                    throw new IllegalStateException("Constructors must be already saturated");
                }
                // Just like closures, our constructors are strict in captures.
                yield term.freeVariables();
            }
            case Term.Operator _ ->
                throw new IllegalStateException("Operators must be already saturated");
            case Term.Reference _,Term.NullLiteral _,Term.BooleanLiteral _,Term.IntegerLiteral _,Term.BigIntegerLiteral _,Term.StringLiteral _ ->
                new LinkedHashSet<>();
        };
    }

    private static Set<String> caseDemand(final Term.Case myCase) {
        final var result = myCase.t().freeVariables();
        myCase.xs().forEach(result::remove);
        return result;
    }

    private static Term annotate(final Environment phi, final Term term) {
        return switch (term) {
            case Term.Lambda(var x, var t) ->
                new Term.Lambda(x, annotate(phi, t));
            case Term.StrictApplication(var t1, var t2) ->
                new Term.StrictApplication(annotate(phi, t1), annotate(phi, t2));
            case Term.Application _ -> {
                final var spine = spine(term);
                final var positions = strictPositions(phi, spine.head);
                Term result = annotate(phi, spine.head);
                for (int i = 0; i < spine.arguments.size(); i++) {
                    final var argument = annotate(phi, spine.arguments.get(i));
                    result = positions.contains(i)
                            ? new Term.StrictApplication(result, argument)
                            : new Term.Application(result, argument);
                }
                yield result;
            }
            case Term.Constructor(var name, var ts, var missing) -> {
                if (missing != 0) {
                    throw new IllegalStateException("Constructors must be already saturated");
                }
                yield new Term.Constructor(
                        name,
                        ts.stream().map(t -> annotate(phi, t)).toList(),
                        0);
            }
            case Term.Match(var s, var cases) ->
                new Term.Match(
                        annotate(phi, s),
                        cases.stream().map(myCase -> annotateCase(phi, myCase)).toList());
            case Term.IfThenElse(var t1, var t2, var t3) ->
                new Term.IfThenElse(
                        annotate(phi, t1),
                        annotate(phi, t2),
                        annotate(phi, t3));
            case Term.Not(var t) ->
                new Term.Not(annotate(phi, t));
            case Term.And(var t1, var t2) ->
                new Term.And(annotate(phi, t1), annotate(phi, t2));
            case Term.Or(var t1, var t2) ->
                new Term.Or(annotate(phi, t1), annotate(phi, t2));
            case Term.Range(var t1, var t2, var inclusive) ->
                new Term.Range(
                        t1.map(t -> annotate(phi, t)),
                        t2.map(t -> annotate(phi, t)),
                        inclusive);
            case Term.StrictOp1(var op, var t) ->
                new Term.StrictOp1(op, annotate(phi, t));
            case Term.StrictOp2(var t1, var op, var t2) ->
                new Term.StrictOp2(annotate(phi, t1), op, annotate(phi, t2));
            case Term.Operator _ ->
                throw new IllegalStateException("Operators must be already saturated");
            case Term.Variable _,Term.Reference _,Term.NullLiteral _,Term.BooleanLiteral _,Term.IntegerLiteral _,Term.BigIntegerLiteral _,Term.StringLiteral _ ->
                term;
        };
    }

    private static Term.Case annotateCase(final Environment phi, final Term.Case myCase) {
        if (!myCase.guards().isEmpty()) {
            throw new IllegalStateException("`|`-guards must be already eliminated");
        }
        return new Term.Case(
                myCase.name(),
                myCase.xs(),
                List.of(),
                annotate(phi, myCase.t()));
    }

    private record Spine(Term head, List<Term> arguments) {
    }

    private static Spine spine(final Term term) {
        final var arguments = new ArrayList<Term>();
        Term head = term;
        while (head instanceof Term.Application(var rator, var rand)) {
            arguments.addFirst(rand);
            head = rator;
        }
        return new Spine(head, arguments);
    }
}
