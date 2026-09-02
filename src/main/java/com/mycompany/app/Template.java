package com.mycompany.app;

import com.mycompany.app.Primitives.StrictOp1;
import com.mycompany.app.Primitives.StrictOp2;
import com.mycompany.app.Term.StrictnessSource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedMap;
import java.util.Set;

public final class Template {
    private sealed interface Payload permits
            // The interface.
            PRoot,
            // Operators.
            PReference, PStrictOp1, PStrictOp2, PIfThenElse, PExpansion, PNot, PAnd, POr, PDoRange,
            PDoRangeFrom, PDoRangeTo, PApplicator, PStrictApplicator, PResolver, PCapture, PMatch,
            PConstructorResolver, PSelect, PDuplicator,
            // Data.
            PLambda, PEndOfList, PNull, PTrue, PFalse, PInteger, PBigInteger, PString, PRangeFull,
            PIdentity, PConstructor {
    }

    private record PRoot() implements Payload {
    }

    private record PReference(String name) implements Payload {
    }

    private record PStrictOp1(StrictOp1 op) implements Payload {
    }

    private record PStrictOp2(StrictOp2 op) implements Payload {
    }

    private record PIfThenElse() implements Payload {
    }

    private record PExpansion(Template template) implements Payload {
    }

    private record PNot() implements Payload {
    }

    private record PAnd() implements Payload {
    }

    private record POr() implements Payload {
    }

    private record PDoRange(boolean inclusive) implements Payload {
    }

    private record PDoRangeFrom() implements Payload {
    }

    private record PDoRangeTo(boolean inclusive) implements Payload {
    }

    private record PApplicator() implements Payload {
    }

    private record PStrictApplicator() implements Payload {
    }

    private record PResolver() implements Payload {
    }

    private record PCapture() implements Payload {
    }

    private record PMatch(String[] names /* interned */) implements Payload {
    }

    private record PConstructorResolver(String name /* interned */, int arity) implements Payload {
    }

    private record PSelect(String name /* interned */, int index) implements Payload {
    }

    private record PDuplicator() implements Payload {
    }

    private record PLambda() implements Payload {
    }

    private record PEndOfList() implements Payload {
    }

    private record PNull() implements Payload {
    }

    private record PTrue() implements Payload {
    }

    private record PFalse() implements Payload {
    }

    private record PInteger(CheckedInteger.Value value) implements Payload {
    }

    private record PBigInteger(MyBigInteger value) implements Payload {
    }

    private record PString(MyString value) implements Payload {
    }

    private record PRangeFull() implements Payload {
    }

    private record PIdentity() implements Payload {
    }

    private record PConstructor(String name /* interned */, int arity) implements Payload {
    }

    // The array of objects that hold auxiliary data for run-time agents.
    private final Payload[] payloads;
    // `links[i]` is the index of the producer that the `i`th consumer reads.
    private final int[] links;
    // The total number of template-local producers.
    private final int nproducers;
    // The total number of imported producers.
    private final int nimports;

    private Template(
            final Payload[] payloads,
            final int[] links,
            final int nproducers,
            final int nimports) {
        this.payloads = payloads;
        this.links = links;
        this.nproducers = nproducers;
        this.nimports = nimports;
    }

    public int nimports() {
        return nimports;
    }

    public void materialize(final Port.Consumer consumer, final Port.Producer[] imports) {
        if (imports.length != nimports) {
            throw new IllegalArgumentException(
                    String.format("Expected %d imports, got %d", nimports, imports.length));
        }
        final Port.Consumer[] consumers = new Port.Consumer[links.length];
        final Port.Producer[] producers = new Port.Producer[nproducers + nimports];
        int i = 0, j = 0;
        for (final Payload payload : payloads) {
            switch (payload) {
                case PRoot _ -> consumers[i++] = consumer;
                case PReference p -> producers[j++] = new Motor.AReference(p.name).a;
                case PStrictOp1 p -> {
                    final var agent = new Motor.AStrictOp1(p.op);
                    consumers[i++] = agent.a;
                    producers[j++] = agent.b;
                }
                case PStrictOp2 p -> {
                    final var agent = new Motor.AStrictOp2(p.op);
                    consumers[i++] = agent.a;
                    producers[j++] = agent.b;
                    consumers[i++] = agent.c;
                }
                case PIfThenElse _ -> {
                    final var agent = new Motor.AIfThenElse();
                    consumers[i++] = agent.a;
                    producers[j++] = agent.b;
                    consumers[i++] = agent.c;
                    consumers[i++] = agent.d;
                }
                case PExpansion p -> {
                    final var agent = new Motor.AExpansion(p.template);
                    producers[j++] = agent.a;
                    for (final var port : agent.imports) {
                        consumers[i++] = port;
                    }
                }
                case PNot _ -> {
                    final var agent = new Motor.ANot();
                    consumers[i++] = agent.a;
                    producers[j++] = agent.b;
                }
                case PAnd _ -> {
                    final var agent = new Motor.AAnd();
                    consumers[i++] = agent.a;
                    producers[j++] = agent.b;
                    consumers[i++] = agent.c;
                }
                case POr _ -> {
                    final var agent = new Motor.AOr();
                    consumers[i++] = agent.a;
                    producers[j++] = agent.b;
                    consumers[i++] = agent.c;
                }
                case PDoRange p -> {
                    final var agent = new Motor.ADoRange(p.inclusive);
                    consumers[i++] = agent.a;
                    producers[j++] = agent.b;
                    consumers[i++] = agent.c;
                }
                case PDoRangeFrom _ -> {
                    final var agent = new Motor.ADoRangeFrom();
                    consumers[i++] = agent.a;
                    producers[j++] = agent.b;
                }
                case PDoRangeTo p -> {
                    final var agent = new Motor.ADoRangeTo(p.inclusive);
                    consumers[i++] = agent.a;
                    producers[j++] = agent.b;
                }
                case PApplicator _ -> {
                    final var agent = new Motor.AApplicator();
                    consumers[i++] = agent.a;
                    producers[j++] = agent.b;
                    consumers[i++] = agent.c;
                }
                case PStrictApplicator _ -> {
                    final var agent = new Motor.AStrictApplicator();
                    consumers[i++] = agent.a;
                    producers[j++] = agent.b;
                    consumers[i++] = agent.c;
                }
                case PResolver _ -> {
                    final var agent = new Motor.AResolver();
                    consumers[i++] = agent.a;
                    producers[j++] = agent.b;
                    producers[j++] = agent.c;
                    consumers[i++] = agent.d;
                }
                case PCapture _ -> {
                    final var agent = new Motor.ACapture();
                    consumers[i++] = agent.a;
                    producers[j++] = agent.b;
                    producers[j++] = agent.c;
                    consumers[i++] = agent.d;
                }
                case PMatch p -> {
                    final var agent = new Motor.AMatch(p.names);
                    consumers[i++] = agent.a;
                    producers[j++] = agent.b;
                    for (final var port : agent.handlers) {
                        consumers[i++] = port;
                    }
                }
                case PConstructorResolver p -> {
                    final var agent = new Motor.AConstructorResolver(p.name, p.arity);
                    consumers[i++] = agent.a;
                    producers[j++] = agent.b;
                    for (final var port : agent.arguments) {
                        consumers[i++] = port;
                    }
                }
                case PSelect p -> {
                    final var agent = new Motor.ASelect(p.name, p.index);
                    consumers[i++] = agent.a;
                    producers[j++] = agent.b;
                }
                case PDuplicator _ -> {
                    final var agent = new Motor.ADuplicator(Motor.Label.COPY);
                    consumers[i++] = agent.a;
                    producers[j++] = agent.b;
                    producers[j++] = agent.c;
                }
                case PLambda _ -> {
                    final var agent = new Motor.ALambda();
                    producers[j++] = agent.a;
                    producers[j++] = agent.b;
                    consumers[i++] = agent.c;
                }
                case PEndOfList _ -> producers[j++] = new Motor.AEndOfList().a;
                case PNull _ -> producers[j++] = new Motor.ANull().a;
                case PTrue _ -> producers[j++] = new Motor.ATrue().a;
                case PFalse _ -> producers[j++] = new Motor.AFalse().a;
                case PInteger p -> producers[j++] = new Motor.AInteger(p.value).a;
                case PBigInteger p -> producers[j++] = new Motor.ABigInteger(p.value).a;
                case PString p -> producers[j++] = new Motor.AString(p.value).a;
                case PRangeFull _ -> producers[j++] = new Motor.ARangeFull().a;
                case PIdentity _ -> producers[j++] = new Motor.AIdentity().a;
                case PConstructor p -> {
                    final var agent = new Motor.AConstructor(p.name, p.arity);
                    producers[j++] = agent.a;
                    for (final var port : agent.arguments) {
                        consumers[i++] = port;
                    }
                }
            }
        }
        System.arraycopy(imports, 0, producers, j, nimports);
        for (int k = 0; k < links.length; k++) {
            consumers[k].setProducer(producers[links[k]]);
        }
    }

