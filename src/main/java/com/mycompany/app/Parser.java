package com.mycompany.app;

import com.mycompany.app.CheckedInteger.IntegerTy;
import com.mycompany.app.grammar.MotorBaseVisitor;
import com.mycompany.app.grammar.MotorLexer;
import com.mycompany.app.grammar.MotorParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.Trees;
import org.apache.commons.text.StringEscapeUtils;

public final class Parser {
    private static final Map<String, Primitives.Operator> UNARY_OPS = Map.ofEntries(
            Map.entry("fix", new Primitives.Fix()),
            Map.entry("not", new Primitives.Not()),
            Map.entry("$show", Primitives.StrictOp1.STRING_OF),
            Map.entry("$chr", Primitives.StrictOp1.STRING_OF_CHARACTER),
            Map.entry("negate", Primitives.StrictOp1.NEGATE),
            Map.entry("$ffs", Primitives.StrictOp1.FFS),
            Map.entry("$clz", Primitives.StrictOp1.CLZ),
            Map.entry("$ctz", Primitives.StrictOp1.CTZ),
            Map.entry("$clrsb", Primitives.StrictOp1.CLRSB),
            Map.entry("$popcount", Primitives.StrictOp1.POPCOUNT),
            Map.entry("$parity", Primitives.StrictOp1.PARITY),
            Map.entry("$strlen", Primitives.StrictOp1.STRLEN),
            Map.entry("$panic", Primitives.StrictOp1.PANIC),
            Map.entry("$hash", Primitives.StrictOp1.HASH),
            Map.entry("$memory", Primitives.StrictOp1.MEMORY));

    private static final Map<String, Primitives.Operator> BINARY_OPS = Map.ofEntries(
            Map.entry("$", new Primitives.Apply()),
            Map.entry("$!", new Primitives.StrictApply()),
            Map.entry("&&", new Primitives.And()),
            Map.entry("||", new Primitives.Or()),
            Map.entry("+", Primitives.StrictOp2.ADD),
            Map.entry("-", Primitives.StrictOp2.SUBTRACT),
            Map.entry("*", Primitives.StrictOp2.MULTIPLY),
            Map.entry("/", Primitives.StrictOp2.DIVIDE),
            Map.entry("%", Primitives.StrictOp2.REMAINDER),
            Map.entry("|", Primitives.StrictOp2.STRICT_OR),
            Map.entry("&", Primitives.StrictOp2.STRICT_AND),
            Map.entry("^", Primitives.StrictOp2.STRICT_XOR),
            Map.entry("<<", Primitives.StrictOp2.SHIFT_LEFT),
            Map.entry(">>", Primitives.StrictOp2.SHIFT_RIGHT),
            Map.entry("==", Primitives.StrictOp2.EQUALS),
            Map.entry("!=", Primitives.StrictOp2.NOT_EQUALS),
            Map.entry("<", Primitives.StrictOp2.LESS),
            Map.entry("<=", Primitives.StrictOp2.LESS_OR_EQUALS),
            Map.entry(">", Primitives.StrictOp2.GREATER),
            Map.entry(">=", Primitives.StrictOp2.GREATER_OR_EQUALS),
            Map.entry("$min", Primitives.StrictOp2.MIN),
            Map.entry("$max", Primitives.StrictOp2.MAX),
            Map.entry("$oftype", Primitives.StrictOp2.OFTYPE),
            Map.entry("@", Primitives.StrictOp2.CHARACTER_AT),
            Map.entry("@@", Primitives.StrictOp2.SLICE),
            Map.entry("++", Primitives.StrictOp2.PLUS_PLUS),
            Map.entry("$strcmp", Primitives.StrictOp2.STRCMP),
            Map.entry("$strchr", Primitives.StrictOp2.STRCHR),
            Map.entry("$strrchr", Primitives.StrictOp2.STRRCHR),
            Map.entry("$strstr", Primitives.StrictOp2.STRSTR),
            Map.entry("$strrstr", Primitives.StrictOp2.STRRSTR),
            Map.entry("$strspn", Primitives.StrictOp2.STRSPN),
            Map.entry("$strcspn", Primitives.StrictOp2.STRCSPN),
            Map.entry("$strpbrk", Primitives.StrictOp2.STRPBRK),
            Map.entry("$strrspn", Primitives.StrictOp2.STRRSPN),
            Map.entry("$strrcspn", Primitives.StrictOp2.STRRCSPN),
            Map.entry("$strrpbrk", Primitives.StrictOp2.STRRPBRK),
            Map.entry("$startswith", Primitives.StrictOp2.STARTSWITH),
            Map.entry("$endswith", Primitives.StrictOp2.ENDSWITH),
            Map.entry("$remember", Primitives.StrictOp2.REMEMBER));

