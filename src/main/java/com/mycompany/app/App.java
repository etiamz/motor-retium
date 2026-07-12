package com.mycompany.app;

import com.mycompany.app.Motor.ABigInteger;
import com.mycompany.app.Motor.AConstructor;
import com.mycompany.app.Motor.AFalse;
import com.mycompany.app.Motor.AIdentity;
import com.mycompany.app.Motor.AInteger;
import com.mycompany.app.Motor.ALambda;
import com.mycompany.app.Motor.AString;
import com.mycompany.app.Motor.ATrue;
import com.mycompany.app.Motor.Agent;
import com.mycompany.app.passes.GuardEliminator;
import com.mycompany.app.passes.OperatorSaturator;
import com.mycompany.app.passes.SharedVariableHoister;
import com.mycompany.app.passes.StrictnessAnalyzer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class App {
    private App() {
    }

    public static void main(final String[] args) throws IOException {
        final var source = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        try {
            final var program = SharedVariableHoister.hoist(
                    StrictnessAnalyzer.analyze(
                            OperatorSaturator.saturate(
                                    GuardEliminator.eliminate(
                                            Parser.parse("<stdin>", source)))));
            final var compilation = Compiler.compile(program);
            final var motor = new Motor(compilation.book());
            final var root = new Port.Consumer(null);
            compilation.main().materialize(root);
            System.out.println(show(motor, motor.whnf(root)));
        } catch (final SyntaxError e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (final Panic e) {
            System.err.println("Panic: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String show(final Motor motor, final Agent value) {
        return switch (value) {
            case ATrue _ -> "true";
            case AFalse _ -> "false";
            case AInteger i -> {
                final String suffix = (i.ty().isSigned ? "i" : "u") + i.ty().bits;
                yield i.data.show() + suffix;
            }
            case ABigInteger i -> i.data.show() + "bigint";
            case AString s -> s.data.toString();
            case AIdentity _ -> "<identity>";
            case ALambda _ -> "<function>";
            case AConstructor ctr when ctr.isNullary() -> ctr.name;
            case AConstructor ctr -> {
                final var arguments = IntStream.range(0, ctr.arity())
                        .mapToObj(i -> show(motor, motor.whnf(ctr.arguments[i])))
                        .collect(Collectors.joining(" "));
                yield String.format("(%s %s)", ctr.name, arguments);
            }
            default -> throw new IllegalStateException(
                    "Cannot print: `" + value.getClass().getSimpleName() + "`");
        };
    }
}
