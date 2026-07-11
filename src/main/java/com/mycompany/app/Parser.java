package com.mycompany.app;

import com.mycompany.app.CheckedInteger.IntegerTy;
import com.mycompany.app.grammar.MotorBaseVisitor;
import com.mycompany.app.grammar.MotorLexer;
import com.mycompany.app.grammar.MotorParser;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

public final class Parser {
    private static final Map<String, Primitives.Operator> INTEGER_TY_OPS = Map.ofEntries(
            Map.entry("u8", new Primitives.IntegerOf(IntegerTy.U8)),
            Map.entry("u16", new Primitives.IntegerOf(IntegerTy.U16)),
            Map.entry("u32", new Primitives.IntegerOf(IntegerTy.U32)),
            Map.entry("u64", new Primitives.IntegerOf(IntegerTy.U64)),
            Map.entry("i8", new Primitives.IntegerOf(IntegerTy.I8)),
            Map.entry("i16", new Primitives.IntegerOf(IntegerTy.I16)),
            Map.entry("i32", new Primitives.IntegerOf(IntegerTy.I32)),
            Map.entry("i64", new Primitives.IntegerOf(IntegerTy.I64)));

    private static final Map<Integer, Primitives.Operator> UNARY_OPS = Map.ofEntries(
            Map.entry(MotorParser.FIX, new Primitives.Fix()),
            Map.entry(MotorParser.NOT, new Primitives.Not()),
            Map.entry(MotorParser.BIGINT_TY, new Primitives.BigIntegerOf()),
            Map.entry(MotorParser.STRING_TY, new Primitives.StringOf()),
            Map.entry(MotorParser.STRING_OF_CHARACTER, new Primitives.StringOfCharacter()),
            Map.entry(MotorParser.NEGATE, new Primitives.Negate()),
            Map.entry(MotorParser.INTEGER_NOT, new Primitives.IntegerNot()),
            Map.entry(MotorParser.FFS, new Primitives.Ffs()),
            Map.entry(MotorParser.CLZ, new Primitives.Clz()),
            Map.entry(MotorParser.CTZ, new Primitives.Ctz()),
            Map.entry(MotorParser.CLRSB, new Primitives.Clrsb()),
            Map.entry(MotorParser.POPCOUNT, new Primitives.Popcount()),
            Map.entry(MotorParser.PARITY, new Primitives.Parity()),
            Map.entry(MotorParser.STRLEN, new Primitives.Strlen()),
            Map.entry(MotorParser.PANIC, new Primitives.Panic()),
            Map.entry(MotorParser.MEMORY, new Primitives.Memory()),
            Map.entry(MotorParser.HASH, new Primitives.Hash()));