    public static final class Builder {
        // This class follows the same design as run-time `Port.Consumer`.
        public static final class Consumer {
            private Producer producer;

            public Consumer(final Producer producer) {
                this.producer = producer;
            }

            public Producer producer() {
                return this.producer;
            }

            public Agent chase() {
                Producer port = this.producer;
                while (port.meaning instanceof Producer forwarder) {
                    port = forwarder;
                }
                this.producer = port;
                return (Agent) port.meaning;
            }

            public void setProducer(final Producer producer) {
                this.producer = producer;
            }
        }

        // This class follows the same design as run-time `Port.Producer`.
        public static final class Producer {
            private Object meaning;

            public Producer(final Agent owner) {
                this.meaning = owner;
            }

            public void forward(final Producer other) {
                this.meaning = other;
            }
        }

        public sealed interface Agent permits
                // The interface.
                ARoot,
                // Operators.
                AReference, AStrictOp1, AStrictOp2, AIfThenElse, AExpansion, ANot, AAnd, AOr,
                ADoRange, ADoRangeFrom, ADoRangeTo, AApplicator, AStrictApplicator, AResolver,
                ACapture, AMatch, AConstructorResolver, ASelect, ADuplicator,
                // Data.
                ALambda, AEndOfList, ANull, ATrue, AFalse, AInteger, ABigInteger, AString,
                ARangeFull, AIdentity, AConstructor {
        }

        // Data agents that can stand in the function position of an application.
        public sealed interface Callable permits ALambda, AIdentity {
        }

        public static final class ARoot implements Agent {
            private final Consumer a;

            private ARoot() {
                this.a = new Consumer(null);
            }

            public Consumer a() {
                return a;
            }
        }

        public static final class AReference implements Agent {
            private final String name;
            private final Producer a;

            private AReference(final String name) {
                this.name = name;
                this.a = new Producer(this);
            }

            public Producer a() {
                return a;
            }
        }

        public static final class AStrictOp1 implements Agent {
            private final StrictOp1 op;
            private final Consumer a;
            private final Producer b;

            private AStrictOp1(final StrictOp1 op) {
                this.op = op;
                this.a = new Consumer(null);
                this.b = new Producer(this);
            }

            public Consumer a() {
                return a;
            }

            public Producer b() {
                return b;
            }
        }

        public static final class AStrictOp2 implements Agent {
            private final StrictOp2 op;
            private final Consumer a;
            private final Producer b;
            private final Consumer c;

            private AStrictOp2(final StrictOp2 op) {
                this.op = op;
                this.a = new Consumer(null);
                this.b = new Producer(this);
                this.c = new Consumer(null);
            }

            public Consumer a() {
                return a;
            }

            public Producer b() {
                return b;
            }

            public Consumer c() {
                return c;
            }
        }

        public static final class AIfThenElse implements Agent {
            private final Consumer a;
            private final Producer b;
            private final Consumer c;
            private final Consumer d;

            private AIfThenElse() {
                this.a = new Consumer(null);
                this.b = new Producer(this);
                this.c = new Consumer(null);
                this.d = new Consumer(null);
            }

            public Consumer a() {
                return a;
            }

            public Producer b() {
                return b;
            }

            public Consumer c() {
                return c;
            }

            public Consumer d() {
                return d;
            }
        }

        public static final class AExpansion implements Agent {
            private final Builder inner;
            private final Producer a;
            private final SequencedMap<String, Consumer> imports;

            private AExpansion(final Builder inner) {
                this.inner = inner;
                this.a = new Producer(this);
                this.imports = new LinkedHashMap<>();
                for (final String name : inner.imports.keySet()) {
                    this.imports.put(name, new Consumer(null));
                }
            }

            public Producer a() {
                return a;
            }

            public Consumer imported(final String name) {
                return imports.get(name);
            }
        }

        public static final class ANot implements Agent {
            private final Consumer a;
            private final Producer b;

            private ANot() {
                this.a = new Consumer(null);
                this.b = new Producer(this);
            }

            public Consumer a() {
                return a;
            }

            public Producer b() {
                return b;
            }
        }

        public static final class AAnd implements Agent {
            private final Consumer a;
            private final Producer b;
            private final Consumer c;

            private AAnd() {
                this.a = new Consumer(null);
                this.b = new Producer(this);
                this.c = new Consumer(null);
            }

            public Consumer a() {
                return a;
            }

            public Producer b() {
                return b;
            }

            public Consumer c() {
                return c;
            }
        }

        public static final class AOr implements Agent {
            private final Consumer a;
            private final Producer b;
            private final Consumer c;

            private AOr() {
                this.a = new Consumer(null);
                this.b = new Producer(this);
                this.c = new Consumer(null);
            }

            public Consumer a() {
                return a;
            }

            public Producer b() {
                return b;
            }

            public Consumer c() {
                return c;
            }
        }

        public static final class ADoRange implements Agent {
            private final boolean inclusive;
            private final Consumer a;
            private final Producer b;
            private final Consumer c;

            private ADoRange(final boolean inclusive) {
                this.inclusive = inclusive;
                this.a = new Consumer(null);
                this.b = new Producer(this);
                this.c = new Consumer(null);
            }

            public Consumer a() {
                return a;
            }

            public Producer b() {
                return b;
            }

            public Consumer c() {
                return c;
            }
        }

        public static final class ADoRangeFrom implements Agent {
            private final Consumer a;
            private final Producer b;

            private ADoRangeFrom() {
                this.a = new Consumer(null);
                this.b = new Producer(this);
            }

