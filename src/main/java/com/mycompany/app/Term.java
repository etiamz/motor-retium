package com.mycompany.app;

import com.mycompany.app.CheckedInteger.Value;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public sealed interface Term {
    public record Variable(String x) implements Term {
    }

    public record Reference(String name) implements Term {
    }

    public record Lambda(String x, Term t) implements Term {
    }

    public record Application(Term t1, Term t2) implements Term {
    }

    public record StrictApplication(Term t1, Term t2) implements Term {
    }

    public record Constructor(String name, List<Term> ts, int missing) implements Term {
    }

    public record Operator(Primitives.Operator op) implements Term {
    }

    public record Fix(Term t) implements Term {
    }

    public record NullLiteral() implements Term {
    }

    public record BooleanLiteral(boolean b) implements Term {
    }

    public record IntegerLiteral(Value i) implements Term {
    }

    public record BigIntegerLiteral(MyBigInteger i) implements Term {
    }

    public record StringLiteral(MyString s) implements Term {
    }

    public record IfThenElse(Term t1, Term t2, Term t3) implements Term {
    }

    public record Match(Term s, List<Case> cases) implements Term {
    }

    public record Case(String name, List<String> xs, List<Term> guards, Term t) {
    }

    public record Not(Term t) implements Term {
    }

    public record And(Term t1, Term t2) implements Term {
    }

    public record Or(Term t1, Term t2) implements Term {
    }

    public record Range(Optional<Term> t1, Optional<Term> t2, boolean inclusive) implements Term {
    }

    public record StrictOp1(Primitives.StrictOp1 op, Term t) implements Term {
    }

    public record StrictOp2(Term t1, Primitives.StrictOp2 op, Term t2) implements Term {
    }

    public default Term rename(final Map<String, String> renaming, final Set<String> banlist) {
        return switch (this) {
            case Variable(var x) ->
                new Variable(renaming.getOrDefault(x, x));
            case Lambda(var x, var t) -> {
                final var y = freshen(x, banlist);
                final var renamingx = new LinkedHashMap<>(renaming);
                renamingx.put(x, y);
                final var banlistx = new LinkedHashSet<>(banlist);
                banlistx.add(y);
                yield new Lambda(y, t.rename(renamingx, banlistx));
            }
            case Application(var t1, var t2) ->
                new Application(t1.rename(renaming, banlist), t2.rename(renaming, banlist));
            case StrictApplication(var t1, var t2) ->
                new StrictApplication(t1.rename(renaming, banlist), t2.rename(renaming, banlist));
            case Constructor(var name, var ts, var missing) ->
                new Constructor(
                        name,
                        ts.stream().map(t -> t.rename(renaming, banlist)).toList(),
                        missing);
            case Fix(var t) ->
                new Fix(t.rename(renaming, banlist));
            case IfThenElse(var t1, var t2, var t3) ->
                new IfThenElse(
                        t1.rename(renaming, banlist),
                        t2.rename(renaming, banlist),
                        t3.rename(renaming, banlist));
            case Match(var s, var cases) ->
                new Match(
                        s.rename(renaming, banlist),
                        cases.stream().map(myCase -> renameCase(myCase, renaming, banlist))
                                .toList());
            case Not(var t) ->
                new Not(t.rename(renaming, banlist));
            case And(var t1, var t2) ->
                new And(t1.rename(renaming, banlist), t2.rename(renaming, banlist));
            case Or(var t1, var t2) ->
                new Or(t1.rename(renaming, banlist), t2.rename(renaming, banlist));
            case Range(var t1, var t2, var inclusive) ->
                new Range(
                        t1.map(t -> t.rename(renaming, banlist)),
                        t2.map(t -> t.rename(renaming, banlist)),
                        inclusive);
            case StrictOp1(var op, var t) ->
                new StrictOp1(op, t.rename(renaming, banlist));
            case StrictOp2(var t1, var op, var t2) ->
                new StrictOp2(t1.rename(renaming, banlist), op, t2.rename(renaming, banlist));
            case Operator _,Reference _,NullLiteral _,BooleanLiteral _,IntegerLiteral _,BigIntegerLiteral _,StringLiteral _ ->
                this;
        };
    }

    private static Case renameCase(
            final Case myCase,
            final Map<String, String> renaming,
            final Set<String> banlist) {
        final var renamingx = new LinkedHashMap<>(renaming);
        final var banlistx = new LinkedHashSet<>(banlist);
        final var ys = new ArrayList<String>();
        for (final var x : myCase.xs()) {
            final var y = freshen(x, banlistx);
            renamingx.put(x, y);
            banlistx.add(y);
            ys.add(y);
        }
        return new Case(
                myCase.name(),
                ys,
                myCase.guards().stream().map(guard -> guard.rename(renamingx, banlistx)).toList(),
                myCase.t().rename(renamingx, banlistx));
    }

    // @formatter:off
    public default Term rename(
            final List<String> from, final List<String> to, final Set<String> banlist) {
        assert from.size() == to.size();
        final var renaming = new LinkedHashMap<String, String>();
        for (int i = 0; i < from.size(); i++) {
            renaming.put(from.get(i), to.get(i));
        }
        return rename(renaming, banlist);
    }
    // @formatter:on

    public static List<String> freshNames(final int n, final Set<String> banlist) {
        final var names = new ArrayList<String>();
        for (int i = 0; i < n; i++) {
            names.add(freshen("v" + i, banlist));
        }
        return names;
    }

    public static String freshen(final String x, final Set<String> banlist) {
        var y = x;
        while (banlist.contains(y)) {
            y += "'";
        }
        return y;
    }

    public default Set<String> freeVariables() {
        return switch (this) {
            case Variable(var x) ->
                new LinkedHashSet<>(List.of(x));
            case Lambda(var x, var t) -> {
                final var fvSet = t.freeVariables();
                fvSet.remove(x);
                yield fvSet;
            }
            case Application(var t1, var t2) ->
                union(t1, t2);
            case StrictApplication(var t1, var t2) ->
                union(t1, t2);
            case Constructor(var _, var ts, var _) ->
                union(ts.toArray(Term[]::new));
            case Fix(var t) ->
                t.freeVariables();
            case IfThenElse(var t1, var t2, var t3) ->
                union(t1, t2, t3);
            case Match(var s, var cases) -> {
                final var fvSet = s.freeVariables();
                for (final var myCase : cases) {
                    final var caseFvSet = myCase.t().freeVariables();
                    myCase.guards().forEach(guard -> caseFvSet.addAll(guard.freeVariables()));
                    myCase.xs().forEach(caseFvSet::remove);
                    fvSet.addAll(caseFvSet);
                }
                yield fvSet;
            }
            case Not(var t) ->
                t.freeVariables();
            case And(var t1, var t2) ->
                union(t1, t2);
            case Or(var t1, var t2) ->
                union(t1, t2);
            case Range(var t1, var t2, var _) ->
                union(Stream.concat(t1.stream(), t2.stream()).toArray(Term[]::new));
            case StrictOp1(var _, var t) ->
                t.freeVariables();
            case StrictOp2(var t1, var _, var t2) ->
                union(t1, t2);
            case Operator _,Reference _,NullLiteral _,BooleanLiteral _,IntegerLiteral _,BigIntegerLiteral _,StringLiteral _ ->
                new LinkedHashSet<>();
        };
    }

    private static Set<String> union(final Term... terms) {
        final var fvSet = new LinkedHashSet<String>();
        for (final var t : terms) {
            fvSet.addAll(t.freeVariables());
        }
        return fvSet;
    }

    public default Set<String> references() {
        return switch (this) {
            case Reference(var name) ->
                new LinkedHashSet<>(List.of(name));
            case Lambda(var _, var t) ->
                t.references();
            case Application(var t1, var t2) ->
                unionReferences(t1, t2);
            case StrictApplication(var t1, var t2) ->
                unionReferences(t1, t2);
            case Constructor(var _, var ts, var _) ->
                unionReferences(ts.toArray(Term[]::new));
            case Fix(var t) ->
                t.references();
            case IfThenElse(var t1, var t2, var t3) ->
                unionReferences(t1, t2, t3);
            case Match(var s, var cases) -> {
                final var refs = s.references();
                for (final var myCase : cases) {
                    myCase.guards().forEach(guard -> refs.addAll(guard.references()));
                    refs.addAll(myCase.t().references());
                }
                yield refs;
            }
            case Not(var t) ->
                t.references();
            case And(var t1, var t2) ->
                unionReferences(t1, t2);
            case Or(var t1, var t2) ->
                unionReferences(t1, t2);
            case Range(var t1, var t2, var _) ->
                unionReferences(Stream.concat(t1.stream(), t2.stream()).toArray(Term[]::new));
            case StrictOp1(var _, var t) ->
                t.references();
            case StrictOp2(var t1, var _, var t2) ->
                unionReferences(t1, t2);
            case Operator _,Variable _,NullLiteral _,BooleanLiteral _,IntegerLiteral _,BigIntegerLiteral _,StringLiteral _ ->
                new LinkedHashSet<>();
        };
    }

    private static Set<String> unionReferences(final Term... terms) {
        final var refs = new LinkedHashSet<String>();
        for (final var t : terms) {
            refs.addAll(t.references());
        }
        return refs;
    }
}