    private static final Set<Class<?>> RANGE_CONTEXTS = Set.of(
            MotorParser.RangeTermContext.class,
            MotorParser.InclusiveRangeTermContext.class,
            MotorParser.RangeFromTermContext.class,
            MotorParser.RangeToTermContext.class,
            MotorParser.InclusiveRangeToTermContext.class,
            MotorParser.RangeFullTermContext.class);

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
        private final List<String> bound = new ArrayList<>();

        private Builder(final String filename) {
            this.filename = filename;
        }

        private void push(final String x) {
            bound.add(x);
        }

        private void pop(final String x) {
            bound.remove(x);
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
                if (name.equals("_")) {
                    throw error(filename, d, "`_` cannot be a top-level definition name");
                }
                if (!globals.add(name)) {
                    throw error(filename, d, "Found a duplicate top-level definition: `%s`", name);
                }
            }
            for (final var d : ctx.definition()) {
                final var name = d.SYMBOL(0).getText();
                final var parameters = d.SYMBOL().subList(1, d.SYMBOL().size());
                final var parameterNames = bindingNames(d, parameters);
                parameterNames.forEach(this::push);
                Term body = visit(d.term());
                parameterNames.forEach(this::pop);
                for (int i = parameterNames.size() - 1; i >= 0; i--) {
                    body = new Term.Lambda(parameterNames.get(i), body);
                }
                definitions.put(name, body);
            }
            if (!globals.contains("main")) {
                throw new SyntaxError("No `main` top-level definition");
            }
            return new Term.Reference("main");
        }

        @Override
        public Term visitIndexingTerm(final MotorParser.IndexingTermContext ctx) {
            return infix(ctx.op, ctx.term(0), ctx.term(1));
        }

        @Override
        public Term visitMultiplicativeTerm(final MotorParser.MultiplicativeTermContext ctx) {
            return infix(ctx.op, ctx.term(0), ctx.term(1));
        }

        @Override
        public Term visitAdditiveTerm(final MotorParser.AdditiveTermContext ctx) {
            return infix(ctx.op, ctx.term(0), ctx.term(1));
        }

        @Override
        public Term visitShiftTerm(final MotorParser.ShiftTermContext ctx) {
            return infix(ctx.op, ctx.term(0), ctx.term(1));
        }

        @Override
        public Term visitStrictAndTerm(final MotorParser.StrictAndTermContext ctx) {
            return infix(ctx.op, ctx.term(0), ctx.term(1));
        }

        @Override
        public Term visitStrictXorTerm(final MotorParser.StrictXorTermContext ctx) {
            return infix(ctx.op, ctx.term(0), ctx.term(1));
        }

        @Override
        public Term visitStrictOrTerm(final MotorParser.StrictOrTermContext ctx) {
            return infix(ctx.op, ctx.term(0), ctx.term(1));
        }

        @Override
        public Term visitConcatenationTerm(final MotorParser.ConcatenationTermContext ctx) {
            return infix(ctx.op, ctx.term(0), ctx.term(1));
        }

        @Override
        public Term visitComparisonTerm(final MotorParser.ComparisonTermContext ctx) {
            for (final var t : ctx.term()) {
                if (t instanceof MotorParser.ComparisonTermContext) {
                    throw error(filename, ctx, "Chained comparisons require parentheses");
                }
            }
            return infix(ctx.op, ctx.term(0), ctx.term(1));
        }

        @Override
        public Term visitConjunctionTerm(final MotorParser.ConjunctionTermContext ctx) {
            return infix(ctx.op, ctx.term(0), ctx.term(1));
        }

        @Override
        public Term visitDisjunctionTerm(final MotorParser.DisjunctionTermContext ctx) {
            return infix(ctx.op, ctx.term(0), ctx.term(1));
        }

        @Override
        public Term visitRangeTerm(final MotorParser.RangeTermContext ctx) {
            ctx.term().forEach(this::checkNotRange);
            final boolean inclusive = false;
            return new Term.Range(
                    Optional.of(visit(ctx.term(0))),
                    Optional.of(visit(ctx.term(1))),
                    inclusive);
        }

        @Override
        public Term visitInclusiveRangeTerm(final MotorParser.InclusiveRangeTermContext ctx) {
            ctx.term().forEach(this::checkNotRange);
            final boolean inclusive = true;
            return new Term.Range(
                    Optional.of(visit(ctx.term(0))),
                    Optional.of(visit(ctx.term(1))),
                    inclusive);
        }

        @Override
        public Term visitRangeFromTerm(final MotorParser.RangeFromTermContext ctx) {
            checkNotRange(ctx.term());
            final boolean inclusive = false;
            return new Term.Range(
                    Optional.of(visit(ctx.term())),
                    Optional.empty(),
                    inclusive);
        }

        @Override
        public Term visitRangeToTerm(final MotorParser.RangeToTermContext ctx) {
            checkNotRange(ctx.term());
            final boolean inclusive = false;
            return new Term.Range(
                    Optional.empty(),
                    Optional.of(visit(ctx.term())),
                    inclusive);
        }

        @Override
        public Term visitInclusiveRangeToTerm(final MotorParser.InclusiveRangeToTermContext ctx) {
            checkNotRange(ctx.term());
            final boolean inclusive = true;
            return new Term.Range(
                    Optional.empty(),
                    Optional.of(visit(ctx.term())),
                    inclusive);
        }

        @Override
        public Term visitRangeFullTerm(final MotorParser.RangeFullTermContext ctx) {
            final boolean inclusive = false;
            return new Term.Range(
                    Optional.empty(),
                    Optional.empty(),
                    inclusive);
        }

        @Override
        public Term visitApplyOpTerm(final MotorParser.ApplyOpTermContext ctx) {
            return infix(ctx.op, ctx.term(0), ctx.term(1));
        }

        @Override
        public Term visitLambdaTerm(final MotorParser.LambdaTermContext ctx) {
            final var parameters = ctx.SYMBOL();
            final var parameterNames = bindingNames(ctx, parameters);
            parameterNames.forEach(this::push);
            Term result = visit(ctx.term());
            parameterNames.forEach(this::pop);
            for (int i = parameterNames.size() - 1; i >= 0; i--) {
                result = new Term.Lambda(parameterNames.get(i), result);
            }
            return result;
        }

        @Override
        public Term visitLetTerm(final MotorParser.LetTermContext ctx) {
            final var x = bindingNames(ctx, List.of(ctx.SYMBOL())).getFirst();
            final var t1 = visit(ctx.term(0));
            push(x);
            final var t2 = visit(ctx.term(1));
            pop(x);
            return new Term.Application(new Term.Lambda(x, t2), t1);
        }

        @Override
        public Term visitStrictLetTerm(final MotorParser.StrictLetTermContext ctx) {
            final var x = bindingNames(ctx, List.of(ctx.SYMBOL())).getFirst();
            final var t1 = visit(ctx.term(0));
            push(x);
            final var t2 = visit(ctx.term(1));
            pop(x);
            return new Term.StrictApplication(new Term.Lambda(x, t2), t1);
        }

        @Override
        public Term visitPatternLetTerm(final MotorParser.PatternLetTermContext ctx) {
            final var name = ctx.CONSTRUCTOR().getText();
            final var parameters = ctx.SYMBOL();
            final var xs = bindingNames(ctx, parameters);
            final var seen = new LinkedHashSet<String>();
            for (final var x : xs) {
                if (!seen.add(x)) {
                    throw error(
                            filename,
                            ctx,
                            "Found a duplicate variable in a `let` pattern: `%s`",
                            x);
                }
            }
            checkArity(ctx, name, parameters.size());
            final var s = visit(ctx.term(0));
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
                final Predicate<MotorParser.CaseContext> isFallback = c -> c.term().size() == 1 &&
                        c.CONSTRUCTOR().getText().equals(name);
                if (prefix.stream().anyMatch(isFallback)) {
                    throw error(
                            filename,
                            myCase,
                            "Found a provably unreachable case for `%s`",
                            name);
                }
                if (myCase.term().size() > 1 && suffix.stream().noneMatch(isFallback)) {
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
                        final var xs = bindingNames(myCase, parameters);
                        final var seen = new LinkedHashSet<String>();
                        for (final var x : xs) {
                            if (!seen.add(x)) {
                                throw error(
                                        filename,
                                        myCase,
                                        "Found a duplicate variable in a case pattern: `%s`",
                                        x);
                            }
                        }
                        final var name = myCase.CONSTRUCTOR().getText();
                        checkArity(myCase, name, parameters.size());
                        final var terms = myCase.term();
                        xs.forEach(this::push);
                        final var guards = terms
                                .subList(0, terms.size() - 1)
                                .stream()
                                .map(this::visit)
                                .toList();
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
        public Term visitAtomTerm(final MotorParser.AtomTermContext ctx) {
            return visit(ctx.atom());
        }

        @Override
        public Term visitOperatorTerm(final MotorParser.OperatorTermContext ctx) {
            return operatorOf(ctx.op2().getStart());
        }

        @Override
        public Term visitGroupTerm(final MotorParser.GroupTermContext ctx) {
            return visit(ctx.term());
        }

        @Override
        public Term visitOp1Term(final MotorParser.Op1TermContext ctx) {
            return operatorOf(ctx.op1().getStart());
        }

        @Override
        public Term visitIntrinsicTerm(final MotorParser.IntrinsicTermContext ctx) {
            return operatorOf(ctx.intrinsic().getStart());
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
            final var text = ctx.INTEGER_LITERAL().getText();
            for (final var entry : INTEGER_TYPES.entrySet()) {
                final var suffix = entry.getKey();
                if (text.endsWith(suffix)) {
                    final var i = text.substring(0, text.length() - suffix.length());
                    try {
                        return new Term.IntegerLiteral(entry.getValue().ofString(i));
                    } catch (final CheckedInteger.OutOfRange e) {
                        throw error(filename, ctx, "`%s` is out of range", text);
                    }
                }
            }
            throw new IllegalStateException(
                    String.format(
                            "Unrecognized integer literal: `%s`",
                            StringEscapeUtils.escapeJava(text)));
        }

        @Override
        public Term visitBigIntegerTerm(final MotorParser.BigIntegerTermContext ctx) {
            final var text = ctx.BIG_INTEGER_LITERAL().getText();
            final var suffix = "bigint";
            if (!text.endsWith(suffix)) {
                throw new IllegalStateException(
                        String.format(
                                "Unrecognized big integer literal: `%s`",
                                StringEscapeUtils.escapeJava(text)));
            }
            final var i = text.substring(0, text.length() - suffix.length());
            try {
                return new Term.BigIntegerLiteral(MyBigInteger.ofString(i));
            } catch (final MyBigInteger.OutOfRange e) {
                throw error(filename, ctx, "`%s` is out of range", text);
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
        public Term visitVariableTerm(final MotorParser.VariableTermContext ctx) {
            final var x = ctx.SYMBOL().getText();
            if (bound.contains(x)) {
                return new Term.Variable(x);
            }
            if (globals.contains(x)) {
                return new Term.Reference(x);
            }
            throw error(filename, ctx, "Variable not in scope: `%s`", x);
        }

        private Term infix(final Token op, final ParseTree left, final ParseTree right) {
            return new Term.Application(
                    new Term.Application(operatorOf(op), visit(left)),
                    visit(right));
        }

        private void checkNotRange(final MotorParser.TermContext t) {
            if (RANGE_CONTEXTS.contains(t.getClass())) {
                throw error(filename, t, "Chained ranges require parentheses");
            }
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

        private static Set<String> freeVariables(final ParseTree tree) {
            final var result = new LinkedHashSet<String>();
            for (final var node : Trees.findAllTokenNodes(tree, MotorLexer.SYMBOL)) {
                result.add(node.getText());
            }
            return result;
        }

        private static List<String> bindingNames(
                final ParserRuleContext site,
                final List<TerminalNode> parameters) {
            final var banlist = freeVariables(site);
            return IntStream.range(0, parameters.size())
                    .mapToObj(i -> {
                        final var p = parameters.get(i).getText();
                        if (p.equals("_")) {
                            return Term.freshen("v" + i, banlist);
                        }
                        return p;
                    })
                    .toList();
        }

        private Term operatorOf(final Token token) {
            final var text = token.getText();
            if (UNARY_OPS.containsKey(text)) {
                return new Term.Operator(UNARY_OPS.get(text));
            }
            if (BINARY_OPS.containsKey(text)) {
                return new Term.Operator(BINARY_OPS.get(text));
            }
            throw new IllegalStateException(
                    String.format("Unknown operator: `%s`", StringEscapeUtils.escapeJava(text)));
        }
    }
}
