package com.mycompany.app.passes;

import com.mycompany.app.Program;
import com.mycompany.app.Term;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class StrictnessAnalyzer {
    public StrictnessAnalyzer() {
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
    public Program analyze(final Program program) {
        final var analyzer = new Analyzer();
        analyzer.fix(program.definitions());
        final var main = analyzer.annotate(program.main());
        final var definitions = new LinkedHashMap<String, Term>();
        program.definitions().forEach((name, t) -> definitions.put(name, analyzer.annotate(t)));
        return new Program(main, definitions);
    }

    private static final class Analyzer {
        private final Environment phi;

        public Analyzer() {
            this.phi = new Environment();
        }

        // Refines `phi` until the summaries stop changing; must run before any `annotate`.
        public void fix(final Map<String, Term> definitions) {
            definitions.forEach((name, _) -> phi.put(name, Set.of()));
            boolean fix = false;
            while (!fix) {
                fix = true;
                for (final var entry : definitions.entrySet()) {
                    final var name = entry.getKey();
                    final var summary = strictParameters(entry.getValue());
                    if (!summary.equals(phi.put(name, summary))) {
                        fix = false;
                    }
                }
            }
        }

        private Set<Integer> strictPositions(final Term head) {
            return switch (head) {
                case Term.Reference(var name) when phi.get(name) instanceof Set<Integer> summary ->
                    summary;
                default -> strictParameters(head);
            };
        }

        private Set<Integer> strictParameters(final Term term) {
            final var summary = new LinkedHashSet<Integer>();
            Term body = term;
            for (int i = 0; body instanceof Term.Lambda(var x, var t); i++) {
                if (demand(t).contains(x)) {
                    summary.add(i);
                }
                body = t;
            }
            return summary;
        }

        private Set<String> demand(final Term term) {
            return switch (term) {
                case Term.Variable(var x) -> new LinkedHashSet<>(List.of(x));
                case Term.Lambda _ ->
                    // Unlike normal-order reduction, our closures are strict in captures.
                    term.freeVariables();
                case Term.Let(var x, var e, var t) -> {
                    final var result = demand(t);
                    if (result.remove(x)) {
                        result.addAll(demand(e));
                    }
                    yield result;
                }
                case Term.StrictApplication(var t1, var t2, var _) -> {
                    final var result = demand(t1);
                    result.addAll(demand(t2));
                    yield result;
                }
                case Term.Application _ -> {
                    final var spine = term.nonStrictSpine();
                    final var result = demand(spine.head());
                    final var positions = strictPositions(spine.head());
                    for (int i = 0; i < spine.arguments().size(); i++) {
                        if (positions.contains(i)) {
                            result.addAll(demand(spine.arguments().get(i)));
                        }
                    }
                    yield result;
                }
                case Term.IfThenElse(var t1, var t2, var t3) -> {
                    final var result = demand(t1);
                    final var branches = demand(t2);
                    branches.retainAll(demand(t3));
                    result.addAll(branches);
                    yield result;
                }
                case Term.Match(var s, var cases) -> {
                    final var result = demand(s);
                    final var common = caseDemand(cases.getFirst());
                    for (final var myCase : cases.subList(1, cases.size())) {
                        common.retainAll(caseDemand(myCase));
                    }
                    result.addAll(common);
                    yield result;
                }
                case Term.Select(var _, var _, var t) -> demand(t);
                case Term.Not(var t) -> demand(t);
                case Term.And(var t1, var _) -> demand(t1);
                case Term.Or(var t1, var _) -> demand(t1);
                case Term.Range(var t1, var t2, var _) -> {
                    final var result = new LinkedHashSet<String>();
                    t1.ifPresent(t -> result.addAll(demand(t)));
                    t2.ifPresent(t -> result.addAll(demand(t)));
                    yield result;
                }
                case Term.StrictOp1(var _, var t) -> demand(t);
                case Term.StrictOp2(var t1, var _, var t2) -> {
                    final var result = demand(t1);
                    result.addAll(demand(t2));
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

        private Set<String> caseDemand(final Term.Case myCase) {
            // If there are no pattern variables, demand is recursive; otherwise, the branch is
            // wrapped in lambdas, which demand the free variables.
            final var result = myCase.xs().isEmpty()
                    ? demand(myCase.t())
                    : myCase.t().freeVariables();
            myCase.xs().forEach(result::remove);
            return result;
        }

        public Term annotate(final Term term) {
            return switch (term) {
                case Term.Lambda(var x, var t) -> new Term.Lambda(x, annotate(t));
                case Term.Let(var x, var e, var t) -> new Term.Let(x, annotate(e), annotate(t));
                case Term.StrictApplication(var t1, var t2, var source) ->
                    new Term.StrictApplication(annotate(t1), annotate(t2), source);
                case Term.Application _ -> {
                    final var spine = term.nonStrictSpine();
                    final var positions = strictPositions(spine.head());
                    Term result = annotate(spine.head());
                    for (int i = 0; i < spine.arguments().size(); i++) {
                        final var argument = annotate(spine.arguments().get(i));
                        result = positions.contains(i)
                                ? new Term.StrictApplication(
                                        result,
                                        argument,
                                        Term.StrictnessSource.INFERRED)
                                : new Term.Application(result, argument);
                    }
                    yield result;
                }
                case Term.Constructor(var name, var ts, var missing) -> {
                    if (missing != 0) {
                        throw new IllegalStateException("Constructors must be already saturated");
                    }
                    yield new Term.Constructor(name, ts.stream().map(this::annotate).toList(), 0);
                }
                case Term.Match(var s, var cases) ->
                    new Term.Match(annotate(s), cases.stream().map(this::annotateCase).toList());
                case Term.IfThenElse(var t1, var t2, var t3) ->
                    new Term.IfThenElse(annotate(t1), annotate(t2), annotate(t3));
                case Term.Select(var name, var index, var t) ->
                    new Term.Select(name, index, annotate(t));
                case Term.Not(var t) -> new Term.Not(annotate(t));
                case Term.And(var t1, var t2) -> new Term.And(annotate(t1), annotate(t2));
                case Term.Or(var t1, var t2) -> new Term.Or(annotate(t1), annotate(t2));
                case Term.Range(var t1, var t2, var inclusive) ->
                    new Term.Range(t1.map(this::annotate), t2.map(this::annotate), inclusive);
                case Term.StrictOp1(var op, var t) -> new Term.StrictOp1(op, annotate(t));
                case Term.StrictOp2(var t1, var op, var t2) ->
                    new Term.StrictOp2(annotate(t1), op, annotate(t2));
                case Term.Operator _ ->
                    throw new IllegalStateException("Operators must be already saturated");
                case Term.Variable _,Term.Reference _,Term.NullLiteral _,Term.BooleanLiteral _,Term.IntegerLiteral _,Term.BigIntegerLiteral _,Term.StringLiteral _ ->
                    term;
            };
        }

        private Term.Case annotateCase(final Term.Case myCase) {
            if (!myCase.guards().isEmpty()) {
                throw new IllegalStateException("`|`-guards must be already eliminated");
            }
            return new Term.Case(myCase.name(), myCase.xs(), List.of(), annotate(myCase.t()));
        }
    }
}