            public Consumer a() {
                return a;
            }

            public Producer b() {
                return b;
            }
        }

        public static final class ADoRangeTo implements Agent {
            private final boolean inclusive;
            private final Consumer a;
            private final Producer b;

            private ADoRangeTo(final boolean inclusive) {
                this.inclusive = inclusive;
                this.a = new Consumer(null);
                this.b = new Producer(this);
            }

            public Consumer a() {
                return a;
            }

            public Producer b() {
                return b;
            }
        }

        public static final class AApplicator implements Agent {
            private final Consumer a;
            private final Producer b;
            private final Consumer c;

            private AApplicator() {
                this.a = new Consumer(null);
                this.b = new Producer(this);
                this.c = new Consumer(null);
            }

            public Consumer a() {
                return a;
            }

            public Producer b() {
                return b;
            }

            public Consumer c() {
                return c;
            }
        }

        public static final class AStrictApplicator implements Agent {
            private final StrictnessSource source;
            private final Consumer a;
            private final Producer b;
            private final Consumer c;

            private AStrictApplicator(final StrictnessSource source) {
                this.source = source;
                this.a = new Consumer(null);
                this.b = new Producer(this);
                this.c = new Consumer(null);
            }

            public Consumer a() {
                return a;
            }

            public Producer b() {
                return b;
            }

            public Consumer c() {
                return c;
            }
        }

        public static final class AResolver implements Agent {
            private final Consumer a;
            private final Producer b;
            private final Producer c;
            private final Consumer d;

            private AResolver() {
                this.a = new Consumer(null);
                this.b = new Producer(this);
                this.c = new Producer(this);
                this.d = new Consumer(null);
            }

            public Consumer a() {
                return a;
            }

            public Producer b() {
                return b;
            }

            public Producer c() {
                return c;
            }

            public Consumer d() {
                return d;
            }
        }

        public static final class ACapture implements Agent {
            private final Consumer a;
            private final Producer b;
            private final Producer c;
            private final Consumer d;

            private ACapture() {
                this.a = new Consumer(null);
                this.b = new Producer(this);
                this.c = new Producer(this);
                this.d = new Consumer(null);
            }

            public Consumer a() {
                return a;
            }

            public Producer b() {
                return b;
            }

            public Producer c() {
                return c;
            }

            public Consumer d() {
                return d;
            }
        }

        public static final class AMatch implements Agent {
            private final String[] names;
            private final Consumer a;
            private final Producer b;
            private final Consumer[] handlers;

            private AMatch(final String[] names) {
                this.names = Arrays.stream(names).map(String::intern).toArray(String[]::new);
                this.a = new Consumer(null);
                this.b = new Producer(this);
                this.handlers = new Consumer[names.length];
                for (int i = 0; i < names.length; i++) {
                    this.handlers[i] = new Consumer(null);
                }
            }

            public Consumer a() {
                return a;
            }

            public Producer b() {
                return b;
            }

            public Consumer handler(final int i) {
                return handlers[i];
            }
        }

        public static final class AConstructorResolver implements Agent {
            private final String name;
            private final Consumer a;
            private final Producer b;
            private final Consumer[] arguments;

            private AConstructorResolver(final String name, final int arity) {
                this.name = name.intern();
                this.a = new Consumer(null);
                this.b = new Producer(this);
                this.arguments = new Consumer[arity];
                for (int i = 0; i < arity; i++) {
                    this.arguments[i] = new Consumer(null);
                }
            }

            public Consumer a() {
                return a;
            }

            public Producer b() {
                return b;
            }

            public Consumer argument(final int i) {
                return arguments[i];
            }
        }

        public static final class ASelect implements Agent {
            private final String name;
            private final int index;
            private final Consumer a;
            private final Producer b;

            private ASelect(final String name, final int index) {
                this.name = name.intern();
                this.index = index;
                this.a = new Consumer(null);
                this.b = new Producer(this);
            }

            public Consumer a() {
                return a;
            }

            public Producer b() {
                return b;
            }
        }

        public static final class ADuplicator implements Agent {
            private final Consumer a;
            private final Producer b;
            private final Producer c;

            private ADuplicator() {
                this.a = new Consumer(null);
                this.b = new Producer(this);
                this.c = new Producer(this);
            }

            public Consumer a() {
                return a;
            }

            public Producer b() {
                return b;
            }

            public Producer c() {
                return c;
            }
        }

        public static final class ALambda implements Agent, Callable {
            private final Producer a;
            private final Producer b;
            private final Consumer c;

            private ALambda() {
                this.a = new Producer(this);
                this.b = new Producer(this);
                this.c = new Consumer(null);
            }

            public Producer a() {
                return a;
            }

            public Producer b() {
                return b;
            }

            public Consumer c() {
                return c;
            }
        }

        public static final class AEndOfList implements Agent {
            private final Producer a;

            private AEndOfList() {
                this.a = new Producer(this);
            }

            public Producer a() {
                return a;
            }
        }

        public static final class ANull implements Agent {
            private final Producer a;

            private ANull() {
                this.a = new Producer(this);
            }

            public Producer a() {
                return a;
            }
        }

        public static final class ATrue implements Agent {
            private final Producer a;

            private ATrue() {
                this.a = new Producer(this);
            }

            public Producer a() {
                return a;
            }
        }

        public static final class AFalse implements Agent {
            private final Producer a;

            private AFalse() {
                this.a = new Producer(this);
            }

            public Producer a() {
                return a;
            }
        }

        public static final class AInteger implements Agent {
            private final CheckedInteger.Value value;
            private final Producer a;

            private AInteger(final CheckedInteger.Value value) {
                this.value = value;
                this.a = new Producer(this);
            }

            public Producer a() {
                return a;
            }
        }

        public static final class ABigInteger implements Agent {
            private final MyBigInteger value;
            private final Producer a;

            private ABigInteger(final MyBigInteger value) {
                this.value = value;
                this.a = new Producer(this);
            }

            public Producer a() {
                return a;
            }
        }

        public static final class AString implements Agent {
            private final MyString value;
            private final Producer a;

            private AString(final MyString value) {
                this.value = value;
                this.a = new Producer(this);
            }

            public Producer a() {
                return a;
            }
        }

        public static final class ARangeFull implements Agent {
            private final Producer a;

            private ARangeFull() {
                this.a = new Producer(this);
            }

            public Producer a() {
                return a;
            }
        }

        public static final class AIdentity implements Agent, Callable {
            private final Producer a;

            private AIdentity() {
                this.a = new Producer(this);
            }

            public Producer a() {
                return a;
            }
        }

        public static final class AConstructor implements Agent {
            private final String name;
            private final Producer a;
            private final Consumer[] arguments;

            private AConstructor(final String name, final int arity) {
                this.name = name.intern();
                this.a = new Producer(this);
                this.arguments = new Consumer[arity];
                for (int i = 0; i < arity; i++) {
                    this.arguments[i] = new Consumer(null);
                }
            }

            public Producer a() {
                return a;
            }

            public Consumer argument(final int i) {
                return arguments[i];
            }

            public boolean isNullary() {
                return arguments.length == 0;
            }
        }

        private final SequencedMap<String, Producer> imports = new LinkedHashMap<>();
        private ARoot root;

