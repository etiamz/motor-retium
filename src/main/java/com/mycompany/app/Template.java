package com.mycompany.app;

import com.mycompany.app.Primitives.StrictOp1;
import com.mycompany.app.Primitives.StrictOp2;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class Template {
    private sealed interface Kind permits
            // The interface.
            KRoot,
            // Operators.
            KStrictOp1, KStrictOp2, KIfThenElse, KNot, KAnd, KOr, KDoRange, KDoRangeFrom,
            KDoRangeTo, KApplicator, KStrictApplicator, KResolver, KCapture, KFix, KMatch,
            KDuplicator,
            // Data.
            KTrue, KFalse, KInteger, KBigInteger, KString, KRangeFull, KIdentity, KReference,
            KEndOfList, KLambda, KConstructor {
    }

    private record KRoot() implements Kind {
    }

    private record KStrictOp1(StrictOp1 op) implements Kind {
    }

    private record KStrictOp2(StrictOp2 op) implements Kind {
    }

    private record KIfThenElse() implements Kind {
    }

    private record KNot() implements Kind {
    }

    private record KAnd() implements Kind {
    }

    private record KOr() implements Kind {
    }

    private record KDoRange() implements Kind {
    }

    private record KDoRangeFrom() implements Kind {
    }

    private record KDoRangeTo() implements Kind {
    }

    private record KRangeFull() implements Kind {
    }

    private record KApplicator() implements Kind {
    }

    private record KStrictApplicator() implements Kind {
    }

    private record KResolver() implements Kind {
    }

    private record KCapture() implements Kind {
    }

    private record KFix() implements Kind {
    }

    private record KMatch(String[] names) implements Kind {
    }

    private record KDuplicator() implements Kind {
    }

    private record KTrue() implements Kind {
    }

    private record KFalse() implements Kind {
    }

    private record KInteger(CheckedInteger.Value value) implements Kind {
    }

    private record KBigInteger(MyBigInteger value) implements Kind {
    }

    private record KString(MyString value) implements Kind {
    }

    private record KIdentity() implements Kind {
    }

    private record KReference(String name) implements Kind {
    }

    private record KEndOfList() implements Kind {
    }

    private record KLambda() implements Kind {
    }

    private record KConstructor(String name, int arity) implements Kind {
    }

    private record Link(int consumer, int producer) {
    }

    private final KStrictOp1[] op1Kinds;
    private final KStrictOp2[] op2Kinds;
    private final KIfThenElse[] iteKinds;
    private final KNot[] notKinds;
    private final KAnd[] andKinds;
    private final KOr[] orKinds;
    private final KDoRange[] doRngKinds;
    private final KDoRangeFrom[] doRngFromKinds;
    private final KDoRangeTo[] doRngToKinds;
    private final KApplicator[] appKinds;
    private final KStrictApplicator[] sappKinds;
    private final KResolver[] resKinds;
    private final KCapture[] capKinds;
    private final KFix[] fixKinds;
    private final KMatch[] matchKinds;
    private final KDuplicator[] dupKinds;
    private final KTrue[] bTrueKinds;
    private final KFalse[] bFalseKinds;
    private final KInteger[] iKinds;
    private final KBigInteger[] biKinds;
    private final KString[] sKinds;
    private final KRangeFull[] rngFullKinds;
    private final KIdentity[] idKinds;
    private final KReference[] refKinds;
    private final KEndOfList[] endKinds;
    private final KLambda[] lamKinds;
    private final KConstructor[] ctrKinds;
    private final Link[] links;
    private final int nconsumers, nproducers;

    private Template(
            final KStrictOp1[] op1Kinds,
            final KStrictOp2[] op2Kinds,
            final KIfThenElse[] iteKinds,
            final KNot[] notKinds,
            final KAnd[] andKinds,
            final KOr[] orKinds,
            final KDoRange[] doRngKinds,
            final KDoRangeFrom[] doRngFromKinds,
            final KDoRangeTo[] doRngToKinds,
            final KApplicator[] appKinds,
            final KStrictApplicator[] sappKinds,
            final KResolver[] resKinds,
            final KCapture[] capKinds,
            final KFix[] fixKinds,
            final KMatch[] matchKinds,
            final KDuplicator[] dupKinds,
            final KTrue[] bTrueKinds,
            final KFalse[] bFalseKinds,
            final KInteger[] iKinds,
            final KBigInteger[] biKinds,
            final KString[] sKinds,
            final KRangeFull[] rngFullKinds,
            final KIdentity[] idKinds,
            final KReference[] refKinds,
            final KEndOfList[] endKinds,
            final KLambda[] lamKinds,
            final KConstructor[] ctrKinds,
            final Link[] links,
            final int nconsumers,
            final int nproducers) {
        this.op1Kinds = op1Kinds;
        this.op2Kinds = op2Kinds;
        this.iteKinds = iteKinds;
        this.notKinds = notKinds;
        this.andKinds = andKinds;
        this.orKinds = orKinds;
        this.doRngKinds = doRngKinds;
        this.doRngFromKinds = doRngFromKinds;
        this.doRngToKinds = doRngToKinds;
        this.appKinds = appKinds;
        this.sappKinds = sappKinds;
        this.resKinds = resKinds;
        this.capKinds = capKinds;
        this.fixKinds = fixKinds;
        this.matchKinds = matchKinds;
        this.dupKinds = dupKinds;
        this.bTrueKinds = bTrueKinds;
        this.bFalseKinds = bFalseKinds;
        this.iKinds = iKinds;
        this.biKinds = biKinds;
        this.sKinds = sKinds;
        this.rngFullKinds = rngFullKinds;
        this.idKinds = idKinds;
        this.refKinds = refKinds;
        this.endKinds = endKinds;
        this.lamKinds = lamKinds;
        this.ctrKinds = ctrKinds;
        this.links = links;
        this.nconsumers = nconsumers;
        this.nproducers = nproducers;
    }

    public void materialize(final Port.Consumer consumer) {
        final Port.Consumer[] consumers = new Port.Consumer[nconsumers];
        final Port.Producer[] producers = new Port.Producer[nproducers];
        int i = 0, j = 0;
        consumers[i++] = consumer;
        for (final KStrictOp1 k : op1Kinds) {
            final var agent = new Motor.AStrictOp1(k.op);
            consumers[i++] = agent.a;
            producers[j++] = agent.b;
        }
        for (final KStrictOp2 k : op2Kinds) {
            final var agent = new Motor.AStrictOp2(k.op);
            consumers[i++] = agent.a;
            producers[j++] = agent.b;
            consumers[i++] = agent.c;
        }
        for (final KIfThenElse _ : iteKinds) {
            final var agent = new Motor.AIfThenElse();
            consumers[i++] = agent.a;
            producers[j++] = agent.b;
            consumers[i++] = agent.c;
            consumers[i++] = agent.d;
        }
        for (final KNot _ : notKinds) {
            final var agent = new Motor.ANot();
            consumers[i++] = agent.a;
            producers[j++] = agent.b;
        }
        for (final KAnd _ : andKinds) {
            final var agent = new Motor.AAnd();
            consumers[i++] = agent.a;
            producers[j++] = agent.b;
            consumers[i++] = agent.c;
        }
        for (final KOr _ : orKinds) {
            final var agent = new Motor.AOr();
            consumers[i++] = agent.a;
            producers[j++] = agent.b;
            consumers[i++] = agent.c;
        }
        for (final KDoRange _ : doRngKinds) {
            final var agent = new Motor.ADoRange();
            consumers[i++] = agent.a;
            producers[j++] = agent.b;
            consumers[i++] = agent.c;
        }
        for (final KDoRangeFrom _ : doRngFromKinds) {
            final var agent = new Motor.ADoRangeFrom();
            consumers[i++] = agent.a;
            producers[j++] = agent.b;
        }
        for (final KDoRangeTo _ : doRngToKinds) {
            final var agent = new Motor.ADoRangeTo();
            consumers[i++] = agent.a;
            producers[j++] = agent.b;
        }
        for (final KApplicator _ : appKinds) {
            final var agent = new Motor.AApplicator();
            consumers[i++] = agent.a;
            producers[j++] = agent.b;
            consumers[i++] = agent.c;
        }
        for (final KStrictApplicator _ : sappKinds) {
            final var agent = new Motor.AStrictApplicator();
            consumers[i++] = agent.a;
            producers[j++] = agent.b;
            consumers[i++] = agent.c;
        }
        for (final KResolver _ : resKinds) {
            final var agent = new Motor.AResolver();
            consumers[i++] = agent.a;
            producers[j++] = agent.b;
            producers[j++] = agent.c;
            consumers[i++] = agent.d;
        }
        for (final KCapture _ : capKinds) {
            final var agent = new Motor.ACapture();
            consumers[i++] = agent.a;
            producers[j++] = agent.b;
            producers[j++] = agent.c;
            consumers[i++] = agent.d;
        }
        for (final KFix _ : fixKinds) {
            final var agent = new Motor.AFix();
            consumers[i++] = agent.a;
            producers[j++] = agent.b;
        }
        for (final KMatch k : matchKinds) {
            final var agent = new Motor.AMatch(k.names);
            consumers[i++] = agent.a;
            producers[j++] = agent.b;
            for (final var port : agent.handlers) {
                consumers[i++] = port;
            }
        }
        for (final KDuplicator _ : dupKinds) {
            final var agent = new Motor.ADuplicator(Motor.Label.COPY);
            consumers[i++] = agent.a;
            producers[j++] = agent.b;
            producers[j++] = agent.c;
        }
        for (final KTrue _ : bTrueKinds) {
            producers[j++] = new Motor.ATrue().a;
        }
        for (final KFalse _ : bFalseKinds) {
            producers[j++] = new Motor.AFalse().a;
        }
        for (final KInteger k : iKinds) {
            producers[j++] = new Motor.AInteger(k.value).a;
        }
        for (final KBigInteger k : biKinds) {
            producers[j++] = new Motor.ABigInteger(k.value).a;
        }
        for (final KString k : sKinds) {
            producers[j++] = new Motor.AString(k.value).a;
        }
        for (final KRangeFull _ : rngFullKinds) {
            producers[j++] = new Motor.ARangeFull().a;
        }
        for (final KIdentity _ : idKinds) {
            producers[j++] = new Motor.AIdentity().a;
        }
        for (final KReference k : refKinds) {
            producers[j++] = new Motor.AReference(k.name).a;
        }
        for (final KEndOfList _ : endKinds) {
            producers[j++] = new Motor.AEndOfList().a;
        }
        for (final KLambda _ : lamKinds) {
            final var agent = new Motor.ALambda();
            producers[j++] = agent.a;
            producers[j++] = agent.b;
            consumers[i++] = agent.c;
        }
        for (final KConstructor k : ctrKinds) {
            final var agent = new Motor.AConstructor(k.name, k.arity);
            producers[j++] = agent.a;
            for (final var port : agent.arguments) {
                consumers[i++] = port;
            }
        }
        for (final Link link : links) {
            consumers[link.consumer].setProducer(producers[link.producer]);
        }
    }

    public static final class Builder {
        public sealed interface Port permits Consumer, Producer {
        }

        public static final class Consumer implements Port {
            private Producer producer;

            public Consumer() {
                this.producer = null;
            }

            public Producer producer() {
                return this.producer;
            }

            public void setProducer(final Producer producer) {
                this.producer = producer;
            }
        }

        public static final class Producer implements Port {
        }

        private record Agent(Kind kind, Port[] ports) {
        }

        public static final class ARoot {
            private final Consumer a;

            private ARoot(final Consumer a) {
                this.a = a;
            }

            public Consumer a() {
                return a;
            }
        }

        public static final class AStrictOp1 {
            private final Consumer a;
            private final Producer b;

            private AStrictOp1(final Consumer a, final Producer b) {
                this.a = a;
                this.b = b;
            }

            public Consumer a() {
                return a;
            }

            public Producer b() {
                return b;
            }
        }

        public static final class AStrictOp2 {
            private final Consumer a;
            private final Producer b;
            private final Consumer c;

            private AStrictOp2(final Consumer a, final Producer b, final Consumer c) {
                this.a = a;
                this.b = b;
                this.c = c;
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

        public static final class AIfThenElse {
            private final Consumer a;
            private final Producer b;
            private final Consumer c;
            private final Consumer d;

            private AIfThenElse(
                    final Consumer a,
                    final Producer b,
                    final Consumer c,
                    final Consumer d) {
                this.a = a;
                this.b = b;
                this.c = c;
                this.d = d;
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

        public static final class ANot {
            private final Consumer a;
            private final Producer b;

            private ANot(final Consumer a, final Producer b) {
                this.a = a;
                this.b = b;
            }

            public Consumer a() {
                return a;
            }

            public Producer b() {
                return b;
            }
        }

        public static final class AAnd {
            private final Consumer a;
            private final Producer b;
            private final Consumer c;

            private AAnd(final Consumer a, final Producer b, final Consumer c) {
                this.a = a;
                this.b = b;
                this.c = c;
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

        public static final class AOr {
            private final Consumer a;
            private final Producer b;
            private final Consumer c;

            private AOr(final Consumer a, final Producer b, final Consumer c) {
                this.a = a;
                this.b = b;
                this.c = c;
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

        public static final class ADoRange {
            private final Consumer a;
            private final Producer b;
            private final Consumer c;

            private ADoRange(final Consumer a, final Producer b, final Consumer c) {
                this.a = a;
                this.b = b;
                this.c = c;
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

        public static final class ADoRangeFrom {
            private final Consumer a;
            private final Producer b;

            private ADoRangeFrom(final Consumer a, final Producer b) {
                this.a = a;
                this.b = b;
            }

            public Consumer a() {
                return a;
            }

            public Producer b() {
                return b;
            }
        }

        public static final class ADoRangeTo {
            private final Consumer a;
            private final Producer b;

            private ADoRangeTo(final Consumer a, final Producer b) {
                this.a = a;
                this.b = b;
            }

            public Consumer a() {
                return a;
            }

            public Producer b() {
                return b;
            }
        }

        public static final class ARangeFull {
            private final Producer a;

            private ARangeFull(final Producer a) {
                this.a = a;
            }

            public Producer a() {
                return a;
            }
        }

        public static final class AApplicator {
            private final Consumer a;
            private final Producer b;
            private final Consumer c;

            private AApplicator(final Consumer a, final Producer b, final Consumer c) {
                this.a = a;
                this.b = b;
                this.c = c;
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

        public static final class AStrictApplicator {
            private final Consumer a;
            private final Producer b;
            private final Consumer c;

            private AStrictApplicator(final Consumer a, final Producer b, final Consumer c) {
                this.a = a;
                this.b = b;
                this.c = c;
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

        public static final class AResolver {
            private final Consumer a;
            private final Producer b;
            private final Producer c;
            private final Consumer d;

            private AResolver(
                    final Consumer a,
                    final Producer b,
                    final Producer c,
                    final Consumer d) {
                this.a = a;
                this.b = b;
                this.c = c;
                this.d = d;
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

        public static final class ACapture {
            private final Consumer a;
            private final Producer b;
            private final Producer c;
            private final Consumer d;

            private ACapture(
                    final Consumer a,
                    final Producer b,
                    final Producer c,
                    final Consumer d) {
                this.a = a;
                this.b = b;
                this.c = c;
                this.d = d;
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

        public static final class AFix {
            private final Consumer a;
            private final Producer b;

            private AFix(final Consumer a, final Producer b) {
                this.a = a;
                this.b = b;
            }

            public Consumer a() {
                return a;
            }

            public Producer b() {
                return b;
            }
        }

        public static final class ADuplicator {
            private final Consumer a;
            private final Producer b;
            private final Producer c;

            private ADuplicator(final Consumer a, final Producer b, final Producer c) {
                this.a = a;
                this.b = b;
                this.c = c;
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

        public static final class ATrue {
            private final Producer a;

            private ATrue(final Producer a) {
                this.a = a;
            }

            public Producer a() {
                return a;
            }
        }

        public static final class AFalse {
            private final Producer a;

            private AFalse(final Producer a) {
                this.a = a;
            }

            public Producer a() {
                return a;
            }
        }

        public static final class AInteger {
            private final Producer a;

            private AInteger(final Producer a) {
                this.a = a;
            }

            public Producer a() {
                return a;
            }
        }

        public static final class ABigInteger {
            private final Producer a;

            private ABigInteger(final Producer a) {
                this.a = a;
            }

            public Producer a() {
                return a;
            }
        }

        public static final class AString {
            private final Producer a;

            private AString(final Producer a) {
                this.a = a;
            }

            public Producer a() {
                return a;
            }
        }

        public static final class AIdentity {
            private final Producer a;

            private AIdentity(final Producer a) {
                this.a = a;
            }

            public Producer a() {
                return a;
            }
        }

        public static final class AReference {
            private final Producer a;

            private AReference(final Producer a) {
                this.a = a;
            }

            public Producer a() {
                return a;
            }
        }

        public static final class AEndOfList {
            private final Producer a;

            private AEndOfList(final Producer a) {
                this.a = a;
            }

            public Producer a() {
                return a;
            }
        }

        public static final class ALambda {
            private final Producer a;
            private final Producer b;
            private final Consumer c;

            private ALambda(final Producer a, final Producer b, final Consumer c) {
                this.a = a;
                this.b = b;
                this.c = c;
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

        public static final class AConstructor {
            private final Producer a;
            private final Consumer[] arguments;

            private AConstructor(final Producer a, final Consumer[] arguments) {
                this.a = a;
                this.arguments = arguments;
            }

            public Producer a() {
                return a;
            }

            public Consumer argument(final int i) {
                return arguments[i];
            }
        }

        public static final class AMatch {
            private final Consumer a;
            private final Producer b;
            private final Consumer[] handlers;

            private AMatch(final Consumer a, final Producer b, final Consumer[] handlers) {
                this.a = a;
                this.b = b;
                this.handlers = handlers;
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

        public ARoot mkRoot() {
            if (root != null) {
                throw new IllegalStateException("Attempting to create a second root");
            }
            final Consumer a = new Consumer();
            root = new Agent(new KRoot(), new Port[]{a});
            agents.add(root);
            return new ARoot(a);
        }

        public AStrictOp1 mkStrictOp1(final StrictOp1 op) {
            final Consumer a = new Consumer();
            final Producer b = new Producer();
            agents.add(new Agent(new KStrictOp1(op), new Port[]{a, b}));
            return new AStrictOp1(a, b);
        }

        public AStrictOp2 mkStrictOp2(final StrictOp2 op) {
            final Consumer a = new Consumer();
            final Producer b = new Producer();
            final Consumer c = new Consumer();
            agents.add(new Agent(new KStrictOp2(op), new Port[]{a, b, c}));
            return new AStrictOp2(a, b, c);
        }

        public AIfThenElse mkIfThenElse() {
            final Consumer a = new Consumer();
            final Producer b = new Producer();
            final Consumer c = new Consumer();
            final Consumer d = new Consumer();
            agents.add(new Agent(new KIfThenElse(), new Port[]{a, b, c, d}));
            return new AIfThenElse(a, b, c, d);
        }

        public ANot mkNot() {
            final Consumer a = new Consumer();
            final Producer b = new Producer();
            agents.add(new Agent(new KNot(), new Port[]{a, b}));
            return new ANot(a, b);
        }

        public AAnd mkAnd() {
            final Consumer a = new Consumer();
            final Producer b = new Producer();
            final Consumer c = new Consumer();
            agents.add(new Agent(new KAnd(), new Port[]{a, b, c}));
            return new AAnd(a, b, c);
        }

        public AOr mkOr() {
            final Consumer a = new Consumer();
            final Producer b = new Producer();
            final Consumer c = new Consumer();
            agents.add(new Agent(new KOr(), new Port[]{a, b, c}));
            return new AOr(a, b, c);
        }

        public ADoRange mkDoRange() {
            final Consumer a = new Consumer();
            final Producer b = new Producer();
            final Consumer c = new Consumer();
            agents.add(new Agent(new KDoRange(), new Port[]{a, b, c}));
            return new ADoRange(a, b, c);
        }

        public ADoRangeFrom mkDoRangeFrom() {
            final Consumer a = new Consumer();
            final Producer b = new Producer();
            agents.add(new Agent(new KDoRangeFrom(), new Port[]{a, b}));
            return new ADoRangeFrom(a, b);
        }

        public ADoRangeTo mkDoRangeTo() {
            final Consumer a = new Consumer();
            final Producer b = new Producer();
            agents.add(new Agent(new KDoRangeTo(), new Port[]{a, b}));
            return new ADoRangeTo(a, b);
        }

        public ARangeFull mkRangeFull() {
            final Producer a = new Producer();
            agents.add(new Agent(new KRangeFull(), new Port[]{a}));
            return new ARangeFull(a);
        }

        public AApplicator mkApplicator() {
            final Consumer a = new Consumer();
            final Producer b = new Producer();
            final Consumer c = new Consumer();
            agents.add(new Agent(new KApplicator(), new Port[]{a, b, c}));
            return new AApplicator(a, b, c);
        }

        public AStrictApplicator mkStrictApplicator() {
            final Consumer a = new Consumer();
            final Producer b = new Producer();
            final Consumer c = new Consumer();
            agents.add(new Agent(new KStrictApplicator(), new Port[]{a, b, c}));
            return new AStrictApplicator(a, b, c);
        }

        public AResolver mkResolver() {
            final Consumer a = new Consumer();
            final Producer b = new Producer();
            final Producer c = new Producer();
            final Consumer d = new Consumer();
            agents.add(new Agent(new KResolver(), new Port[]{a, b, c, d}));
            return new AResolver(a, b, c, d);
        }

        public ACapture mkCapture() {
            final Consumer a = new Consumer();
            final Producer b = new Producer();
            final Producer c = new Producer();
            final Consumer d = new Consumer();
            agents.add(new Agent(new KCapture(), new Port[]{a, b, c, d}));
            return new ACapture(a, b, c, d);
        }

        public AFix mkFix() {
            final Consumer a = new Consumer();
            final Producer b = new Producer();
            agents.add(new Agent(new KFix(), new Port[]{a, b}));
            return new AFix(a, b);
        }

        public ADuplicator mkDuplicator() {
            final Consumer a = new Consumer();
            final Producer b = new Producer();
            final Producer c = new Producer();
            agents.add(new Agent(new KDuplicator(), new Port[]{a, b, c}));
            return new ADuplicator(a, b, c);
        }

        public ATrue mkTrue() {
            final Producer a = new Producer();
            agents.add(new Agent(new KTrue(), new Port[]{a}));
            return new ATrue(a);
        }

        public AFalse mkFalse() {
            final Producer a = new Producer();
            agents.add(new Agent(new KFalse(), new Port[]{a}));
            return new AFalse(a);
        }

        public AInteger mkInteger(final CheckedInteger.Value i) {
            final Producer a = new Producer();
            agents.add(new Agent(new KInteger(i), new Port[]{a}));
            return new AInteger(a);
        }

        public ABigInteger mkBigInteger(final MyBigInteger i) {
            final Producer a = new Producer();
            agents.add(new Agent(new KBigInteger(i), new Port[]{a}));
            return new ABigInteger(a);
        }

        public AString mkString(final MyString s) {
            final Producer a = new Producer();
            agents.add(new Agent(new KString(s), new Port[]{a}));
            return new AString(a);
        }

        public AIdentity mkIdentity() {
            final Producer a = new Producer();
            agents.add(new Agent(new KIdentity(), new Port[]{a}));
            return new AIdentity(a);
        }

        public AReference mkReference(final String name) {
            final Producer a = new Producer();
            agents.add(new Agent(new KReference(name), new Port[]{a}));
            return new AReference(a);
        }

        public AEndOfList mkEndOfList() {
            final Producer a = new Producer();
            agents.add(new Agent(new KEndOfList(), new Port[]{a}));
            return new AEndOfList(a);
        }

        public ALambda mkLambda() {
            final Producer a = new Producer();
            final Producer b = new Producer();
            final Consumer c = new Consumer();
            agents.add(new Agent(new KLambda(), new Port[]{a, b, c}));
            return new ALambda(a, b, c);
        }

        public AConstructor mkConstructor(final String name, final int arity) {
            final Producer a = new Producer();
            final Consumer[] arguments = new Consumer[arity];
            for (int i = 0; i < arity; i++) {
                arguments[i] = new Consumer();
            }
            final Port[] ports = new Port[1 + arity];
            ports[0] = a;
            for (int i = 0; i < arity; i++) {
                ports[1 + i] = arguments[i];
            }
            agents.add(new Agent(new KConstructor(name, arity), ports));
            return new AConstructor(a, arguments);
        }

        public AMatch mkMatch(final String[] names) {
            final Consumer a = new Consumer();
            final Producer b = new Producer();
            final Consumer[] handlers = new Consumer[names.length];
            for (int i = 0; i < names.length; i++) {
                handlers[i] = new Consumer();
            }
            final Port[] ports = new Port[2 + names.length];
            ports[0] = a;
            ports[1] = b;
            for (int i = 0; i < names.length; i++) {
                ports[2 + i] = handlers[i];
            }
            agents.add(new Agent(new KMatch(names), ports));
            return new AMatch(a, b, handlers);
        }

        private final Set<Agent> agents = new HashSet<>();
        private Agent root;

        @SuppressWarnings("unchecked")
        private <K extends Kind> K[] collect(final Class<K> type, final List<Agent> into) {
            final var result = new ArrayList<K>();
            for (final Agent agent : agents) {
                if (type.isInstance(agent.kind)) {
                    result.add(type.cast(agent.kind));
                    into.add(agent);
                }
            }
            return result.toArray((K[]) Array.newInstance(type, result.size()));
        }

        public Template build() {
            final var orderedAgents = new ArrayList<Agent>(agents.size());
            orderedAgents.add(root);
            final var op1Kinds = collect(KStrictOp1.class, orderedAgents);
            final var op2Kinds = collect(KStrictOp2.class, orderedAgents);
            final var iteKinds = collect(KIfThenElse.class, orderedAgents);
            final var notKinds = collect(KNot.class, orderedAgents);
            final var andKinds = collect(KAnd.class, orderedAgents);
            final var orKinds = collect(KOr.class, orderedAgents);
            final var doRngKinds = collect(KDoRange.class, orderedAgents);
            final var doRngFromKinds = collect(KDoRangeFrom.class, orderedAgents);
            final var doRngToKinds = collect(KDoRangeTo.class, orderedAgents);
            final var appKinds = collect(KApplicator.class, orderedAgents);
            final var sappKinds = collect(KStrictApplicator.class, orderedAgents);
            final var resKinds = collect(KResolver.class, orderedAgents);
            final var capKinds = collect(KCapture.class, orderedAgents);
            final var fixKinds = collect(KFix.class, orderedAgents);
            final var matchKinds = collect(KMatch.class, orderedAgents);
            final var dupKinds = collect(KDuplicator.class, orderedAgents);
            final var bTrueKinds = collect(KTrue.class, orderedAgents);
            final var bFalseKinds = collect(KFalse.class, orderedAgents);
            final var iKinds = collect(KInteger.class, orderedAgents);
            final var biKinds = collect(KBigInteger.class, orderedAgents);
            final var sKinds = collect(KString.class, orderedAgents);
            final var rngFullKinds = collect(KRangeFull.class, orderedAgents);
            final var idKinds = collect(KIdentity.class, orderedAgents);
            final var refKinds = collect(KReference.class, orderedAgents);
            final var endKinds = collect(KEndOfList.class, orderedAgents);
            final var lamKinds = collect(KLambda.class, orderedAgents);
            final var ctrKinds = collect(KConstructor.class, orderedAgents);

            final var consumerIndex = new IdentityHashMap<Consumer, Integer>();
            final var producerIndex = new IdentityHashMap<Producer, Integer>();
            for (final Agent agent : orderedAgents) {
                for (final Port port : agent.ports) {
                    switch (port) {
                        case Consumer consumer ->
                            consumerIndex.put(consumer, consumerIndex.size());
                        case Producer producer ->
                            producerIndex.put(producer, producerIndex.size());
                    }
                }
            }

            final var links = new Link[consumerIndex.size()];
            for (final var entry : consumerIndex.entrySet()) {
                final Consumer consumer = entry.getKey();
                final int index = entry.getValue();
                final int producer = producerIndex.get(consumer.producer);
                links[index] = new Link(index, producer);
            }

            return new Template(
                    op1Kinds,
                    op2Kinds,
                    iteKinds,
                    notKinds,
                    andKinds,
                    orKinds,
                    doRngKinds,
                    doRngFromKinds,
                    doRngToKinds,
                    appKinds,
                    sappKinds,
                    resKinds,
                    capKinds,
                    fixKinds,
                    matchKinds,
                    dupKinds,
                    bTrueKinds,
                    bFalseKinds,
                    iKinds,
                    biKinds,
                    sKinds,
                    rngFullKinds,
                    idKinds,
                    refKinds,
                    endKinds,
                    lamKinds,
                    ctrKinds,
                    links,
                    consumerIndex.size(),
                    producerIndex.size());
        }
    }
}
