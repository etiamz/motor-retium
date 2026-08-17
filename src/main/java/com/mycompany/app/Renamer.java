package com.mycompany.app;

import com.mycompany.app.Term.*;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Renamer {
    private final Map<String, String> renaming;
    private final Set<String> banlist;

    public Renamer(final Map<String, String> renaming, final Set<String> banlist) {
        this.renaming = renaming;
        this.banlist = banlist;
    }

    public Renamer(final List<String> from, final List<String> to, final Set<String> banlist) {
        if (from.size() != to.size()) {
            throw new IllegalStateException("Renaming source and target must be equally sized");
        }
        final var renaming = new LinkedHashMap<String, String>();
        for (int i = 0; i < from.size(); i++) {
            renaming.put(from.get(i), to.get(i));
        }
        this.renaming = renaming;
        this.banlist = banlist;
    }

    public Term rename(final Term term) {
        return switch (term) {
            case Variable(var x) -> new Variable(renaming.getOrDefault(x, x));
            case Lambda(var x, var t) -> {
                final var inner = bind(List.of(x));
                yield new Lambda(inner.renaming.get(x), inner.rename(t));
            }
            case Application(var t1, var t2) -> new Application(rename(t1), rename(t2));
            case StrictApplication(var t1, var t2) -> new StrictApplication(rename(t1), rename(t2));
            case Constructor(var name, var ts, var missing) ->
                new Constructor(name, ts.stream().map(this::rename).toList(), missing);
            case IfThenElse(var t1, var t2, var t3) ->
                new IfThenElse(rename(t1), rename(t2), rename(t3));
            case Match(var s, var cases) ->
                new Match(rename(s), cases.stream().map(this::renameCase).toList());
            case Not(var t) -> new Not(rename(t));
            case And(var t1, var t2) -> new And(rename(t1), rename(t2));
            case Or(var t1, var t2) -> new Or(rename(t1), rename(t2));
            case Range(var t1, var t2, var inclusive) ->
                new Range(t1.map(this::rename), t2.map(this::rename), inclusive);
            case StrictOp1(var op, var t) -> new StrictOp1(op, rename(t));
            case StrictOp2(var t1, var op, var t2) -> new StrictOp2(rename(t1), op, rename(t2));
            case Operator _,Reference _,NullLiteral _,BooleanLiteral _,IntegerLiteral _,BigIntegerLiteral _,StringLiteral _ ->
                term;
        };
    }

    private Case renameCase(final Case myCase) {
        final var inner = bind(myCase.xs());
        return new Case(
                myCase.name(),
                myCase.xs().stream().map(inner.renaming::get).toList(),
                myCase.guards().stream().map(inner::rename).toList(),
                inner.rename(myCase.t()));
    }

    private Renamer bind(final List<String> xs) {
        final var myRenaming = new LinkedHashMap<>(renaming);
        final var myBanlist = new LinkedHashSet<>(banlist);
        for (final var x : xs) {
            final var y = Term.freshen(x, myBanlist);
            myRenaming.put(x, y);
            myBanlist.add(y);
        }
        return new Renamer(myRenaming, myBanlist);
    }
}