        public ARoot mkRoot() {
            if (root != null) {
                throw new IllegalStateException("Attempting to create a second root");
            }
            root = new ARoot();
            return root;
        }

        public AReference mkReference(final String name) {
            return new AReference(name);
        }

        public AStrictOp1 mkStrictOp1(final StrictOp1 op) {
            return new AStrictOp1(op);
        }

        public AStrictOp2 mkStrictOp2(final StrictOp2 op) {
            return new AStrictOp2(op);
        }

        public AIfThenElse mkIfThenElse() {
            return new AIfThenElse();
        }

        public AExpansion mkExpansion(final Builder inner) {
            return new AExpansion(inner);
        }

        public ANot mkNot() {
            return new ANot();
        }

        public AAnd mkAnd() {
            return new AAnd();
        }

        public AOr mkOr() {
            return new AOr();
        }

        public ADoRange mkDoRange(final boolean inclusive) {
            return new ADoRange(inclusive);
        }

        public ADoRangeFrom mkDoRangeFrom() {
            return new ADoRangeFrom();
        }

        public ADoRangeTo mkDoRangeTo(final boolean inclusive) {
            return new ADoRangeTo(inclusive);
        }

        public AApplicator mkApplicator() {
            return new AApplicator();
        }

        public AStrictApplicator mkStrictApplicator(final StrictnessSource source) {
            return new AStrictApplicator(source);
        }

        public AResolver mkResolver() {
            return new AResolver();
        }

        public ACapture mkCapture() {
            return new ACapture();
        }

        public AMatch mkMatch(final String[] names) {
            return new AMatch(names);
        }

        public AConstructorResolver mkConstructorResolver(final String name, final int arity) {
            return new AConstructorResolver(name, arity);
        }

        public ASelect mkSelect(final String name, final int index) {
            return new ASelect(name, index);
        }

        public ADuplicator mkDuplicator() {
            return new ADuplicator();
        }

        public ALambda mkLambda() {
            return new ALambda();
        }

        public AEndOfList mkEndOfList() {
            return new AEndOfList();
        }

        public ANull mkNull() {
            return new ANull();
        }

        public ATrue mkTrue() {
            return new ATrue();
        }

        public AFalse mkFalse() {
            return new AFalse();
        }

        public AInteger mkInteger(final CheckedInteger.Value i) {
            return new AInteger(i);
        }

        public ABigInteger mkBigInteger(final MyBigInteger i) {
            return new ABigInteger(i);
        }

        public AString mkString(final MyString s) {
            return new AString(s);
        }

        public ARangeFull mkRangeFull() {
            return new ARangeFull();
        }

        public AIdentity mkIdentity() {
            return new AIdentity();
        }

        public AConstructor mkConstructor(final String name, final int arity) {
            return new AConstructor(name, arity);
        }

        public Producer mkImport(final String name) {
            return imports.computeIfAbsent(name, _ -> new Producer(null));
        }

        private static List<Consumer> consumers(final Agent agent) {
            return switch (agent) {
                case ARoot root -> List.of(root.a);
                case AStrictOp1 op1 -> List.of(op1.a);
                case AStrictOp2 op2 -> List.of(op2.a, op2.c);
                case AIfThenElse ite -> List.of(ite.a, ite.c, ite.d);
                case AExpansion exp -> List.copyOf(exp.imports.values());
                case ANot not -> List.of(not.a);
                case AAnd and -> List.of(and.a, and.c);
                case AOr or -> List.of(or.a, or.c);
                case ADoRange doRng -> List.of(doRng.a, doRng.c);
                case ADoRangeFrom doRng -> List.of(doRng.a);
                case ADoRangeTo doRng -> List.of(doRng.a);
                case AApplicator app -> List.of(app.a, app.c);
                case AStrictApplicator sapp -> List.of(sapp.a, sapp.c);
                case AResolver res -> List.of(res.a, res.d);
                case ACapture cap -> List.of(cap.a, cap.d);
                case AMatch match -> {
                    final var result = new ArrayList<Consumer>();
                    result.add(match.a);
                    result.addAll(List.of(match.handlers));
                    yield result;
                }
                case AConstructorResolver res -> {
                    final var result = new ArrayList<Consumer>();
                    result.add(res.a);
                    result.addAll(List.of(res.arguments));
                    yield result;
                }
                case ASelect sel -> List.of(sel.a);
                case ADuplicator dup -> List.of(dup.a);
                case ALambda lam -> List.of(lam.c);
                case AConstructor ctr -> List.of(ctr.arguments);
                case AReference _,AEndOfList _,ANull _,ATrue _,AFalse _,AInteger _,ABigInteger _,AString _,ARangeFull _,AIdentity _ ->
                    List.of();
            };
        }

        private static List<Producer> producers(final Agent agent) {
            return switch (agent) {
                case ARoot _ -> List.of();
                case AReference ref -> List.of(ref.a);
                case AStrictOp1 op1 -> List.of(op1.b);
                case AStrictOp2 op2 -> List.of(op2.b);
                case AIfThenElse ite -> List.of(ite.b);
                case AExpansion exp -> List.of(exp.a);
                case ANot not -> List.of(not.b);
                case AAnd and -> List.of(and.b);
                case AOr or -> List.of(or.b);
                case ADoRange doRng -> List.of(doRng.b);
                case ADoRangeFrom doRng -> List.of(doRng.b);
                case ADoRangeTo doRng -> List.of(doRng.b);
                case AApplicator app -> List.of(app.b);
                case AStrictApplicator sapp -> List.of(sapp.b);
                case AResolver res -> List.of(res.b, res.c);
                case ACapture cap -> List.of(cap.b, cap.c);
                case AMatch match -> List.of(match.b);
                case AConstructorResolver res -> List.of(res.b);
                case ASelect sel -> List.of(sel.b);
                case ADuplicator dup -> List.of(dup.b, dup.c);
                case ALambda lam -> List.of(lam.a, lam.b);
                case AEndOfList end -> List.of(end.a);
                case ANull myNull -> List.of(myNull.a);
                case ATrue b -> List.of(b.a);
                case AFalse b -> List.of(b.a);
                case AInteger i -> List.of(i.a);
                case ABigInteger i -> List.of(i.a);
                case AString s -> List.of(s.a);
                case ARangeFull rng -> List.of(rng.a);
                case AIdentity id -> List.of(id.a);
                case AConstructor ctr -> List.of(ctr.a);
            };
        }