    private static final Map<Integer, Primitives.Operator> BINARY_OPS = Map.ofEntries(
            Map.entry(MotorParser.AND, new Primitives.And()),
            Map.entry(MotorParser.OR, new Primitives.Or()),
            Map.entry(MotorParser.ADD, Primitives.StrictOp2.ADD),
            Map.entry(MotorParser.SUBTRACT, Primitives.StrictOp2.SUBTRACT),
            Map.entry(MotorParser.MULTIPLY, Primitives.StrictOp2.MULTIPLY),
            Map.entry(MotorParser.DIVIDE, Primitives.StrictOp2.DIVIDE),
            Map.entry(MotorParser.REMAINDER, Primitives.StrictOp2.REMAINDER),
            Map.entry(MotorParser.INTEGER_OR, Primitives.StrictOp2.INTEGER_OR),
            Map.entry(MotorParser.INTEGER_AND, Primitives.StrictOp2.INTEGER_AND),
            Map.entry(MotorParser.INTEGER_XOR, Primitives.StrictOp2.INTEGER_XOR),
            Map.entry(MotorParser.SHIFT_LEFT, Primitives.StrictOp2.SHIFT_LEFT),
            Map.entry(MotorParser.SHIFT_RIGHT, Primitives.StrictOp2.SHIFT_RIGHT),
            Map.entry(MotorParser.EQUALS, Primitives.StrictOp2.EQUALS),
            Map.entry(MotorParser.NOT_EQUALS, Primitives.StrictOp2.NOT_EQUALS),
            Map.entry(MotorParser.LESS, Primitives.StrictOp2.LESS),
            Map.entry(MotorParser.LESS_OR_EQUALS, Primitives.StrictOp2.LESS_OR_EQUALS),
            Map.entry(MotorParser.GREATER, Primitives.StrictOp2.GREATER),
            Map.entry(MotorParser.GREATER_OR_EQUALS, Primitives.StrictOp2.GREATER_OR_EQUALS),
            Map.entry(MotorParser.MIN, Primitives.StrictOp2.MIN),
            Map.entry(MotorParser.MAX, Primitives.StrictOp2.MAX),
            Map.entry(MotorParser.CHARACTER_AT, Primitives.StrictOp2.CHARACTER_AT),
            Map.entry(MotorParser.SLICE, Primitives.StrictOp2.SLICE),
            Map.entry(MotorParser.PLUS_PLUS, Primitives.StrictOp2.PLUS_PLUS),
            Map.entry(MotorParser.STRCMP, Primitives.StrictOp2.STRCMP),
            Map.entry(MotorParser.STRCHR, Primitives.StrictOp2.STRCHR),
            Map.entry(MotorParser.STRRCHR, Primitives.StrictOp2.STRRCHR),
            Map.entry(MotorParser.STRSTR, Primitives.StrictOp2.STRSTR),
            Map.entry(MotorParser.STRRSTR, Primitives.StrictOp2.STRRSTR),
            Map.entry(MotorParser.STRSPN, Primitives.StrictOp2.STRSPN),
            Map.entry(MotorParser.STRCSPN, Primitives.StrictOp2.STRCSPN),
            Map.entry(MotorParser.STRPBRK, Primitives.StrictOp2.STRPBRK),
            Map.entry(MotorParser.STRRSPN, Primitives.StrictOp2.STRRSPN),
            Map.entry(MotorParser.STRRCSPN, Primitives.StrictOp2.STRRCSPN),
            Map.entry(MotorParser.STRRPBRK, Primitives.StrictOp2.STRRPBRK),
            Map.entry(MotorParser.STARTSWITH, Primitives.StrictOp2.STARTSWITH),
            Map.entry(MotorParser.ENDSWITH, Primitives.StrictOp2.ENDSWITH),
            Map.entry(MotorParser.REMEMBER, Primitives.StrictOp2.REMEMBER));

    private static final Map<String, IntegerTy> INTEGER_TYPES = Map.ofEntries(
            Map.entry("u8", IntegerTy.U8),
            Map.entry("u16", IntegerTy.U16),
            Map.entry("u32", IntegerTy.U32),
            Map.entry("u64", IntegerTy.U64),
            Map.entry("i8", IntegerTy.I8),
            Map.entry("i16", IntegerTy.I16),
            Map.entry("i32", IntegerTy.I32),
            Map.entry("i64", IntegerTy.I64));

    private Parser() {
    }

    public static Program parse(final String filename, final String source) {
        final MotorLexer lexer = new MotorLexer(CharStreams.fromString(source));
        final var listener = new ThrowingErrorListener(filename);
        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        final MotorParser parser = new MotorParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(listener);
        final var builder = new Builder(filename);
        final var main = builder.visit(parser.program());
        return new Program(main, builder.definitions);
    }

    private static final class ThrowingErrorListener extends BaseErrorListener {
        private final String filename;

        private ThrowingErrorListener(final String filename) {
            this.filename = filename;
        }

        @Override
        public void syntaxError(
                final Recognizer<?, ?> recognizer,
                final Object offendingSymbol,
                final int line,
                final int charPositionInLine,
                final String msg,
                final RecognitionException e) {
            throw error(filename, line, charPositionInLine, "%s", msg);
        }
    }

    private static SyntaxError error(
            final String filename,
            final ParserRuleContext ctx,
            final String format,
            final Object... arguments) {
        return error(
                filename,
                ctx.getStart().getLine(),
                ctx.getStart().getCharPositionInLine(),
                format,
                arguments);
    }

    private static SyntaxError error(
            final String filename,
            final int line,
            final int charPositionInLine,
            final String format,
            final Object... arguments) {
        return new SyntaxError(
                String.format(
                        "%s:%d:%d: %s",
                        filename,
                        line,
                        charPositionInLine + 1,
                        String.format(format, arguments)));
    }

