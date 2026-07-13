package com.mycompany.app.passes;

import com.mycompany.app.Program;
import com.mycompany.app.Term;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class GuardEliminator {
    private GuardEliminator() {
    }

    // Transformes all `|`-guards into if-then-else expressions: same-constructor case-groups of the
    // form `C x1 ... xN | b1, ..., bK -> t1; ...; C z1 ... zN -> tN` become single case-statements
    // of the form `C f1 ... fN -> if (b1 && (... && bK))[f1/x1, ..., fN/xN] then t1[f1/x1, ...,
    // fN/xN] else if ... else tN[f1/z1, ..., fN/zN]`, where `f1 ... fN` are fresh variables not
    // occurring anywhere else.
    public static Program eliminate(final Program program) {
        final var main = eliminate(program.main(), new LinkedHashSet<>());
        final var definitions = new LinkedHashMap<String, Term>();
        program.definitions()
                .forEach((name, t) -> definitions.put(name, eliminate(t, new LinkedHashSet<>())));
        return new Program(main, definitions);
    }

    private static Term eliminate(final Term term, final Set<String> banlist) {
        return switch (term) {
            case Term.Lambda(var x, var t) -> {
                final var banlistx = new LinkedHashSet<>(banlist);
                banlistx.add(x);
                yield new Term.Lambda(x, eliminate(t, banlistx));
            }
            case Term.Application(var t1, var t2) ->
                new Term.Application(eliminate(t1, banlist), eliminate(t2, banlist));
            case Term.StrictApplication(var t1, var t2) ->
                new Term.StrictApplication(eliminate(t1, banlist), eliminate(t2, banlist));
            case Term.Constructor(var name, var ts, var missing) ->
                new Term.Constructor(
                        name,
                        ts.stream().map(t -> eliminate(t, banlist)).toList(),
                        missing);
            case Term.Match(var s, var cases) -> {
                final var sx = eliminate(s, banlist);
                final var casesx = cases.stream().map(myCase -> eliminateCase(myCase, banlist))
                        .toList();
                final var casesxx = groupCases(casesx)
                        .stream()
                        .map(group -> renameCasesInGroup(group, banlist))
                        .map(group -> foldGroup(group))
                        .toList();
                yield new Term.Match(sx, casesxx);
            }
            case Term.Fix(var t) ->
                new Term.Fix(eliminate(t, banlist));
            case Term.IfThenElse(var t1, var t2, var t3) ->
                new Term.IfThenElse(
                        eliminate(t1, banlist),
                        eliminate(t2, banlist),
                        eliminate(t3, banlist));
            case Term.Not(var t) ->
                new Term.Not(eliminate(t, banlist));
            case Term.And(var t1, var t2) ->
                new Term.And(eliminate(t1, banlist), eliminate(t2, banlist));
            case Term.Or(var t1, var t2) ->
                new Term.Or(eliminate(t1, banlist), eliminate(t2, banlist));
            case Term.Range(var t1, var t2, var inclusive) ->
                new Term.Range(
                        t1.map(t -> eliminate(t, banlist)),
                        t2.map(t -> eliminate(t, banlist)),
                        inclusive);
            case Term.StrictOp1(var op, var t) ->
                new Term.StrictOp1(op, eliminate(t, banlist));
            case Term.StrictOp2(var t1, var op, var t2) ->
                new Term.StrictOp2(eliminate(t1, banlist), op, eliminate(t2, banlist));
            case Term.Variable _,Term.Reference _,Term.Operator _,Term.NullLiteral _,Term.BooleanLiteral _,Term.IntegerLiteral _,Term.BigIntegerLiteral _,Term.StringLiteral _ ->
                term;
        };
    }

    private static Term.Case eliminateCase(final Term.Case myCase, final Set<String> banlist) {
        final var banlistx = new LinkedHashSet<>(banlist);
        banlistx.addAll(myCase.xs());
        return new Term.Case(
                myCase.name(),
                myCase.xs(),
                myCase.guards().stream().map(guard -> eliminate(guard, banlistx)).toList(),
                eliminate(myCase.t(), banlistx));
    }

    // Groups the cases by constructor name, preserving the relative order of both the groups & the
    // order of cases within each group.
    private static List<List<Term.Case>> groupCases(final List<Term.Case> cases) {
        final var groups = new LinkedHashMap<String, List<Term.Case>>();
        for (final var myCase : cases) {
            groups.computeIfAbsent(myCase.name(), _ -> new ArrayList<>()).add(myCase);
        }
        for (final var group : groups.values()) {
            final var representative = group.getFirst();
            final var branches = group.subList(0, group.size() - 1);
            final var fallback = group.getLast();
            final var name = representative.name();
            assert branches
                    .stream()
                    .allMatch(myCase -> !myCase.guards().isEmpty())
                    : String.format("Unreachable case(s) in the group `%s`", name);
            assert fallback.guards().isEmpty()
                    : String.format("No fallback case in the group `%s`", name);
        }
        return List.copyOf(groups.values());
    }

    // Unifies all the variable names in the provided case patterns; the fresh variable names will
    // constitute the new pattern.
    private static List<Term.Case> renameCasesInGroup(
            final List<Term.Case> group,
            final Set<String> banlist) {
        assert !group.isEmpty();
        if (group.size() == 1) {
            return group;
        }
        final var representative = group.getFirst();
        final var ys = Term.freshNames(representative.xs().size(), banlist);
        final var banlistx = new LinkedHashSet<>(banlist);
        banlistx.addAll(ys);
        return group
                .stream()
                .map(
                        myCase -> new Term.Case(
                                myCase.name(),
                                ys,
                                myCase.guards()
                                        .stream()
                                        .map(guard -> guard.rename(myCase.xs(), ys, banlistx))
                                        .toList(),
                                myCase.t().rename(myCase.xs(), ys, banlistx)))
                .toList();
    }

    // Once the pattern variables in the group are renamed, fold the group into a single case with
    // an if-then-else body.
    private static Term.Case foldGroup(final List<Term.Case> group) {
        assert !group.isEmpty();
        final var branches = group.subList(0, group.size() - 1);
        final var fallback = group.getLast();
        final var name = fallback.name();
        final var parameters = fallback.xs();
        assert fallback.guards().isEmpty(); // checked in `groupCases`
        Term body = fallback.t();
        for (final var myCase : branches.reversed()) {
            final var condition = foldGuards(myCase.guards());
            final var consequent = myCase.t();
            body = new Term.IfThenElse(condition, consequent, body);
        }
        return new Term.Case(name, parameters, List.of(), body);
    }

    // Folds a non-empty guard sequence `b1, ..., bN` into `(b1 && (... && bN))`, mirroring
    // Haskell's `infixr 3` association.
    private static Term foldGuards(final List<Term> guards) {
        assert !guards.isEmpty(); // checked in `groupCases`
        Term condition = guards.getLast();
        for (final var guard : guards.subList(0, guards.size() - 1).reversed()) {
            condition = new Term.And(guard, condition);
        }
        return condition;
    }
}