        private static Payload payload(final Agent agent) {
            return switch (agent) {
                case ARoot _ -> new PRoot();
                case AReference ref -> new PReference(ref.name);
                case AStrictOp1 op1 -> new PStrictOp1(op1.op);
                case AStrictOp2 op2 -> new PStrictOp2(op2.op);
                case AIfThenElse _ -> new PIfThenElse();
                case AExpansion exp -> new PExpansion(exp.inner.build());
                case ANot _ -> new PNot();
                case AAnd _ -> new PAnd();
                case AOr _ -> new POr();
                case ADoRange doRng -> new PDoRange(doRng.inclusive);
                case ADoRangeFrom _ -> new PDoRangeFrom();
                case ADoRangeTo doRng -> new PDoRangeTo(doRng.inclusive);
                case AApplicator _ -> new PApplicator();
                case AStrictApplicator _ -> new PStrictApplicator();
                case AResolver _ -> new PResolver();
                case ACapture _ -> new PCapture();
                case AMatch match -> new PMatch(match.names);
                case AConstructorResolver res ->
                    new PConstructorResolver(res.name, res.arguments.length);
                case ASelect sel -> new PSelect(sel.name, sel.index);
                case ADuplicator _ -> new PDuplicator();
                case ALambda _ -> new PLambda();
                case AEndOfList _ -> new PEndOfList();
                case ANull _ -> new PNull();
                case ATrue _ -> new PTrue();
                case AFalse _ -> new PFalse();
                case AInteger i -> new PInteger(i.value);
                case ABigInteger i -> new PBigInteger(i.value);
                case AString s -> new PString(s.value);
                case ARangeFull _ -> new PRangeFull();
                case AIdentity _ -> new PIdentity();
                case AConstructor ctr -> new PConstructor(ctr.name, ctr.arguments.length);
            };
        }

        private static final class Optimizer {
            private static final boolean COLLAPSE_CAPTURES = Boolean
                    .parseBoolean(System.getProperty("motor.collapseCaptures", "true"));
            private static final boolean RESOLVE_CAPTURES = Boolean
                    .parseBoolean(System.getProperty("motor.resolveCaptures", "true"));
            private static final boolean RESOLVE_LAMBDAS = Boolean
                    .parseBoolean(System.getProperty("motor.resolveLambdas", "true"));
            private static final boolean RESOLVE_CONSTRUCTORS = Boolean
                    .parseBoolean(System.getProperty("motor.resolveConstructors", "true"));
            private static final boolean DUPLICATE_ATOMS = Boolean
                    .parseBoolean(System.getProperty("motor.duplicateAtoms", "true"));
            private static final boolean BETA_REDUCE = Boolean
                    .parseBoolean(System.getProperty("motor.betaReduce", "true"));

            // Set whenever a rewrite fires during a pass.
            private boolean proceed;

            public Optimizer() {
                this.proceed = false;
            }

            public void optimize(final Consumer root) {
                do {
                    proceed = false;
                    if (COLLAPSE_CAPTURES) {
                        collapseCaptures(root, new HashSet<>());
                    }
                    if (RESOLVE_CAPTURES) {
                        resolveCaptures(root, new HashSet<>());
                    }
                    if (RESOLVE_LAMBDAS) {
                        resolveLambdas(root, new HashSet<>());
                    }
                    if (RESOLVE_CONSTRUCTORS) {
                        resolveConstructors(root, new HashSet<>());
                    }
                    if (DUPLICATE_ATOMS) {
                        duplicateAtoms(root, new HashSet<>());
                    }
                    if (BETA_REDUCE) {
                        betaReduce(root, new HashSet<>());
                    }
                } while (proceed);
            }

            // Collapses capture chains: when the upper capture points to the lower capture's port
            // `c`, safely remove the former.
            private void collapseCaptures(final Consumer p, final Set<Agent> visitedSet) {
                final Agent agent = p.chase();
                if (agent == null || !visitedSet.add(agent)) {
                    return;
                }
                switch (agent) {
                    case AStrictOp1 op1 -> {
                        collapseCaptures(op1.a, visitedSet);
                    }
                    case AStrictOp2 op2 -> {
                        collapseCaptures(op2.a, visitedSet);
                        collapseCaptures(op2.c, visitedSet);
                    }
                    case AIfThenElse ite -> {
                        collapseCaptures(ite.a, visitedSet);
                        collapseCaptures(ite.c, visitedSet);
                        collapseCaptures(ite.d, visitedSet);
                    }
                    case AExpansion exp -> {
                        for (final Consumer imported : exp.imports.values()) {
                            collapseCaptures(imported, visitedSet);
                        }
                    }
                    case ANot not -> {
                        collapseCaptures(not.a, visitedSet);
                    }
                    case AAnd and -> {
                        collapseCaptures(and.a, visitedSet);
                        collapseCaptures(and.c, visitedSet);
                    }
                    case AOr or -> {
                        collapseCaptures(or.a, visitedSet);
                        collapseCaptures(or.c, visitedSet);
                    }
                    case ADoRange doRng -> {
                        collapseCaptures(doRng.a, visitedSet);
                        collapseCaptures(doRng.c, visitedSet);
                    }
                    case ADoRangeFrom doRng -> {
                        collapseCaptures(doRng.a, visitedSet);
                    }
                    case ADoRangeTo doRng -> {
                        collapseCaptures(doRng.a, visitedSet);
                    }
                    case AApplicator app -> {
                        collapseCaptures(app.a, visitedSet);
                        collapseCaptures(app.c, visitedSet);
                    }
                    case AStrictApplicator sapp -> {
                        collapseCaptures(sapp.a, visitedSet);
                        collapseCaptures(sapp.c, visitedSet);
                    }
                    case AResolver res -> {
                        collapseCaptures(res.a, visitedSet);
                        collapseCaptures(res.d, visitedSet);
                    }
                    case ACapture cap -> {
                        collapseCaptures(cap.a, visitedSet);
                        collapseCaptures(cap.d, visitedSet);
                        if (cap.a.chase() instanceof ACapture other
                                && cap.a.producer() == other.c) {
                            cap.c.forward(cap.a.producer());
                            cap.b.forward(cap.d.producer());
                            proceed = true;
                        }
                    }
                    case AMatch match -> {
                        collapseCaptures(match.a, visitedSet);
                        for (final Consumer handler : match.handlers) {
                            collapseCaptures(handler, visitedSet);
                        }
                    }
                    case AConstructorResolver res -> {
                        collapseCaptures(res.a, visitedSet);
                        for (final Consumer argument : res.arguments) {
                            collapseCaptures(argument, visitedSet);
                        }
                    }
                    case ASelect sel -> {
                        collapseCaptures(sel.a, visitedSet);
                    }
                    case ADuplicator dup -> {
                        collapseCaptures(dup.a, visitedSet);
                    }
                    case ALambda lam -> {
                        collapseCaptures(lam.c, visitedSet);
                    }
                    case AConstructor ctr -> {
                        for (final Consumer argument : ctr.arguments) {
                            collapseCaptures(argument, visitedSet);
                        }
                    }
                    case ARoot _,AReference _,AEndOfList _,ANull _,ATrue _,AFalse _,AInteger _,ABigInteger _,AString _,ARangeFull _,AIdentity _ ->
                        {
                        }
                }
            }