    private static final class Builder extends MotorBaseVisitor<Term> {
        private final String filename;
        private final Map<String, Term> definitions = new LinkedHashMap<>();
        private final Map<String, Integer> arities = new LinkedHashMap<>();
        private final Set<String> globals = new LinkedHashSet<>();
        // A multiset: the same variable can be bound at several nesting levels at once.
        private final Map<String, Integer> bound = new LinkedHashMap<>();

        private Builder(final String filename) {
            this.filename = filename;
        }

        private void push(final String x) {
            bound.merge(x, 1, Integer::sum);
        }

        private void pop(final String x) {
            bound.merge(x, -1, Integer::sum);
        }

        @Override
        public Term visitProgram(final MotorParser.ProgramContext ctx) {
            for (final var c : ctx.constructorDeclaration()) {
                final var name = c.CONSTRUCTOR().getText();
                if (arities.containsKey(name)) {
                    throw error(
                            filename,
                            c,
                            "Found a duplicate constructor declaration: `%s`",
                            name);
                }
                final var text = c.INTEGER().getText();
                final int arity;
                try {
                    arity = Integer.parseInt(text);
                } catch (final NumberFormatException _) {
                    throw error(filename, c, "Not a valid arity: `%s`", text);
                }
                if (arity < 0) {
                    throw error(filename, c, "Expected a non-negative arity: `%s`", text);
                }
                arities.put(name, arity);
            }
            for (final var d : ctx.definition()) {
                final var name = d.SYMBOL(0).getText();
                if (!globals.add(name)) {
                    throw error(filename, d, "Found a duplicate definition: `%s`", name);
                }
            }
            for (final var d : ctx.definition()) {
                final var name = d.SYMBOL(0).getText();
                final var parameters = d.SYMBOL().subList(1, d.SYMBOL().size());
                parameters.forEach(p -> push(p.getText()));
                Term body = visit(d.term());
                parameters.forEach(p -> pop(p.getText()));
                for (int i = parameters.size() - 1; i >= 0; i--) {
                    body = new Term.Lambda(parameters.get(i).getText(), body);
                }
                definitions.put(name, body);
            }
            if (!globals.contains("main")) {
                throw new SyntaxError("No `main` definition");
            }
            return new Term.Reference("main");
        }

        @Override
        public Term visitLambdaTerm(final MotorParser.LambdaTermContext ctx) {
            final var parameters = ctx.SYMBOL();
            parameters.forEach(p -> push(p.getText()));
            Term result = visit(ctx.term());
            parameters.forEach(p -> pop(p.getText()));
            for (int i = parameters.size() - 1; i >= 0; i--) {
                result = new Term.Lambda(parameters.get(i).getText(), result);
            }
            return result;
        }

        @Override
        public Term visitLetTerm(final MotorParser.LetTermContext ctx) {
            final var x = ctx.SYMBOL().getText();
            final var t1 = visit(ctx.term(0));
            push(x);
            final var t2 = visit(ctx.term(1));
            pop(x);
            return new Term.Application(new Term.Lambda(x, t2), t1);
        }

        @Override
        public Term visitStrictLetTerm(final MotorParser.StrictLetTermContext ctx) {
            final var x = ctx.SYMBOL().getText();
            final var t1 = visit(ctx.term(0));
            push(x);
            final var t2 = visit(ctx.term(1));
            pop(x);
            return new Term.StrictApplication(new Term.Lambda(x, t2), t1);
        }

        @Override
        public Term visitDestructuringLetTerm(final MotorParser.DestructuringLetTermContext ctx) {
            final var name = ctx.CONSTRUCTOR().getText();
            final var parameters = ctx.SYMBOL();
            final var seen = new LinkedHashSet<String>();
            for (final var p : parameters) {
                if (!seen.add(p.getText())) {
                    throw error(
                            filename,
                            ctx,
                            "Found a duplicate variable in a `let` pattern: `%s`",
                            p.getText());
                }
            }
            checkArity(ctx, name, parameters.size());
            final var s = visit(ctx.term(0));
            final var xs = List.copyOf(seen);
            final List<Term> guard = List.of();
            xs.forEach(this::push);
            final var continuation = visit(ctx.term(1));
            xs.forEach(this::pop);
            return new Term.Match(s, List.of(new Term.Case(name, xs, guard, continuation)));
        }

