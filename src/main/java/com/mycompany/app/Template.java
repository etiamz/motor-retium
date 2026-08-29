package com.mycompany.app;

import com.mycompany.app.Primitives.StrictOp1;
import com.mycompany.app.Primitives.StrictOp2;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedMap;

public final class Template {
    private sealed interface Payload permits
            // The interface.
            PRoot,
            // Operators.
            PReference, PStrictOp1, PStrictOp2, PIfThenElse, PExpansion, PNot, PAnd, POr, PDoRange,
            PDoRangeFrom, PDoRangeTo, PApplicator, PStrictApplicator, PResolver, PCapture, PMatch,
            PConstructorResolver, PDuplicator,
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
                ACapture, AMatch, AConstructorResolver, ADuplicator,
                // Data.
                ALambda, AEndOfList, ANull, ATrue, AFalse, AInteger, ABigInteger, AString,
                ARangeFull, AIdentity, AConstructor {
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
            private final Consumer a;
            private final Producer b;
            private final Consumer c;

            private AStrictApplicator() {
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

        public static final class ALambda implements Agent {
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

        public static final class AIdentity implements Agent {
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

        public AStrictApplicator mkStrictApplicator() {
            return new AStrictApplicator();
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
                case ARoot a -> List.of(a.a);
                case AStrictOp1 a -> List.of(a.a);
                case AStrictOp2 a -> List.of(a.a, a.c);
                case AIfThenElse a -> List.of(a.a, a.c, a.d);
                case AExpansion a -> List.copyOf(a.imports.values());
                case ANot a -> List.of(a.a);
                case AAnd a -> List.of(a.a, a.c);
                case AOr a -> List.of(a.a, a.c);
                case ADoRange a -> List.of(a.a, a.c);
                case ADoRangeFrom a -> List.of(a.a);
                case ADoRangeTo a -> List.of(a.a);
                case AApplicator a -> List.of(a.a, a.c);
                case AStrictApplicator a -> List.of(a.a, a.c);
                case AResolver a -> List.of(a.a, a.d);
                case ACapture a -> List.of(a.a, a.d);
                case AMatch a -> {
                    final var result = new ArrayList<Consumer>();
                    result.add(a.a);
                    result.addAll(List.of(a.handlers));
                    yield result;
                }
                case AConstructorResolver a -> {
                    final var result = new ArrayList<Consumer>();
                    result.add(a.a);
                    result.addAll(List.of(a.arguments));
                    yield result;
                }
                case ADuplicator a -> List.of(a.a);
                case ALambda a -> List.of(a.c);
                case AConstructor a -> List.of(a.arguments);
                case AReference _,AEndOfList _,ANull _,ATrue _,AFalse _,AInteger _,ABigInteger _,AString _,ARangeFull _,AIdentity _ ->
                    List.of();
            };
        }

        private static List<Producer> producers(final Agent agent) {
            return switch (agent) {
                case ARoot _ -> List.of();
                case AReference a -> List.of(a.a);
                case AStrictOp1 a -> List.of(a.b);
                case AStrictOp2 a -> List.of(a.b);
                case AIfThenElse a -> List.of(a.b);
                case AExpansion a -> List.of(a.a);
                case ANot a -> List.of(a.b);
                case AAnd a -> List.of(a.b);
                case AOr a -> List.of(a.b);
                case ADoRange a -> List.of(a.b);
                case ADoRangeFrom a -> List.of(a.b);
                case ADoRangeTo a -> List.of(a.b);
                case AApplicator a -> List.of(a.b);
                case AStrictApplicator a -> List.of(a.b);
                case AResolver a -> List.of(a.b, a.c);
                case ACapture a -> List.of(a.b, a.c);
                case AMatch a -> List.of(a.b);
                case AConstructorResolver a -> List.of(a.b);
                case ADuplicator a -> List.of(a.b, a.c);
                case ALambda a -> List.of(a.a, a.b);
                case AEndOfList a -> List.of(a.a);
                case ANull a -> List.of(a.a);
                case ATrue a -> List.of(a.a);
                case AFalse a -> List.of(a.a);
                case AInteger a -> List.of(a.a);
                case ABigInteger a -> List.of(a.a);
                case AString a -> List.of(a.a);
                case ARangeFull a -> List.of(a.a);
                case AIdentity a -> List.of(a.a);
                case AConstructor a -> List.of(a.a);
            };
        }

        private static Payload payload(final Agent agent) {
            return switch (agent) {
                case ARoot _ -> new PRoot();
                case AReference a -> new PReference(a.name);
                case AStrictOp1 a -> new PStrictOp1(a.op);
                case AStrictOp2 a -> new PStrictOp2(a.op);
                case AIfThenElse _ -> new PIfThenElse();
                case AExpansion a -> new PExpansion(a.inner.build());
                case ANot _ -> new PNot();
                case AAnd _ -> new PAnd();
                case AOr _ -> new POr();
                case ADoRange a -> new PDoRange(a.inclusive);
                case ADoRangeFrom _ -> new PDoRangeFrom();
                case ADoRangeTo a -> new PDoRangeTo(a.inclusive);
                case AApplicator _ -> new PApplicator();
                case AStrictApplicator _ -> new PStrictApplicator();
                case AResolver _ -> new PResolver();
                case ACapture _ -> new PCapture();
                case AMatch a -> new PMatch(a.names);
                case AConstructorResolver a -> new PConstructorResolver(a.name, a.arguments.length);
                case ADuplicator _ -> new PDuplicator();
                case ALambda _ -> new PLambda();
                case AEndOfList _ -> new PEndOfList();
                case ANull _ -> new PNull();
                case ATrue _ -> new PTrue();
                case AFalse _ -> new PFalse();
                case AInteger a -> new PInteger(a.value);
                case ABigInteger a -> new PBigInteger(a.value);
                case AString a -> new PString(a.value);
                case ARangeFull _ -> new PRangeFull();
                case AIdentity _ -> new PIdentity();
                case AConstructor a -> new PConstructor(a.name, a.arguments.length);
            };
        }

        public Template build() {
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