            // Interact captures with WHNF data; see the cases of `Motor.ACapture.interact`.
            private void resolveCaptures(final Consumer p, final Set<Agent> visitedSet) {
                final Agent agent = p.chase();
                if (agent == null || !visitedSet.add(agent)) {
                    return;
                }
                switch (agent) {
                    case AStrictOp1 op1 -> {
                        resolveCaptures(op1.a, visitedSet);
                    }
                    case AStrictOp2 op2 -> {
                        resolveCaptures(op2.a, visitedSet);
                        resolveCaptures(op2.c, visitedSet);
                    }
                    case AIfThenElse ite -> {
                        resolveCaptures(ite.a, visitedSet);
                        resolveCaptures(ite.c, visitedSet);
                        resolveCaptures(ite.d, visitedSet);
                    }
                    case AExpansion exp -> {
                        for (final Consumer imported : exp.imports.values()) {
                            resolveCaptures(imported, visitedSet);
                        }
                    }
                    case ANot not -> {
                        resolveCaptures(not.a, visitedSet);
                    }
                    case AAnd and -> {
                        resolveCaptures(and.a, visitedSet);
                        resolveCaptures(and.c, visitedSet);
                    }
                    case AOr or -> {
                        resolveCaptures(or.a, visitedSet);
                        resolveCaptures(or.c, visitedSet);
                    }
                    case ADoRange doRng -> {
                        resolveCaptures(doRng.a, visitedSet);
                        resolveCaptures(doRng.c, visitedSet);
                    }
                    case ADoRangeFrom doRng -> {
                        resolveCaptures(doRng.a, visitedSet);
                    }
                    case ADoRangeTo doRng -> {
                        resolveCaptures(doRng.a, visitedSet);
                    }
                    case AApplicator app -> {
                        resolveCaptures(app.a, visitedSet);
                        resolveCaptures(app.c, visitedSet);
                    }
                    case AStrictApplicator sapp -> {
                        resolveCaptures(sapp.a, visitedSet);
                        resolveCaptures(sapp.c, visitedSet);
                    }
                    case AResolver res -> {
                        resolveCaptures(res.a, visitedSet);
                        resolveCaptures(res.d, visitedSet);
                    }
                    case ACapture cap -> {
                        resolveCaptures(cap.a, visitedSet);
                        resolveCaptures(cap.d, visitedSet);
                        final boolean whnf = switch (cap.a.chase()) {
                            case ALambda lam when cap.a.producer() == lam.a -> true;
                            case ANull _ -> true;
                            case ATrue _ -> true;
                            case AFalse _ -> true;
                            case AInteger _ -> true;
                            case ABigInteger _ -> true;
                            case AString _ -> true;
                            case ARangeFull _ -> true;
                            case AIdentity _ -> true;
                            case AConstructor _ -> true;
                            case ARoot _,AReference _,AStrictOp1 _,AStrictOp2 _,AIfThenElse _,AExpansion _,ANot _,AAnd _,AOr _,ADoRange _,ADoRangeFrom _,ADoRangeTo _,AApplicator _,AStrictApplicator _,AResolver _,ACapture _,AMatch _,AConstructorResolver _,ASelect _,ADuplicator _,ALambda _,AEndOfList _ ->
                                false;
                            case null -> false;
                        };
                        if (whnf) {
                            cap.c.forward(cap.a.producer());
                            cap.b.forward(cap.d.producer());
                            proceed = true;
                        }
                    }
                    case AMatch match -> {
                        resolveCaptures(match.a, visitedSet);
                        for (final Consumer handler : match.handlers) {
                            resolveCaptures(handler, visitedSet);
                        }
                    }
                    case AConstructorResolver res -> {
                        resolveCaptures(res.a, visitedSet);
                        for (final Consumer argument : res.arguments) {
                            resolveCaptures(argument, visitedSet);
                        }
                    }
                    case ASelect sel -> {
                        resolveCaptures(sel.a, visitedSet);
                    }
                    case ADuplicator dup -> {
                        resolveCaptures(dup.a, visitedSet);
                    }
                    case ALambda lam -> {
                        resolveCaptures(lam.c, visitedSet);
                    }
                    case AConstructor ctr -> {
                        for (final Consumer argument : ctr.arguments) {
                            resolveCaptures(argument, visitedSet);
                        }
                    }
                    case ARoot _,AReference _,AEndOfList _,ANull _,ATrue _,AFalse _,AInteger _,ABigInteger _,AString _,ARangeFull _,AIdentity _ ->
                        {
                        }
                }
            }

            // Interacts lambda resolvers with an `AEndOfList` agent, turning the former ones into
            // ready, capture-free lambdas.
            private void resolveLambdas(final Consumer p, final Set<Agent> visitedSet) {
                final Agent agent = p.chase();
                if (agent == null || !visitedSet.add(agent)) {
                    return;
                }
                switch (agent) {
                    case AStrictOp1 op1 -> {
                        resolveLambdas(op1.a, visitedSet);
                    }
                    case AStrictOp2 op2 -> {
                        resolveLambdas(op2.a, visitedSet);
                        resolveLambdas(op2.c, visitedSet);
                    }
                    case AIfThenElse ite -> {
                        resolveLambdas(ite.a, visitedSet);
                        resolveLambdas(ite.c, visitedSet);
                        resolveLambdas(ite.d, visitedSet);
                    }
                    case AExpansion exp -> {
                        for (final Consumer imported : exp.imports.values()) {
                            resolveLambdas(imported, visitedSet);
                        }
                    }
                    case ANot not -> {
                        resolveLambdas(not.a, visitedSet);
                    }
                    case AAnd and -> {
                        resolveLambdas(and.a, visitedSet);
                        resolveLambdas(and.c, visitedSet);
                    }
                    case AOr or -> {
                        resolveLambdas(or.a, visitedSet);
                        resolveLambdas(or.c, visitedSet);
                    }
                    case ADoRange doRng -> {
                        resolveLambdas(doRng.a, visitedSet);
                        resolveLambdas(doRng.c, visitedSet);
                    }
                    case ADoRangeFrom doRng -> {
                        resolveLambdas(doRng.a, visitedSet);
                    }
                    case ADoRangeTo doRng -> {
                        resolveLambdas(doRng.a, visitedSet);
                    }
                    case AApplicator app -> {
                        resolveLambdas(app.a, visitedSet);
                        resolveLambdas(app.c, visitedSet);
                    }
                    case AStrictApplicator sapp -> {
                        resolveLambdas(sapp.a, visitedSet);
                        resolveLambdas(sapp.c, visitedSet);
                    }
                    case AResolver res -> {
                        resolveLambdas(res.a, visitedSet);
                        resolveLambdas(res.d, visitedSet);
                        if (res.a.chase() instanceof AEndOfList) {
                            final var lam = new ALambda();
                            res.b.forward(lam.a);
                            res.c.forward(lam.b);
                            lam.c.setProducer(res.d.producer());
                            proceed = true;
                        }
                    }
                    case ACapture cap -> {
                        resolveLambdas(cap.a, visitedSet);
                        resolveLambdas(cap.d, visitedSet);
                    }
                    case AMatch match -> {
                        resolveLambdas(match.a, visitedSet);
                        for (final Consumer handler : match.handlers) {
                            resolveLambdas(handler, visitedSet);
                        }
                    }
                    case AConstructorResolver res -> {
                        resolveLambdas(res.a, visitedSet);
                        for (final Consumer argument : res.arguments) {
                            resolveLambdas(argument, visitedSet);
                        }
                    }
                    case ASelect sel -> {
                        resolveLambdas(sel.a, visitedSet);
                    }
                    case ADuplicator dup -> {
                        resolveLambdas(dup.a, visitedSet);
                    }
                    case ALambda lam -> {
                        resolveLambdas(lam.c, visitedSet);
                    }
                    case AConstructor ctr -> {
                        for (final Consumer argument : ctr.arguments) {
                            resolveLambdas(argument, visitedSet);
                        }
                    }
                    case ARoot _,AReference _,AEndOfList _,ANull _,ATrue _,AFalse _,AInteger _,ABigInteger _,AString _,ARangeFull _,AIdentity _ ->
                        {
                        }
                }
            }