        @Override
        public Term visitIfThenElseTerm(final MotorParser.IfThenElseTermContext ctx) {
            final var t1 = ctx.term(0);
            final var t2 = ctx.term(1);
            final var t3 = ctx.term(2);
            return new Term.IfThenElse(visit(t1), visit(t2), visit(t3));
        }

        @Override
        public Term visitCaseTerm(final MotorParser.CaseTermContext ctx) {
            for (int i = 0; i < ctx.case_().size(); i++) {
                final var myCase = ctx.case_(i);
                final var name = myCase.CONSTRUCTOR().getText();
                final var prefix = ctx.case_().subList(0, i);
                final var suffix = ctx.case_().subList(i + 1, ctx.case_().size());
                final Predicate<MotorParser.CaseContext> isFallback = c -> c.INTEGER_OR() == null &&
                        c.CONSTRUCTOR().getText().equals(name);
                if (prefix.stream().anyMatch(isFallback)) {
                    throw error(
                            filename,
                            myCase,
                            "Found a provably unreachable case for `%s`",
                            name);
                }
                if (myCase.INTEGER_OR() != null && suffix.stream().noneMatch(isFallback)) {
                    throw error(
                            filename,
                            myCase,
                            "No fallback case for `%s` is provided",
                            name);
                }
            }
            final var s = visit(ctx.term());
            final var cases = ctx.case_().stream()
                    .map(myCase -> {
                        final var parameters = myCase.SYMBOL();
                        final var seen = new LinkedHashSet<String>();
                        for (final var p : parameters) {
                            if (!seen.add(p.getText())) {
                                throw error(
                                        filename,
                                        myCase,
                                        "Found a duplicate variable in a case pattern: `%s`",
                                        p.getText());
                            }
                        }
                        final var name = myCase.CONSTRUCTOR().getText();
                        checkArity(myCase, name, parameters.size());
                        final var xs = List.copyOf(seen);
                        final var terms = myCase.term();
                        xs.forEach(this::push);
                        final var guards = terms.subList(0, terms.size() - 1).stream()
                                .map(this::visit).toList();
                        final var t = visit(terms.getLast());
                        xs.forEach(this::pop);
                        return new Term.Case(name, xs, guards, t);
                    })
                    .toList();
            return new Term.Match(s, cases);
        }

        @Override
        public Term visitApplicationTerm(final MotorParser.ApplicationTermContext ctx) {
            return visit(ctx.application());
        }

        @Override
        public Term visitApplyTerm(final MotorParser.ApplyTermContext ctx) {
            return new Term.Application(visit(ctx.application()), visit(ctx.atom()));
        }

        @Override
        public Term visitNonStrictApplyTerm(final MotorParser.NonStrictApplyTermContext ctx) {
            return new Term.Application(visit(ctx.application()), visit(ctx.term()));
        }

        @Override
        public Term visitStrictApplyTerm(final MotorParser.StrictApplyTermContext ctx) {
            return new Term.StrictApplication(visit(ctx.application()), visit(ctx.term()));
        }

        @Override
        public Term visitAtomTerm(final MotorParser.AtomTermContext ctx) {
            return visit(ctx.atom());
        }

        @Override
        public Term visitInfixTerm(final MotorParser.InfixTermContext ctx) {
            final var op = operatorOf(ctx.op2().getStart());
            return new Term.Application(
                    new Term.Application(op, visit(ctx.application(0))),
                    visit(ctx.application(1)));
        }

        @Override
        public Term visitOperatorTerm(final MotorParser.OperatorTermContext ctx) {
            return operatorOf(ctx.op2().getStart());
        }

        @Override
        public Term visitOp1Term(final MotorParser.Op1TermContext ctx) {
            return operatorOf(ctx.op1().getStart());
        }

        @Override
        public Term visitIntrinsicTerm(final MotorParser.IntrinsicTermContext ctx) {
            return operatorOf(ctx.intrinsic().getStart());
        }

