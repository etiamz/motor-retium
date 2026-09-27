package com.mycompany.app;

import com.mycompany.app.passes.GuardEliminator;
import com.mycompany.app.passes.OperatorSaturator;
import com.mycompany.app.passes.StrictnessAnalyzer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class App {
    private App() {
    }

    public static void main(final String[] args) throws IOException {
        final var source = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        try {
            final var compilation = new Compiler().compile(
                    new StrictnessAnalyzer().analyze(
                            new OperatorSaturator().saturate(
                                    new GuardEliminator()
                                            .eliminate(Parser.parse("<stdin>", source)))));
            Motor.initialize(compilation.book());
            final var root = new Port.Consumer(null);
            compilation.main().materialize(root, new Port.Producer[0]);
            final String output = Motor.whnf(root);
            System.out.println(output);
            if (Motor.statsEnabled()) {
                System.err.printf("Interactions: %d\n", Motor.ninteractions());
            }
        } catch (final SyntaxError e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (final Panic e) {
            System.err.println("Panic: " + e.getMessage());
            System.exit(1);
        }
    }
}