            // Interacts constructor resolvers with an `AEndOfList` agent, turning the former ones
            // into ready, capture-free constructors.
            private void resolveConstructors(final Consumer p, final Set<Agent> visitedSet) {
                final Agent agent = p.chase();
                if (agent == null || !visitedSet.add(agent)) {
                    return;
                }
                switch (agent) {
                    case AStrictOp1 op1 -> {
                        resolveConstructors(op1.a, visitedSet);
                    }
                    case AStrictOp2 op2 -> {
                        resolveConstructors(op2.a, visitedSet);
                        resolveConstructors(op2.c, visitedSet);
                    }
                    case AIfThenElse ite -> {
                        resolveConstructors(ite.a, visitedSet);
                        resolveConstructors(ite.c, visitedSet);
                        resolveConstructors(ite.d, visitedSet);
                    }
                    case AExpansion exp -> {
                        for (final Consumer imported : exp.imports.values()) {
                            resolveConstructors(imported, visitedSet);
                        }
                    }
                    case ANot not -> {
                        resolveConstructors(not.a, visitedSet);
                    }
                    case AAnd and -> {
                        resolveConstructors(and.a, visitedSet);
                        resolveConstructors(and.c, visitedSet);
                    }
                    case AOr or -> {
                        resolveConstructors(or.a, visitedSet);
                        resolveConstructors(or.c, visitedSet);
                    }
                    case ADoRange doRng -> {
                        resolveConstructors(doRng.a, visitedSet);
                        resolveConstructors(doRng.c, visitedSet);
                    }
                    case ADoRangeFrom doRng -> {
                        resolveConstructors(doRng.a, visitedSet);
                    }
                    case ADoRangeTo doRng -> {
                        resolveConstructors(doRng.a, visitedSet);
                    }
                    case AApplicator app -> {
                        resolveConstructors(app.a, visitedSet);
                        resolveConstructors(app.c, visitedSet);
                    }
                    case AStrictApplicator sapp -> {
                        resolveConstructors(sapp.a, visitedSet);
                        resolveConstructors(sapp.c, visitedSet);
                    }
                    case AResolver res -> {
                        resolveConstructors(res.a, visitedSet);
                        resolveConstructors(res.d, visitedSet);
                    }
                    case ACapture cap -> {
                        resolveConstructors(cap.a, visitedSet);
                        resolveConstructors(cap.d, visitedSet);
                    }
                    case AMatch match -> {
                        resolveConstructors(match.a, visitedSet);
                        for (final Consumer handler : match.handlers) {
                            resolveConstructors(handler, visitedSet);
                        }
                    }
                    case AConstructorResolver res -> {
                        resolveConstructors(res.a, visitedSet);
                        for (final Consumer argument : res.arguments) {
                            resolveConstructors(argument, visitedSet);
                        }
                        if (res.a.chase() instanceof AEndOfList) {
                            final var ctr = new AConstructor(res.name, res.arguments.length);
                            for (int i = 0; i < res.arguments.length; i++) {
                                ctr.arguments[i].setProducer(res.arguments[i].producer());
                            }
                            res.b.forward(ctr.a);
                            proceed = true;
                        }
                    }
                    case ASelect sel -> {
                        resolveConstructors(sel.a, visitedSet);
                    }
                    case ADuplicator dup -> {
                        resolveConstructors(dup.a, visitedSet);
                    }
                    case ALambda lam -> {
                        resolveConstructors(lam.c, visitedSet);
                    }
                    case AConstructor ctr -> {
                        for (final Consumer argument : ctr.arguments) {
                            resolveConstructors(argument, visitedSet);
                        }
                    }
                    case ARoot _,AReference _,AEndOfList _,ANull _,ATrue _,AFalse _,AInteger _,ABigInteger _,AString _,ARangeFull _,AIdentity _ ->
                        {
                        }
                }
            }

            // Duplicates atomic values, including nullary constructors. The rationale is to reduce
            // the number of duplicators in the template.
            private void duplicateAtoms(final Consumer p, final Set<Agent> visitedSet) {
                final Agent agent = p.chase();
                if (agent == null || !visitedSet.add(agent)) {
                    return;
                }
                switch (agent) {
                    case AStrictOp1 op1 -> {
                        duplicateAtoms(op1.a, visitedSet);
                    }
                    case AStrictOp2 op2 -> {
                        duplicateAtoms(op2.a, visitedSet);
                        duplicateAtoms(op2.c, visitedSet);
                    }
                    case AIfThenElse ite -> {
                        duplicateAtoms(ite.a, visitedSet);
                        duplicateAtoms(ite.c, visitedSet);
                        duplicateAtoms(ite.d, visitedSet);
                    }
                    case AExpansion exp -> {
                        for (final Consumer imported : exp.imports.values()) {
                            duplicateAtoms(imported, visitedSet);
                        }
                    }
                    case ANot not -> {
                        duplicateAtoms(not.a, visitedSet);
                    }
                    case AAnd and -> {
                        duplicateAtoms(and.a, visitedSet);
                        duplicateAtoms(and.c, visitedSet);
                    }
                    case AOr or -> {
                        duplicateAtoms(or.a, visitedSet);
                        duplicateAtoms(or.c, visitedSet);
                    }
                    case ADoRange doRng -> {
                        duplicateAtoms(doRng.a, visitedSet);
                        duplicateAtoms(doRng.c, visitedSet);
                    }
                    case ADoRangeFrom doRng -> {
                        duplicateAtoms(doRng.a, visitedSet);
                    }
                    case ADoRangeTo doRng -> {
                        duplicateAtoms(doRng.a, visitedSet);
                    }
                    case AApplicator app -> {
                        duplicateAtoms(app.a, visitedSet);
                        duplicateAtoms(app.c, visitedSet);
                    }
                    case AStrictApplicator sapp -> {
                        duplicateAtoms(sapp.a, visitedSet);
                        duplicateAtoms(sapp.c, visitedSet);
                    }
                    case AResolver res -> {
                        duplicateAtoms(res.a, visitedSet);
                        duplicateAtoms(res.d, visitedSet);
                    }
                    case ACapture cap -> {
                        duplicateAtoms(cap.a, visitedSet);
                        duplicateAtoms(cap.d, visitedSet);
                    }
                    case AMatch match -> {
                        duplicateAtoms(match.a, visitedSet);
                        for (final Consumer handler : match.handlers) {
                            duplicateAtoms(handler, visitedSet);
                        }
                    }
                    case AConstructorResolver res -> {
                        duplicateAtoms(res.a, visitedSet);
                        for (final Consumer argument : res.arguments) {
                            duplicateAtoms(argument, visitedSet);
                        }
                    }
                    case ASelect sel -> {
                        duplicateAtoms(sel.a, visitedSet);
                    }
                    case ADuplicator dup -> {
                        duplicateAtoms(dup.a, visitedSet);
                        final Producer copy = switch (dup.a.chase()) {
                            case AEndOfList _ -> new AEndOfList().a;
                            case ANull _ -> new ANull().a;
                            case ATrue _ -> new ATrue().a;
                            case AFalse _ -> new AFalse().a;
                            case AInteger i -> new AInteger(i.value).a;
                            case ABigInteger i -> new ABigInteger(i.value).a;
                            case AString s -> new AString(s.value).a;
                            case ARangeFull _ -> new ARangeFull().a;
                            case AIdentity _ -> new AIdentity().a;
                            case AConstructor ctr when ctr.isNullary() ->
                                new AConstructor(ctr.name, 0).a;
                            case ARoot _,AReference _,AStrictOp1 _,AStrictOp2 _,AIfThenElse _,AExpansion _,ANot _,AAnd _,AOr _,ADoRange _,ADoRangeFrom _,ADoRangeTo _,AApplicator _,AStrictApplicator _,AResolver _,ACapture _,AMatch _,AConstructorResolver _,ASelect _,ADuplicator _,ALambda _,AConstructor _ ->
                                null;
                            case null -> null;
                        };
                        if (copy != null) {
                            dup.b.forward(dup.a.producer());
                            dup.c.forward(copy);
                            proceed = true;
                        }
                    }
                    case ALambda lam -> {
                        duplicateAtoms(lam.c, visitedSet);
                    }
                    case AConstructor ctr -> {
                        for (final Consumer argument : ctr.arguments) {
                            duplicateAtoms(argument, visitedSet);
                        }
                    }
                    case ARoot _,AReference _,AEndOfList _,ANull _,ATrue _,AFalse _,AInteger _,ABigInteger _,AString _,ARangeFull _,AIdentity _ ->
                        {
                        }
                }
            }