        private Term operatorOf(final Token token) {
            final int op = token.getType();
            if (INTEGER_TY_OPS.containsKey(token.getText())) {
                return new Term.Operator(INTEGER_TY_OPS.get(token.getText()));
            }
            if (UNARY_OPS.containsKey(op)) {
                return new Term.Operator(UNARY_OPS.get(op));
            }
            if (BINARY_OPS.containsKey(op)) {
                return new Term.Operator(BINARY_OPS.get(op));
            }
            throw new IllegalStateException(String.format("Unknown operator %d", op));
        }

        @Override
        public Term visitGroupTerm(final MotorParser.GroupTermContext ctx) {
            return visit(ctx.term());
        }

        @Override
        public Term visitRangeTerm(final MotorParser.RangeTermContext ctx) {
            return new Term.Range(Optional.of(visit(ctx.term(0))), Optional.of(visit(ctx.term(1))));
        }

        @Override
        public Term visitRangeFromTerm(final MotorParser.RangeFromTermContext ctx) {
            return new Term.Range(Optional.of(visit(ctx.term())), Optional.empty());
        }

        @Override
        public Term visitRangeToTerm(final MotorParser.RangeToTermContext ctx) {
            return new Term.Range(Optional.empty(), Optional.of(visit(ctx.term())));
        }

        @Override
        public Term visitRangeFullTerm(final MotorParser.RangeFullTermContext ctx) {
            return new Term.Range(Optional.empty(), Optional.empty());
        }

        @Override
        public Term visitTrueTerm(final MotorParser.TrueTermContext ctx) {
            return new Term.BooleanLiteral(true);
        }

        @Override
        public Term visitFalseTerm(final MotorParser.FalseTermContext ctx) {
            return new Term.BooleanLiteral(false);
        }

        @Override
        public Term visitIntegerTerm(final MotorParser.IntegerTermContext ctx) {
            final var i = ctx.INTEGER().getText();
            final var suffix = ctx.INTEGER_TY().getText();
            final var ty = INTEGER_TYPES.get(suffix);
            try {
                return new Term.IntegerLiteral(Objects.requireNonNull(ty).ofString(i));
            } catch (final CheckedInteger.OutOfRange e) {
                throw error(filename, ctx, "`%s:%s` is out of range", i, suffix);
            }
        }

        @Override
        public Term visitBigIntegerTerm(final MotorParser.BigIntegerTermContext ctx) {
            final var i = ctx.INTEGER().getText();
            try {
                return new Term.BigIntegerLiteral(MyBigInteger.ofString(i));
            } catch (final MyBigInteger.OutOfRange e) {
                throw error(filename, ctx, "`%s:bigint` is out of range", i);
            }
        }

        @Override
        public Term visitCharacterTerm(final MotorParser.CharacterTermContext ctx) {
            final String text = ctx.CHARACTER().getText();
            final int code = MyString.unescapeCharacter(text.substring(1, text.length() - 1));
            return new Term.IntegerLiteral(IntegerTy.U8.ofLong(code));
        }

        @Override
        public Term visitStringTerm(final MotorParser.StringTermContext ctx) {
            final String text = ctx.STRING().getText();
            final MyString s = MyString.unescape(text.substring(1, text.length() - 1));
            return new Term.StringLiteral(s);
        }

        @Override
        public Term visitConstructorTerm(final MotorParser.ConstructorTermContext ctx) {
            final var name = ctx.CONSTRUCTOR().getText();
            final var arity = arities.get(name);
            if (arity == null) {
                throw error(filename, ctx, "Constructor not declared: `%s`", name);
            }
            return new Term.Constructor(name, List.of(), arity);
        }

        private void checkArity(final ParserRuleContext ctx, final String name, final int actual) {
            final var expected = arities.get(name);
            if (expected == null) {
                throw error(filename, ctx, "Constructor not declared: `%s`", name);
            }
            if (expected != actual) {
                throw error(
                        filename,
                        ctx,
                        "Constructor `%s` expects %d argument(s), but got %d",
                        name,
                        expected,
                        actual);
            }
        }

        @Override
        public Term visitVariableTerm(final MotorParser.VariableTermContext ctx) {
            final var x = ctx.SYMBOL().getText();
            if (bound.getOrDefault(x, 0) > 0) {
                return new Term.Variable(x);
            }
            if (globals.contains(x)) {
                return new Term.Reference(x);
            }
            throw error(filename, ctx, "Variable not in scope: `%s`", x);
        }
    }
}
