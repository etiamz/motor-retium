package com.mycompany.app.passes;

import com.mycompany.app.Program;
import com.mycompany.app.Renamer;
import com.mycompany.app.Term;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class GuardEliminator {
    private final Set<String> banlist;

    public GuardEliminator() {
        this(new LinkedHashSet<>());
    }

    private GuardEliminator(final Set<String> banlist) {
        this.banlist = banlist;
    }

    // Transformes all `|`-guards into if-then-else expressions: same-constructor case-groups of the
    // form `C x1 ... xN | b1, ..., bK -> t1; ...; C z1 ... zN -> tN` become single case-statements
    // of the form `C f1 ... fN -> if (b1 && (... && bK))[f1/x1, ..., fN/xN] then t1[f1/x1, ...,
    // fN/xN] else if ... else tN[f1/z1, ..., fN/zN]`, where `f1 ... fN` are fresh variables not
    // occurring anywhere else.
    public Program eliminate(final Program program) {
        final var main = eliminate(program.main());
        final var definitions = new LinkedHashMap<String, Term>();
        program.definitions().forEach((name, t) -> definitions.put(name, eliminate(t)));
        return new Program(main, definitions);
    }

    public Term eliminate(final Term term) {
        return switch (term) {
            case Term.Lambda(var x, var t) -> new Term.Lambda(x, bind(List.of(x)).eliminate(t));
            case Term.Let(var x, var e, var t) ->
                new Term.Let(x, eliminate(e), bind(List.of(x)).eliminate(t));
            case Term.Application(var t1, var t2) ->
                new Term.Application(eliminate(t1), eliminate(t2));
            case Term.StrictApplication(var t1, var t2, var source) ->
                new Term.StrictApplication(eliminate(t1), eliminate(t2), source);
            case Term.Constructor(var name, var ts, var missing) ->
                new Term.Constructor(name, ts.stream().map(this::eliminate).toList(), missing);
            case Term.Match(var s, var cases) -> {
                final var myS = eliminate(s);
                final var myCases = cases.stream().map(this::eliminateCase).toList();
                final var myMyCases = groupCases(myCases).stream().map(this::renameCasesInGroup)
                        .map(group -> foldGroup(group)).toList();
                yield new Term.Match(myS, myMyCases);
            }
            case Term.IfThenElse(var t1, var t2, var t3) ->
                new Term.IfThenElse(eliminate(t1), eliminate(t2), eliminate(t3));
            case Term.Not(var t) -> new Term.Not(eliminate(t));
            case Term.And(var t1, var t2) -> new Term.And(eliminate(t1), eliminate(t2));
            case Term.Or(var t1, var t2) -> new Term.Or(eliminate(t1), eliminate(t2));
            case Term.Range(var t1, var t2, var inclusive) ->
                new Term.Range(t1.map(this::eliminate), t2.map(this::eliminate), inclusive);
            case Term.StrictOp1(var op, var t) -> new Term.StrictOp1(op, eliminate(t));
            case Term.StrictOp2(var t1, var op, var t2) ->
                new Term.StrictOp2(eliminate(t1), op, eliminate(t2));
            case Term.Variable _,Term.Operator _,Term.Reference _,Term.NullLiteral _,Term.BooleanLiteral _,Term.IntegerLiteral _,Term.BigIntegerLiteral _,Term.StringLiteral _ ->
                term;
        };
    }

    private Term.Case eliminateCase(final Term.Case myCase) {
        final var inner = bind(myCase.xs());
        return new Term.Case(
                myCase.name(),
                myCase.xs(),
                myCase.guards().stream().map(inner::eliminate).toList(),
                inner.eliminate(myCase.t()));
    }

    private GuardEliminator bind(final List<String> xs) {
        final var myBanlist = new LinkedHashSet<>(banlist);
        myBanlist.addAll(xs);
        return new GuardEliminator(myBanlist);
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
            if (branches.stream().anyMatch(myCase -> myCase.guards().isEmpty())) {
                throw new IllegalStateException(
                        String.format("Unreachable case(s) in the group `%s`", name));
            }
            if (!fallback.guards().isEmpty()) {
                throw new IllegalStateException(
                        String.format("No fallback case in the group `%s`", name));
            }
        }
        return List.copyOf(groups.values());
    }

    // Unifies all the variable names in the provided case patterns; the fresh variable names will
    // constitute the new pattern.
    private List<Term.Case> renameCasesInGroup(final List<Term.Case> group) {
        if (group.isEmpty()) {
            throw new IllegalStateException("Case group must be non-empty");
        }
        if (group.size() == 1) {
            return group;
        }
        final var representative = group.getFirst();
        final var ys = Term.freshNames(representative.xs().size(), banlist);
        final var myBanlist = new LinkedHashSet<>(banlist);
        myBanlist.addAll(ys);
        return group.stream().map(
                myCase -> new Term.Case(
                        myCase.name(),
                        ys,
                        myCase.guards().stream()
                                .map(new Renamer(myCase.xs(), ys, myBanlist)::rename).toList(),
                        new Renamer(myCase.xs(), ys, myBanlist).rename(myCase.t())))
                .toList();
    }

    // Once the pattern variables in the group are renamed, fold the group into a single case with
    // an if-then-else body.
    private static Term.Case foldGroup(final List<Term.Case> group) {
        if (group.isEmpty()) {
            throw new IllegalStateException("Case group must be non-empty");
        }
        final var branches = group.subList(0, group.size() - 1);
        final var fallback = group.getLast();
        final var name = fallback.name();
        final var parameters = fallback.xs();
        if (!fallback.guards().isEmpty()) { // checked in `groupCases`
            throw new IllegalStateException("Fallback case must have no guards");
        }
        Term body = fallback.t();
        for (final var myCase : branches.reversed()) {
            final var condition = foldGuards(myCase.guards());
            final var consequent = myCase.t();
            body = new Term.IfThenElse(condition, consequent, body);
        }
        return new Term.Case(name, parameters, List.of(), body);
    }

    private static Term foldGuards(final List<Term> guards) {
        if (guards.isEmpty()) { // checked in `groupCases`
            throw new IllegalStateException("Guard list must be non-empty");
        }
        Term condition = guards.getLast();
        // Using Haskell's `infixr 3` association.
        for (final var guard : guards.subList(0, guards.size() - 1).reversed()) {
            condition = new Term.And(guard, condition);
        }
        return condition;
    }
}