            // Static beta reductions. Onely reduce non-strict applications & strict applications
            // _inferred_ by the analyzer, because reducing user-specified strict applications could
            // remove diverging arguments.
            private void betaReduce(final Consumer p, final Set<Agent> visitedSet) {
                final Agent agent = p.chase();
                if (agent == null || !visitedSet.add(agent)) {
                    return;
                }
                switch (agent) {
                    case AStrictOp1 op1 -> {
                        betaReduce(op1.a, visitedSet);
                    }
                    case AStrictOp2 op2 -> {
                        betaReduce(op2.a, visitedSet);
                        betaReduce(op2.c, visitedSet);
                    }
                    case AIfThenElse ite -> {
                        betaReduce(ite.a, visitedSet);
                        betaReduce(ite.c, visitedSet);
                        betaReduce(ite.d, visitedSet);
                    }
                    case AExpansion exp -> {
                        for (final Consumer imported : exp.imports.values()) {
                            betaReduce(imported, visitedSet);
                        }
                    }
                    case ANot not -> {
                        betaReduce(not.a, visitedSet);
                    }
                    case AAnd and -> {
                        betaReduce(and.a, visitedSet);
                        betaReduce(and.c, visitedSet);
                    }
                    case AOr or -> {
                        betaReduce(or.a, visitedSet);
                        betaReduce(or.c, visitedSet);
                    }
                    case ADoRange doRng -> {
                        betaReduce(doRng.a, visitedSet);
                        betaReduce(doRng.c, visitedSet);
                    }
                    case ADoRangeFrom doRng -> {
                        betaReduce(doRng.a, visitedSet);
                    }
                    case ADoRangeTo doRng -> {
                        betaReduce(doRng.a, visitedSet);
                    }
                    case AApplicator app -> {
                        betaReduce(app.a, visitedSet);
                        betaReduce(app.c, visitedSet);
                        beta(app.a, app.b, app.c);
                    }
                    case AStrictApplicator sapp -> {
                        betaReduce(sapp.a, visitedSet);
                        betaReduce(sapp.c, visitedSet);
                        switch (sapp.source) {
                            case USER_SPECIFIED -> {
                            }
                            case INFERRED -> beta(sapp.a, sapp.b, sapp.c);
                        }
                    }
                    case AResolver res -> {
                        betaReduce(res.a, visitedSet);
                        betaReduce(res.d, visitedSet);
                    }
                    case ACapture cap -> {
                        betaReduce(cap.a, visitedSet);
                        betaReduce(cap.d, visitedSet);
                    }
                    case AMatch match -> {
                        betaReduce(match.a, visitedSet);
                        for (final Consumer handler : match.handlers) {
                            betaReduce(handler, visitedSet);
                        }
                    }
                    case AConstructorResolver res -> {
                        betaReduce(res.a, visitedSet);
                        for (final Consumer argument : res.arguments) {
                            betaReduce(argument, visitedSet);
                        }
                    }
                    case ASelect sel -> {
                        betaReduce(sel.a, visitedSet);
                    }
                    case ADuplicator dup -> {
                        betaReduce(dup.a, visitedSet);
                    }
                    case ALambda lam -> {
                        betaReduce(lam.c, visitedSet);
                    }
                    case AConstructor ctr -> {
                        for (final Consumer argument : ctr.arguments) {
                            betaReduce(argument, visitedSet);
                        }
                    }
                    case ARoot _,AReference _,AEndOfList _,ANull _,ATrue _,AFalse _,AInteger _,ABigInteger _,AString _,ARangeFull _,AIdentity _ ->
                        {
                        }
                }
            }

            private void beta(
                    final Consumer function,
                    final Producer output,
                    final Consumer argument) {
                if (!(function.chase() instanceof Callable data)) {
                    return;
                }
                switch (data) {
                    case ALambda lam when function.producer() == lam.a -> {
                        output.forward(lam.c.producer());
                        lam.b.forward(argument.producer());
                        proceed = true;
                    }
                    case ALambda _ -> {
                    }
                    case AIdentity _ -> {
                        output.forward(argument.producer());
                        proceed = true;
                    }
                }
            }
        }

        public Template build() {
            new Optimizer().optimize(root.a);
            final var payloads = new ArrayList<Payload>();
            final var consumerIndex = new IdentityHashMap<Consumer, Integer>();
            final var producerIndex = new IdentityHashMap<Producer, Integer>();
            final var visitedSet = new LinkedHashSet<Agent>();
            final var pending = new ArrayDeque<Agent>();
            int i = 0, j = 0;
            visitedSet.add(root);
            pending.add(root);
            while (!pending.isEmpty()) {
                final Agent agent = pending.poll();
                payloads.add(payload(agent));
                for (final Consumer consumer : consumers(agent)) {
                    consumerIndex.put(consumer, i++);
                    final Agent owner = consumer.chase();
                    if (owner != null && visitedSet.add(owner)) {
                        pending.add(owner);
                    }
                }
                for (final Producer producer : producers(agent)) {
                    producerIndex.put(producer, j++);
                }
            }
            for (final Producer producer : imports.values()) {
                producerIndex.put(producer, j++);
            }
            assert i == consumerIndex.size();
            assert j == producerIndex.size();
            final var links = new int[consumerIndex.size()];
            for (final var entry : consumerIndex.entrySet()) {
                final Consumer consumer = entry.getKey();
                final int index = entry.getValue();
                // Resolve the producer forwardings introduced by the optimizations, so that the
                // resulting template is clean.
                consumer.chase();
                links[index] = producerIndex.get(consumer.producer);
            }
            return new Template(
                    payloads.toArray(Payload[]::new),
                    links,
                    producerIndex.size() - imports.size(),
                    imports.size());
        }
    }
}
