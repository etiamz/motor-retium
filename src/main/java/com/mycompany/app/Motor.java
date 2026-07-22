package com.mycompany.app;

import static com.mycompany.app.CheckedInteger.IntegerTy.*;
import static com.mycompany.app.Primitives.StrictOp2.*;

import com.mycompany.app.CheckedInteger.IntegerTy;
import com.mycompany.app.CheckedInteger.Value;
import com.mycompany.app.Port.Consumer;
import com.mycompany.app.Port.Producer;
import com.mycompany.app.Primitives.StrictOp1;
import com.mycompany.app.Primitives.StrictOp2;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public final class Motor {
    private sealed interface Bounce permits Thunk, Await, Halt {
    }

    @FunctionalInterface
    private non-sealed interface Thunk extends Bounce {
        Bounce run();
    }

    private record Await(CompletableFuture<?> future, Thunk resume) implements Bounce {
    }

    private enum Halt implements Bounce {
        HALT
    }

    private static final int HEARTBEAT = 64; // empirically established

    private static final ConcurrentHashMap<Object, Object> HSEARCH_TABLE = new ConcurrentHashMap<>();

    private static final ForkJoinPool POOL = new ForkJoinPool();

    private final Map<String, Template> book;

    public Motor(final Map<String, Template> book) {
        this.book = book;
    }

    // Whether to fork a right operand or reduce it inline depends on whether the work performed by
    // the right operand will outweigh the cost of scheduling, which we cannot know in advance. As a
    // solution to this problem, we adopt so-called "heartbeat scheduling" [1] for strict binary
    // operators: instead of forking the right operand eagerly, we instead push it to a queue &
    // promote the oldest pending one to a parallel task once every `HEARTBEAT` trampoline steps,
    // thereby controlling the number of realized forks by performed machine work. Unlike other
    // approaches, heartbeat scheduling is shown to be resistant to adversarial scenarios, which
    // are so abundant in interaction-net workloads.
    //
    // [1] Acar, Umut A., et al. "Heartbeat scheduling: Provable efficiency for nested parallelism."
    // Proceedings of the 39th ACM SIGPLAN Conference on Programming Language Design and
    // Implementation. 2018.
    private static final class Heart {
        // How many trampoline iterations must happen until the next frame promotion.
        private int fuel;
        // The doubly-linked list of promotable frames.
        private Promotable oldest, newest;

        private Heart(final int fuel) {
            this.fuel = fuel;
        }

        private Promotable push(final Consumer right) {
            final var frame = new Promotable(right);
            frame.older = newest;
            if (newest == null) {
                oldest = frame;
            } else {
                newest.newer = frame;
            }
            newest = frame;
            return frame;
        }

        private Promotable pollOldest() {
            final Promotable frame = oldest;
            if (frame != null) {
                unlink(frame);
            }
            return frame;
        }

        private void unlink(final Promotable frame) {
            if (frame.older == null) {
                oldest = frame.newer;
            } else {
                frame.older.newer = frame.newer;
            }
            if (frame.newer == null) {
                newest = frame.older;
            } else {
                frame.newer.older = frame.older;
            }
            frame.older = null;
            frame.newer = null;
        }
    }

    private static final class Promotable {
        // The right operand to consider for parallel evaluation.
        private final Consumer right;
        // Null if not promoted, otherwise the spawned evaluation of right.
        private CompletableFuture<Agent> future;
        // The intrusive doubly-linked list of promotables.
        private Promotable older, newer;

        private Promotable(final Consumer right) {
            this.right = right;
        }
    }

    public Agent whnf(final Consumer root) {
        return whnfAsync(root).join();
    }

    private CompletableFuture<Agent> whnfAsync(final Consumer root) {
        final var result = new CompletableFuture<Agent>();
        final var heart = new Heart(HEARTBEAT);
        schedule(heart, () -> reduce(root, () -> {
            result.complete(root.chase());
            return Halt.HALT;
        }, heart));
        return result;
    }

    private void drive(Bounce bounce, final Heart heart) {
        try {
            for (;;) {
                switch (bounce) {
                    case Thunk thunk -> {
                        bounce = thunk.run();
                        beat(heart);
                    }
                    case Await(var future, var resume) -> {
                        future.thenRun(() -> schedule(heart, resume));
                        return;
                    }
                    case Halt _ -> {
                        return;
                    }
                }
            }
        } catch (final Panic e) {
            System.err.println("Panic: " + e.getMessage());
            Runtime.getRuntime().halt(1);
        } catch (final RuntimeException e) {
            e.printStackTrace();
            Runtime.getRuntime().halt(1);
        }
    }

    private void schedule(final Heart heart, final Thunk thunk) {
        POOL.execute(() -> drive(thunk, heart));
    }

    // Beat the heart; when the remaining fuel is zero, promote the oldest frame, if it exists. This
    // is where logical threads are spawned.
    private void beat(final Heart heart) {
        if (--heart.fuel == 0) {
            heart.fuel = HEARTBEAT;
            final Promotable frame = heart.pollOldest();
            if (frame != null) {
                frame.future = whnfAsync(frame.right);
            }
        }
    }

    private Bounce reduce(final Consumer p, final Thunk k, final Heart heart) {
        return switch (p.chase()) {
            case AStrictOp1 rator -> simplex(rator.a, rator::interact, p, k, heart);
            case AIfThenElse rator -> simplex(rator.a, rator::interact, p, k, heart);
            case ANot rator -> simplex(rator.a, rator::interact, p, k, heart);
            case AAnd rator -> simplex(rator.a, rator::interact, p, k, heart);
            case AOr rator -> simplex(rator.a, rator::interact, p, k, heart);
            case AApplicator rator -> simplex(rator.a, rator::interact, p, k, heart);
            case AStrictApplicator rator -> duplex(rator.a, rator.c, rator::interact, p, k, heart);
            case AResolver rator -> simplex(rator.a, rator::interact, p, k, heart);
            case ACapture rator -> duplex(rator.a, rator.d, rator::interact, p, k, heart);
            case AFix rator -> simplex(rator.a, rator::interact, p, k, heart);
            case AStrictOp2 rator -> duplex(rator.a, rator.c, rator::interact, p, k, heart);
            case ADoRange rator -> duplex(rator.a, rator.c, rator::interact, p, k, heart);
            case ADoRangeFrom rator -> simplex(rator.a, rator::interact, p, k, heart);
            case ADoRangeTo rator -> simplex(rator.a, rator::interact, p, k, heart);
            case AMatch rator -> simplex(rator.a, rator::interact, p, k, heart);
            case ADuplicator dup -> sync(dup, p, k, heart);
            case AReference ref -> (Thunk) () -> {
                dereference(p, ref);
                return reduce(p, k, heart);
            };
            case ALambda _,AEndOfList _,ANull _,ATrue _,AFalse _,AInteger _,ABigInteger _,AString _,ARange _,ARangeFrom _,ARangeTo _,ARangeFull _,AIdentity _,AConstructor _,ASuperposition _ ->
                k;
        };
    }

    private Thunk simplex(
            final Consumer target,
            final Runnable interactor,
            final Consumer p,
            final Thunk k,
            final Heart heart) {
        if (isWhnf(target)) {
            return () -> {
                interactor.run();
                return reduce(p, k, heart);
            };
        }
        return () -> reduce(target, () -> {
            interactor.run();
            return reduce(p, k, heart);
        }, heart);
    }

    private Thunk duplex(
            final Consumer left,
            final Consumer right,
            final Runnable interactor,
            final Consumer p,
            final Thunk k,
            final Heart heart) {
        final boolean isLeftWhnf = isWhnf(left), isRightWhnf = isWhnf(right);
        if (isLeftWhnf && isRightWhnf) {
            return () -> {
                interactor.run();
                return reduce(p, k, heart);
            };
        }
        if (isRightWhnf) {
            return () -> reduce(left, () -> {
                interactor.run();
                return reduce(p, k, heart);
            }, heart);
        }
        if (isLeftWhnf) {
            return () -> reduce(right, () -> {
                interactor.run();
                return reduce(p, k, heart);
            }, heart);
        }
        return () -> {
            final Promotable frame = heart.push(right);
            return reduce(left, () -> {
                final CompletableFuture<Agent> future = frame.future;
                // If not null, the frame has been promoted; await the future & run the interaction
                // once it is ready.
                if (future != null) {
                    return new Await(future, () -> {
                        interactor.run();
                        return reduce(p, k, heart);
                    });
                }
                // The frame has not been promoted; unlink the frame from the list & reduce the
                // right operand inline.
                heart.unlink(frame);
                return reduce(right, () -> {
                    interactor.run();
                    return reduce(p, k, heart);
                }, heart);
            }, heart);
        };
    }

    private Bounce sync(
            final ADuplicator dup,
            final Consumer p,
            final Thunk k,
            final Heart heart) {
        final var origin = p.producer() == dup.b ? Origin.LEFT : Origin.RIGHT;
        final var mine = new CompletableFuture<Duplicand>();
        final var owner = dup.sync.compareAndExchange(null, mine);
        if (owner == null) {
            return (Thunk) () -> reduce(dup.a, () -> {
                final Duplicand duplicand = dup.interact();
                mine.complete(duplicand);
                duplicand.connect(p, origin);
                return reduce(p, k, heart);
            }, heart);
        }
        return new Await(owner, () -> {
            owner.resultNow().connect(p, origin);
            return reduce(p, k, heart);
        });
    }

    private void dereference(final Consumer port, final AReference ref) {
        final Template body = book.get(ref.name);
        if (body == null) {
            crash("Cannot resolve a reference to `%s`", ref.name);
        }
        body.materialize(port);
    }

    // @formatter:off
    public sealed interface Agent permits
        // Operators.
        AStrictOp1, AStrictOp2, AIfThenElse, ANot, AAnd, AOr, ADoRange, ADoRangeFrom, ADoRangeTo, AApplicator, AStrictApplicator, AResolver, ACapture, AFix, AMatch, ADuplicator,
        // Data.
        ALambda, AEndOfList, ANull, ATrue, AFalse, AInteger, ABigInteger, AString, ARange, ARangeFrom, ARangeTo, ARangeFull, AIdentity, AReference, AConstructor, ASuperposition
    {
    }
    // @formatter:on

    private record Operands(Agent first, Agent second) {
    }

    public static final class AStrictOp1 implements Agent {
        public final StrictOp1 op;
        public final Consumer a;
        public final Producer b;

        public AStrictOp1(final StrictOp1 op) {
            this.op = op;
            this.a = new Consumer(null);
            this.b = new Producer(this);
        }

        private void interact() {
            try {
                interactAux();
            } catch (final CheckedInteger.OutOfRange e) {
                panic("Out of range: %s", Primitives.describe(e.ty));
            } catch (final MyBigInteger.OutOfRange e) {
                panic("Out of big integer range");
            }
        }

        private void interactAux() {
            final AStrictOp1 op1 = this;
            final Agent data = op1.a.chase();
            switch (data) {
                case ATrue _ -> {
                    switch (op1.op) {
                        case STRING_OF -> op1.b.forward(new AString("true").a);
                        case HASH -> op1.b.forward(AInteger.one(U64).a);
                        default -> reject(data);
                    }
                }
                case AFalse _ -> {
                    switch (op1.op) {
                        case STRING_OF -> op1.b.forward(new AString("false").a);
                        case HASH -> op1.b.forward(AInteger.zero(U64).a);
                        default -> reject(data);
                    }
                }
                case AInteger i -> {
                    switch (op1.op) {
                        case STRING_OF ->
                            op1.b.forward(new AString(i.data.show()).a);
                        case STRING_OF_CHARACTER -> {
                            if (i.ty() == U8) {
                                op1.b.forward(new AString(MyString.ofByte(i.data.toInt())).a);
                            } else {
                                reject(data);
                            }
                        }
                        case NEGATE ->
                            op1.b.forward(new AInteger(i.data.negate()).a);
                        case FFS ->
                            op1.b.forward(new AInteger(i.data.ffs()).a);
                        case CLZ ->
                            op1.b.forward(new AInteger(i.data.clz()).a);
                        case CTZ ->
                            op1.b.forward(new AInteger(i.data.ctz()).a);
                        case CLRSB ->
                            op1.b.forward(new AInteger(i.data.clrsb()).a);
                        case POPCOUNT ->
                            op1.b.forward(new AInteger(i.data.popcount()).a);
                        case PARITY ->
                            op1.b.forward(new AInteger(i.data.parity()).a);
                        case HASH ->
                            op1.b.forward(new AInteger(new Value(U64, i.data.hash64())).a);
                        default ->
                            reject(data);
                    }
                }
                case ABigInteger i -> {
                    switch (op1.op) {
                        case STRING_OF ->
                            op1.b.forward(new AString(i.data.show()).a);
                        case NEGATE ->
                            op1.b.forward(new ABigInteger(i.data.negate()).a);
                        case POPCOUNT ->
                            op1.b.forward(new AInteger(U64.of(i.data.popcount())).a);
                        case PARITY ->
                            op1.b.forward(new AInteger(U64.of(i.data.parity())).a);
                        case HASH ->
                            op1.b.forward(new AInteger(new Value(U64, i.data.hash64())).a);
                        default ->
                            reject(data);
                    }
                }
                case AString s -> {
                    switch (op1.op) {
                        case STRING_OF ->
                            op1.b.forward(s.a);
                        case STRLEN ->
                            op1.b.forward(new AInteger(IntegerTy.U64.of(s.data.length())).a);
                        case PANIC ->
                            panic("User panic: %s", s.data.toString());
                        case HASH ->
                            op1.b.forward(new AInteger(new Value(U64, s.data.hash64())).a);
                        default ->
                            reject(data);
                    }
                }
                case ASuperposition sup -> {
                    final var op1x = new AStrictOp1(op1.op);
                    final var op1xx = new AStrictOp1(op1.op);
                    final var supx = sup; // reuse
                    op1.b.forward(supx.a);
                    op1x.a.setProducer(sup.b.producer());
                    op1xx.a.setProducer(sup.c.producer());
                    supx.b.setProducer(op1x.b);
                    supx.c.setProducer(op1xx.b);
                }
                default -> reject(data);
            }
        }

        private void reject(final Agent data) {
            final AStrictOp1 op1 = this;
            if (isMachineData(data)) {
                crash("Operand not welcome: %s", describe(data));
            } else if (data instanceof ANull) {
                panic("Null operand: %s", op1.op.describe());
            } else if (isUserData(data)) {
                typeError(op1.op.describe(), data);
            } else if (isOperator(data)) {
                crash("Operand unresolved: %s", describe(data));
            } else {
                throw new IllegalStateException();
            }
        }
    }

    public static final class AStrictOp2 implements Agent {
        public final StrictOp2 op;
        public final Consumer a;
        public final Producer b;
        public final Consumer c;

        public AStrictOp2(final StrictOp2 op) {
            this.op = op;
            this.a = new Consumer(null);
            this.b = new Producer(this);
            this.c = new Consumer(null);
        }

        private void interact() {
            try {
                interactAux();
            } catch (final CheckedInteger.OutOfRange e) {
                panic("Out of range: %s", Primitives.describe(e.ty));
            } catch (final MyBigInteger.OutOfRange e) {
                panic("Out of big integer range");
            }
        }

        private void interactAux() {
            final AStrictOp2 op2 = this;
            final Agent left = op2.a.chase(), right = op2.c.chase();
            switch (left) {
                case ANull mynull -> {
                    switch (right) {
                        case ANull _ -> {
                            switch (op2.op) {
                                case EQUALS -> op2.b.forward(new ATrue().a);
                                case NOT_EQUALS -> op2.b.forward(new AFalse().a);
                                default -> reject(left, right);
                            }
                        }
                        case Agent _ when isPrimitiveData(right) -> {
                            switch (op2.op) {
                                case EQUALS -> op2.b.forward(new AFalse().a);
                                case NOT_EQUALS -> op2.b.forward(new ATrue().a);
                                default -> reject(left, right);
                            }
                        }
                        case ASuperposition sup -> {
                            final var op2x = new AStrictOp2(op2.op);
                            final var op2xx = new AStrictOp2(op2.op);
                            final var supx = sup; // reuse
                            op2.b.forward(supx.a);
                            op2x.c.setProducer(sup.b.producer());
                            op2xx.c.setProducer(sup.c.producer());
                            supx.b.setProducer(op2x.b);
                            supx.c.setProducer(op2xx.b);
                            op2x.a.setProducer(mynull.a);
                            op2xx.a.setProducer(new ANull().a);
                        }
                        default -> reject(left, right);
                    }
                }
                case ATrue b1 -> {
                    switch (right) {
                        case ANull _ -> {
                            switch (op2.op) {
                                case EQUALS -> op2.b.forward(new AFalse().a);
                                case NOT_EQUALS -> op2.b.forward(new ATrue().a);
                                default -> reject(left, right);
                            }
                        }
                        case ATrue _ -> {
                            switch (op2.op) {
                                case EQUALS -> op2.b.forward(b1.a);
                                case NOT_EQUALS -> op2.b.forward(new AFalse().a);
                                case LESS -> op2.b.forward(new AFalse().a);
                                case LESS_OR_EQUALS -> op2.b.forward(b1.a);
                                case GREATER -> op2.b.forward(new AFalse().a);
                                case GREATER_OR_EQUALS -> op2.b.forward(b1.a);
                                case STRICT_OR -> op2.b.forward(b1.a);
                                case STRICT_AND -> op2.b.forward(b1.a);
                                case STRICT_XOR -> op2.b.forward(new AFalse().a);
                                case MIN -> op2.b.forward(b1.a);
                                case MAX -> op2.b.forward(b1.a);
                                case HSEARCH -> hsearch(left, right);
                                default -> reject(left, right);
                            }
                        }
                        case AFalse b2 -> {
                            switch (op2.op) {
                                case EQUALS -> op2.b.forward(b2.a);
                                case NOT_EQUALS -> op2.b.forward(b1.a);
                                case LESS -> op2.b.forward(b2.a);
                                case LESS_OR_EQUALS -> op2.b.forward(b2.a);
                                case GREATER -> op2.b.forward(b1.a);
                                case GREATER_OR_EQUALS -> op2.b.forward(b1.a);
                                case STRICT_OR -> op2.b.forward(b1.a);
                                case STRICT_AND -> op2.b.forward(b2.a);
                                case STRICT_XOR -> op2.b.forward(b1.a);
                                case MIN -> op2.b.forward(b2.a);
                                case MAX -> op2.b.forward(b1.a);
                                case HSEARCH -> hsearch(left, right);
                                default -> reject(left, right);
                            }
                        }
                        case Agent _ when isHsearchData(right) -> {
                            switch (op2.op) {
                                case HSEARCH -> hsearch(left, right);
                                default -> reject(left, right);
                            }
                        }
                        case ASuperposition sup -> {
                            final var op2x = new AStrictOp2(op2.op);
                            final var op2xx = new AStrictOp2(op2.op);
                            final var supx = sup; // reuse
                            op2.b.forward(supx.a);
                            op2x.c.setProducer(sup.b.producer());
                            op2xx.c.setProducer(sup.c.producer());
                            supx.b.setProducer(op2x.b);
                            supx.c.setProducer(op2xx.b);
                            op2x.a.setProducer(b1.a);
                            op2xx.a.setProducer(new ATrue().a);
                        }
                        default -> reject(left, right);
                    }
                }
                case AFalse b1 -> {
                    switch (right) {
                        case ANull _ -> {
                            switch (op2.op) {
                                case EQUALS -> op2.b.forward(new AFalse().a);
                                case NOT_EQUALS -> op2.b.forward(new ATrue().a);
                                default -> reject(left, right);
                            }
                        }
                        case ATrue b2 -> {
                            switch (op2.op) {
                                case EQUALS -> op2.b.forward(b1.a);
                                case NOT_EQUALS -> op2.b.forward(b2.a);
                                case LESS -> op2.b.forward(b2.a);
                                case LESS_OR_EQUALS -> op2.b.forward(b2.a);
                                case GREATER -> op2.b.forward(b1.a);
                                case GREATER_OR_EQUALS -> op2.b.forward(b1.a);
                                case STRICT_OR -> op2.b.forward(b2.a);
                                case STRICT_AND -> op2.b.forward(b1.a);
                                case STRICT_XOR -> op2.b.forward(b2.a);
                                case MIN -> op2.b.forward(b1.a);
                                case MAX -> op2.b.forward(b2.a);
                                case HSEARCH -> hsearch(left, right);
                                default -> reject(left, right);
                            }
                        }
                        case AFalse _ -> {
                            switch (op2.op) {
                                case EQUALS -> op2.b.forward(new ATrue().a);
                                case NOT_EQUALS -> op2.b.forward(b1.a);
                                case LESS -> op2.b.forward(b1.a);
                                case LESS_OR_EQUALS -> op2.b.forward(new ATrue().a);
                                case GREATER -> op2.b.forward(b1.a);
                                case GREATER_OR_EQUALS -> op2.b.forward(new ATrue().a);
                                case STRICT_OR -> op2.b.forward(b1.a);
                                case STRICT_AND -> op2.b.forward(b1.a);
                                case STRICT_XOR -> op2.b.forward(b1.a);
                                case MIN -> op2.b.forward(b1.a);
                                case MAX -> op2.b.forward(b1.a);
                                case HSEARCH -> hsearch(left, right);
                                default -> reject(left, right);
                            }
                        }
                        case Agent _ when isHsearchData(right) -> {
                            switch (op2.op) {
                                case HSEARCH -> hsearch(left, right);
                                default -> reject(left, right);
                            }
                        }
                        case ASuperposition sup -> {
                            final var op2x = new AStrictOp2(op2.op);
                            final var op2xx = new AStrictOp2(op2.op);
                            final var supx = sup; // reuse
                            op2.b.forward(supx.a);
                            op2x.c.setProducer(sup.b.producer());
                            op2xx.c.setProducer(sup.c.producer());
                            supx.b.setProducer(op2x.b);
                            supx.c.setProducer(op2xx.b);
                            op2x.a.setProducer(b1.a);
                            op2xx.a.setProducer(new AFalse().a);
                        }
                        default -> reject(left, right);
                    }
                }
                case AInteger i1 -> {
                    switch (right) {
                        case ANull _ -> {
                            switch (op2.op) {
                                case EQUALS -> op2.b.forward(new AFalse().a);
                                case NOT_EQUALS -> op2.b.forward(new ATrue().a);
                                default -> reject(left, right);
                            }
                        }
                        case ATrue _ -> {
                            switch (op2.op) {
                                case OFTYPE -> op2.b.forward(AInteger.one(i1.ty()).a);
                                case HSEARCH -> hsearch(left, right);
                                default -> reject(left, right);
                            }
                        }
                        case AFalse _ -> {
                            switch (op2.op) {
                                case OFTYPE -> op2.b.forward(AInteger.zero(i1.ty()).a);
                                case HSEARCH -> hsearch(left, right);
                                default -> reject(left, right);
                            }
                        }
                        case AInteger i2 -> interact(i1, i2);
                        case ABigInteger i2 -> interact(i1, i2);
                        case Agent _ when isHsearchData(right) -> {
                            switch (op2.op) {
                                case HSEARCH -> hsearch(left, right);
                                default -> reject(left, right);
                            }
                        }
                        case ASuperposition sup -> {
                            final var op2x = new AStrictOp2(op2.op);
                            final var op2xx = new AStrictOp2(op2.op);
                            final var supx = sup; // reuse
                            op2.b.forward(supx.a);
                            op2x.c.setProducer(sup.b.producer());
                            op2xx.c.setProducer(sup.c.producer());
                            supx.b.setProducer(op2x.b);
                            supx.c.setProducer(op2xx.b);
                            op2x.a.setProducer(i1.a);
                            op2xx.a.setProducer(new AInteger(i1.data).a);
                        }
                        default -> reject(left, right);
                    }
                }
                case ABigInteger i1 -> {
                    switch (right) {
                        case ANull _ -> {
                            switch (op2.op) {
                                case EQUALS -> op2.b.forward(new AFalse().a);
                                case NOT_EQUALS -> op2.b.forward(new ATrue().a);
                                default -> reject(left, right);
                            }
                        }
                        case ATrue _ -> {
                            switch (op2.op) {
                                case OFTYPE -> op2.b.forward(ABigInteger.one().a);
                                case HSEARCH -> hsearch(left, right);
                                default -> reject(left, right);
                            }
                        }
                        case AFalse _ -> {
                            switch (op2.op) {
                                case OFTYPE -> op2.b.forward(ABigInteger.zero().a);
                                case HSEARCH -> hsearch(left, right);
                                default -> reject(left, right);
                            }
                        }
                        case AInteger i2 -> interact(i1, i2);
                        case ABigInteger i2 -> interact(i1, i2);
                        case Agent _ when isHsearchData(right) -> {
                            switch (op2.op) {
                                case HSEARCH -> hsearch(left, right);
                                default -> reject(left, right);
                            }
                        }
                        case ASuperposition sup -> {
                            final var op2x = new AStrictOp2(op2.op);
                            final var op2xx = new AStrictOp2(op2.op);
                            final var supx = sup; // reuse
                            op2.b.forward(supx.a);
                            op2x.c.setProducer(sup.b.producer());
                            op2xx.c.setProducer(sup.c.producer());
                            supx.b.setProducer(op2x.b);
                            supx.c.setProducer(op2xx.b);
                            op2x.a.setProducer(i1.a);
                            op2xx.a.setProducer(new ABigInteger(i1.data).a);
                        }
                        default -> reject(left, right);
                    }
                }
                case AString s1 -> {
                    switch (right) {
                        case ANull _ -> {
                            switch (op2.op) {
                                case EQUALS -> op2.b.forward(new AFalse().a);
                                case NOT_EQUALS -> op2.b.forward(new ATrue().a);
                                default -> reject(left, right);
                            }
                        }
                        case AInteger i -> interact(s1, i);
                        case AString s2 -> interact(s1, s2);
                        case ARange rng -> interact(s1, rng);
                        case ARangeFrom rng -> interact(s1, rng);
                        case ARangeTo rng -> interact(s1, rng);
                        case ARangeFull rng -> interact(s1, rng);
                        case Agent _ when isHsearchData(right) -> {
                            switch (op2.op) {
                                case HSEARCH -> hsearch(left, right);
                                default -> reject(left, right);
                            }
                        }
                        case ASuperposition sup -> {
                            final var op2x = new AStrictOp2(op2.op);
                            final var op2xx = new AStrictOp2(op2.op);
                            final var supx = sup; // reuse
                            op2.b.forward(supx.a);
                            op2x.c.setProducer(sup.b.producer());
                            op2xx.c.setProducer(sup.c.producer());
                            supx.b.setProducer(op2x.b);
                            supx.c.setProducer(op2xx.b);
                            op2x.a.setProducer(s1.a);
                            op2xx.a.setProducer(new AString(s1.data).a);
                        }
                        default -> reject(left, right);
                    }
                }
                case ARange rng -> {
                    switch (right) {
                        case ANull _ -> {
                            switch (op2.op) {
                                case EQUALS -> op2.b.forward(new AFalse().a);
                                case NOT_EQUALS -> op2.b.forward(new ATrue().a);
                                default -> reject(left, right);
                            }
                        }
                        case ASuperposition sup -> {
                            final var op2x = new AStrictOp2(op2.op);
                            final var op2xx = new AStrictOp2(op2.op);
                            final var supx = sup; // reuse
                            op2.b.forward(supx.a);
                            op2x.c.setProducer(sup.b.producer());
                            op2xx.c.setProducer(sup.c.producer());
                            supx.b.setProducer(op2x.b);
                            supx.c.setProducer(op2xx.b);
                            op2x.a.setProducer(rng.a);
                            op2xx.a.setProducer(new ARange(rng.start, rng.end, rng.inclusive).a);
                        }
                        default -> reject(left, right);
                    }
                }
                case ARangeFrom rng -> {
                    switch (right) {
                        case ANull _ -> {
                            switch (op2.op) {
                                case EQUALS -> op2.b.forward(new AFalse().a);
                                case NOT_EQUALS -> op2.b.forward(new ATrue().a);
                                default -> reject(left, right);
                            }
                        }
                        case ASuperposition sup -> {
                            final var op2x = new AStrictOp2(op2.op);
                            final var op2xx = new AStrictOp2(op2.op);
                            final var supx = sup; // reuse
                            op2.b.forward(supx.a);
                            op2x.c.setProducer(sup.b.producer());
                            op2xx.c.setProducer(sup.c.producer());
                            supx.b.setProducer(op2x.b);
                            supx.c.setProducer(op2xx.b);
                            op2x.a.setProducer(rng.a);
                            op2xx.a.setProducer(new ARangeFrom(rng.start).a);
                        }
                        default -> reject(left, right);
                    }
                }
                case ARangeTo rng -> {
                    switch (right) {
                        case ANull _ -> {
                            switch (op2.op) {
                                case EQUALS -> op2.b.forward(new AFalse().a);
                                case NOT_EQUALS -> op2.b.forward(new ATrue().a);
                                default -> reject(left, right);
                            }
                        }
                        case ASuperposition sup -> {
                            final var op2x = new AStrictOp2(op2.op);
                            final var op2xx = new AStrictOp2(op2.op);
                            final var supx = sup; // reuse
                            op2.b.forward(supx.a);
                            op2x.c.setProducer(sup.b.producer());
                            op2xx.c.setProducer(sup.c.producer());
                            supx.b.setProducer(op2x.b);
                            supx.c.setProducer(op2xx.b);
                            op2x.a.setProducer(rng.a);
                            op2xx.a.setProducer(new ARangeTo(rng.end, rng.inclusive).a);
                        }
                        default -> reject(left, right);
                    }
                }
                case ARangeFull rng -> {
                    switch (right) {
                        case ANull _ -> {
                            switch (op2.op) {
                                case EQUALS -> op2.b.forward(new AFalse().a);
                                case NOT_EQUALS -> op2.b.forward(new ATrue().a);
                                default -> reject(left, right);
                            }
                        }
                        case ASuperposition sup -> {
                            final var op2x = new AStrictOp2(op2.op);
                            final var op2xx = new AStrictOp2(op2.op);
                            final var supx = sup; // reuse
                            op2.b.forward(supx.a);
                            op2x.c.setProducer(sup.b.producer());
                            op2xx.c.setProducer(sup.c.producer());
                            supx.b.setProducer(op2x.b);
                            supx.c.setProducer(op2xx.b);
                            op2x.a.setProducer(rng.a);
                            op2xx.a.setProducer(new ARangeFull().a);
                        }
                        default -> reject(left, right);
                    }
                }
                case AConstructor ctr -> {
                    if (ctr.isNullary()) {
                        if (op2.op == EQUALS && right instanceof ANull) {
                            op2.b.forward(new AFalse().a);
                        } else if (op2.op == NOT_EQUALS && right instanceof ANull) {
                            op2.b.forward(new ATrue().a);
                        } else if (op2.op == HSEARCH && isHsearchData(right)) {
                            hsearch(left, right);
                        } else if (right instanceof ASuperposition sup) {
                            final var op2x = new AStrictOp2(op2.op);
                            final var op2xx = new AStrictOp2(op2.op);
                            final var supx = sup; // reuse
                            op2.b.forward(supx.a);
                            op2x.c.setProducer(sup.b.producer());
                            op2xx.c.setProducer(sup.c.producer());
                            supx.b.setProducer(op2x.b);
                            supx.c.setProducer(op2xx.b);
                            op2x.a.setProducer(ctr.a);
                            op2xx.a.setProducer(new AConstructor(ctr.name, 0).a);
                        } else {
                            reject(left, right);
                        }
                    } else {
                        reject(left, right);
                    }
                }
                case ASuperposition sup -> {
                    final var op2x = new AStrictOp2(op2.op);
                    final var op2xx = new AStrictOp2(op2.op);
                    final var supx = sup; // reuse
                    final var dup = new ADuplicator(sup.label);
                    op2.b.forward(supx.a);
                    dup.a.setProducer(op2.c.producer());
                    op2x.a.setProducer(sup.b.producer());
                    op2xx.a.setProducer(sup.c.producer());
                    supx.b.setProducer(op2x.b);
                    supx.c.setProducer(op2xx.b);
                    op2x.c.setProducer(dup.b);
                    op2xx.c.setProducer(dup.c);
                }
                default -> reject(left, right);
            }
        }

        private void interact(final AInteger i1, final AInteger i2) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                // @formatter:off
                case ADD, SUBTRACT, MULTIPLY, DIVIDE, REMAINDER, STRICT_OR, STRICT_AND, STRICT_XOR, SHIFT_LEFT, SHIFT_RIGHT -> {
                // @formatter:on
                    if (i1.ty() != i2.ty()) {
                        reject(i1, i2);
                        return;
                    }
                    final IntegerTy ty = i1.ty();
                    final long x = i1.value(), y = i2.value();
                    final var r = new AInteger(switch (op2.op) {
                        case ADD -> ty.add(x, y);
                        case SUBTRACT -> ty.subtract(x, y);
                        case MULTIPLY -> ty.multiply(x, y);
                        case DIVIDE -> ty.divide(x, y);
                        case REMAINDER -> ty.remainder(x, y);
                        case STRICT_OR -> ty.or(x, y);
                        case STRICT_AND -> ty.and(x, y);
                        case STRICT_XOR -> ty.xor(x, y);
                        case SHIFT_LEFT -> ty.shiftLeft(x, y);
                        case SHIFT_RIGHT -> ty.shiftRight(x, y);
                        default -> crash("Unknown operation");
                    });
                    op2.b.forward(r.a);
                }
                case EQUALS, NOT_EQUALS, LESS, LESS_OR_EQUALS, GREATER, GREATER_OR_EQUALS -> {
                    if (i1.ty() != i2.ty()) {
                        reject(i1, i2);
                        return;
                    }
                    final IntegerTy ty = i1.ty();
                    final long x = i1.value(), y = i2.value();
                    final boolean answer = switch (op2.op) {
                        case EQUALS -> x == y;
                        case NOT_EQUALS -> x != y;
                        case LESS -> ty.compare(x, y) < 0;
                        case LESS_OR_EQUALS -> ty.compare(x, y) <= 0;
                        case GREATER -> ty.compare(x, y) > 0;
                        case GREATER_OR_EQUALS -> ty.compare(x, y) >= 0;
                        default -> crash("Unknown operation");
                    };
                    if (answer) {
                        op2.b.forward(new ATrue().a);
                    } else {
                        op2.b.forward(new AFalse().a);
                    }
                }
                case MIN -> {
                    if (i1.ty() != i2.ty()) {
                        reject(i1, i2);
                    } else if (i1.ty().compare(i1.value(), i2.value()) <= 0) {
                        op2.b.forward(i1.a);
                    } else {
                        op2.b.forward(i2.a);
                    }
                }
                case MAX -> {
                    if (i1.ty() != i2.ty()) {
                        reject(i1, i2);
                    } else if (i1.ty().compare(i1.value(), i2.value()) >= 0) {
                        op2.b.forward(i1.a);
                    } else {
                        op2.b.forward(i2.a);
                    }
                }
                case OFTYPE -> {
                    if (i1.ty() == i2.ty()) {
                        op2.b.forward(i2.a);
                    } else {
                        op2.b.forward(new AInteger(i2.data.convertTo(i1.ty())).a);
                    }
                }
                case HSEARCH -> {
                    hsearch(i1, i2);
                }
                default -> {
                    reject(i1, i2);
                }
            }
        }

        private void interact(final AInteger i1, final ABigInteger i2) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                case OFTYPE -> {
                    op2.b.forward(new AInteger(i2.data.toCheckedInteger(i1.ty())).a);
                }
                case HSEARCH -> {
                    hsearch(i1, i2);
                }
                default -> {
                    reject(i1, i2);
                }
            }
        }

        private void interact(final ABigInteger i1, final AInteger i2) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                case OFTYPE -> {
                    op2.b.forward(new ABigInteger(MyBigInteger.of(i2.data)).a);
                }
                case HSEARCH -> {
                    hsearch(i1, i2);
                }
                default -> {
                    reject(i1, i2);
                }
            }
        }

        private void interact(final ABigInteger i1, final ABigInteger i2) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                // @formatter:off
                case ADD, SUBTRACT, MULTIPLY, DIVIDE, REMAINDER, STRICT_OR, STRICT_AND, STRICT_XOR, SHIFT_LEFT, SHIFT_RIGHT -> {
                // @formatter:on
                    final MyBigInteger x = i1.data, y = i2.data;
                    final var r = new ABigInteger(switch (op2.op) {
                        case ADD -> x.add(y);
                        case SUBTRACT -> x.subtract(y);
                        case MULTIPLY -> x.multiply(y);
                        case DIVIDE -> x.divide(y);
                        case REMAINDER -> x.remainder(y);
                        case STRICT_OR -> x.or(y);
                        case STRICT_AND -> x.and(y);
                        case STRICT_XOR -> x.xor(y);
                        case SHIFT_LEFT -> x.shiftLeft(y);
                        case SHIFT_RIGHT -> x.shiftRight(y);
                        default -> crash("Unknown operation");
                    });
                    op2.b.forward(r.a);
                }
                case EQUALS, NOT_EQUALS, LESS, LESS_OR_EQUALS, GREATER, GREATER_OR_EQUALS -> {
                    final MyBigInteger x = i1.data, y = i2.data;
                    final boolean answer = switch (op2.op) {
                        case EQUALS -> x.equals(y);
                        case NOT_EQUALS -> !x.equals(y);
                        case LESS -> x.compareTo(y) < 0;
                        case LESS_OR_EQUALS -> x.compareTo(y) <= 0;
                        case GREATER -> x.compareTo(y) > 0;
                        case GREATER_OR_EQUALS -> x.compareTo(y) >= 0;
                        default -> crash("Unknown operation");
                    };
                    if (answer) {
                        op2.b.forward(new ATrue().a);
                    } else {
                        op2.b.forward(new AFalse().a);
                    }
                }
                case MIN -> {
                    if (i1.data.compareTo(i2.data) <= 0) {
                        op2.b.forward(i1.a);
                    } else {
                        op2.b.forward(i2.a);
                    }
                }
                case MAX -> {
                    if (i1.data.compareTo(i2.data) >= 0) {
                        op2.b.forward(i1.a);
                    } else {
                        op2.b.forward(i2.a);
                    }
                }
                case OFTYPE -> {
                    op2.b.forward(i2.a);
                }
                case HSEARCH -> {
                    hsearch(i1, i2);
                }
                default -> {
                    reject(i1, i2);
                }
            }
        }

        private void interact(final AString s1, final AInteger i) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                case CHARACTER_AT -> {
                    if (i.ty() != U64) {
                        reject(s1, i);
                        return;
                    }
                    CheckedInteger.Value c;
                    try {
                        c = IntegerTy.U8.of(s1.data.at(i.data.toInt()));
                    } catch (final IndexOutOfBoundsException _) {
                        c = panic("Index out of bounds: %s", op2.op.describe());
                    }
                    op2.b.forward(new AInteger(c).a);
                }
                case STRCHR -> {
                    if (i.ty() != U8) {
                        reject(s1, i);
                        return;
                    }
                    op2.b.forward(new AInteger(IntegerTy.I64.of(s1.data.strchr(i.data.toInt()))).a);
                }
                case STRRCHR -> {
                    if (i.ty() != U8) {
                        reject(s1, i);
                        return;
                    }
                    op2.b.forward(
                            new AInteger(IntegerTy.I64.of(s1.data.strrchr(i.data.toInt()))).a);
                }
                case HSEARCH -> {
                    hsearch(s1, i);
                }
                default -> {
                    reject(s1, i);
                }
            }
        }

        private void interact(final AString s1, final AString s2) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                case EQUALS, NOT_EQUALS, LESS, LESS_OR_EQUALS, GREATER, GREATER_OR_EQUALS -> {
                    final MyString x = s1.data, y = s2.data;
                    final boolean answer = switch (op2.op) {
                        case EQUALS -> x.equals(y);
                        case NOT_EQUALS -> !x.equals(y);
                        case LESS -> x.compareTo(y) < 0;
                        case LESS_OR_EQUALS -> x.compareTo(y) <= 0;
                        case GREATER -> x.compareTo(y) > 0;
                        case GREATER_OR_EQUALS -> x.compareTo(y) >= 0;
                        default -> crash("Unknown operation");
                    };
                    if (answer) {
                        op2.b.forward(new ATrue().a);
                    } else {
                        op2.b.forward(new AFalse().a);
                    }
                }
                case MIN -> {
                    if (s1.data.compareTo(s2.data) <= 0) {
                        op2.b.forward(s1.a);
                    } else {
                        op2.b.forward(s2.a);
                    }
                }
                case MAX -> {
                    if (s1.data.compareTo(s2.data) >= 0) {
                        op2.b.forward(s1.a);
                    } else {
                        op2.b.forward(s2.a);
                    }
                }
                case PLUS_PLUS -> {
                    op2.b.forward(new AString(s1.data.concat(s2.data)).a);
                }
                case STRCMP -> {
                    op2.b.forward(new AInteger(IntegerTy.I64.of(s1.data.compareTo(s2.data))).a);
                }
                case STRSTR -> {
                    op2.b.forward(new AInteger(IntegerTy.I64.of(s1.data.strstr(s2.data))).a);
                }
                case STRSPN -> {
                    op2.b.forward(new AInteger(IntegerTy.I64.of(s1.data.strspn(s2.data))).a);
                }
                case STRCSPN -> {
                    op2.b.forward(new AInteger(IntegerTy.I64.of(s1.data.strcspn(s2.data))).a);
                }
                case STRPBRK -> {
                    op2.b.forward(new AInteger(IntegerTy.I64.of(s1.data.strpbrk(s2.data))).a);
                }
                case STARTSWITH -> {
                    final boolean answer = s1.data.startswith(s2.data);
                    if (answer) {
                        op2.b.forward(new ATrue().a);
                    } else {
                        op2.b.forward(new AFalse().a);
                    }
                }
                case ENDSWITH -> {
                    final boolean answer = s1.data.endswith(s2.data);
                    if (answer) {
                        op2.b.forward(new ATrue().a);
                    } else {
                        op2.b.forward(new AFalse().a);
                    }
                }
                case HSEARCH -> {
                    hsearch(s1, s2);
                }
                default -> {
                    reject(s1, s2);
                }
            }
        }

        private void interact(final AString s1, final ARange rng) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                case SLICE -> {
                    op2.b.forward(s1.slice(rng.start, rng.end, rng.inclusive).a);
                }
                default -> {
                    reject(s1, rng);
                }
            }
        }

        private void interact(final AString s1, final ARangeFrom rng) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                case SLICE -> {
                    final boolean inclusive = false;
                    op2.b.forward(s1.slice(rng.start, s1.data.length(), inclusive).a);
                }
                default -> {
                    reject(s1, rng);
                }
            }
        }

        private void interact(final AString s1, final ARangeTo rng) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                case SLICE -> {
                    op2.b.forward(s1.slice(0, rng.end, rng.inclusive).a);
                }
                default -> {
                    reject(s1, rng);
                }
            }
        }

        private void interact(final AString s1, final ARangeFull rng) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                case SLICE -> {
                    op2.b.forward(s1.a);
                }
                default -> {
                    reject(s1, rng);
                }
            }
        }

        private void hsearch(final Agent left, final Agent right) {
            final AStrictOp2 op2 = this;
            assert isHsearchData(left);
            assert isHsearchData(right);
            final var key = hsearchData(left);
            final var value = hsearchData(right);
            final var data = HSEARCH_TABLE.putIfAbsent(key, value);
            if (data == null) {
                op2.b.forward(new ANull().a);
            } else {
                op2.b.forward(hsearchAgent(data));
            }
        }

        private void reject(final Agent left, final Agent right) {
            final AStrictOp2 op2 = this;
            if (isMachineData(left)) {
                crash("First operand not welcome: %s", describe(left));
            } else if (isMachineData(right) && !(right instanceof ASuperposition)) {
                crash("Second operand not welcome: %s", describe(right));
            } else if (left instanceof ANull) {
                panic("Null first operand: %s", op2.op.describe());
            } else if (right instanceof ANull) {
                panic("Null second operand: %s", op2.op.describe());
            } else if (isUserData(left) || isUserData(right)) {
                typeError(op2.op.describe(), left, right);
            } else if (isOperator(left)) {
                crash("First operand unresolved: %s", describe(left));
            } else if (isOperator(right)) {
                crash("Second operand unresolved: %s", describe(right));
            } else {
                throw new IllegalStateException();
            }
        }
    }

    public static final class AIfThenElse implements Agent {
        public final Consumer a;
        public final Producer b;
        public final Consumer c;
        public final Consumer d;

        public AIfThenElse() {
            this.a = new Consumer(null);
            this.b = new Producer(this);
            this.c = new Consumer(null);
            this.d = new Consumer(null);
        }

        private void interact() {
            final AIfThenElse ite = this;
            final Agent data = ite.a.chase();
            switch (data) {
                case ATrue _ -> ite.b.forward(ite.d.producer());
                case AFalse _ -> ite.b.forward(ite.c.producer());
                case ASuperposition sup -> {
                    final var itex = new AIfThenElse();
                    final var itexx = new AIfThenElse();
                    final var supx = sup; // reuse
                    final var dup = new ADuplicator(sup.label);
                    final var dupx = new ADuplicator(sup.label);
                    ite.b.forward(supx.a);
                    dup.a.setProducer(ite.c.producer());
                    dupx.a.setProducer(ite.d.producer());
                    itex.a.setProducer(sup.b.producer());
                    itexx.a.setProducer(sup.c.producer());
                    supx.b.setProducer(itex.b);
                    supx.c.setProducer(itexx.b);
                    itex.c.setProducer(dup.b);
                    itexx.c.setProducer(dup.c);
                    itex.d.setProducer(dupx.b);
                    itexx.d.setProducer(dupx.c);
                }
                default -> {
                    if (isMachineData(data)) {
                        crash("Operand not welcome: %s", describe(data));
                    } else if (data instanceof ANull) {
                        panic("Null operand: %s", describe(ite));
                    } else if (isUserData(data)) {
                        typeError(describe(ite), data);
                    } else if (isOperator(data)) {
                        crash("Operand unresolved: %s", describe(data));
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }
        }
    }

    public static final class ANot implements Agent {
        public final Consumer a;
        public final Producer b;

        public ANot() {
            this.a = new Consumer(null);
            this.b = new Producer(this);
        }

        private void interact() {
            final ANot not = this;
            final Agent data = not.a.chase();
            switch (data) {
                case ATrue _ -> not.b.forward(new AFalse().a);
                case AFalse _ -> not.b.forward(new ATrue().a);
                case AInteger i -> not.b.forward(new AInteger(i.data.not()).a);
                case ABigInteger i -> not.b.forward(new ABigInteger(i.data.not()).a);
                case ASuperposition sup -> {
                    final var notx = new ANot();
                    final var notxx = new ANot();
                    final var supx = sup; // reuse
                    not.b.forward(supx.a);
                    notx.a.setProducer(sup.b.producer());
                    notxx.a.setProducer(sup.c.producer());
                    supx.b.setProducer(notx.b);
                    supx.c.setProducer(notxx.b);
                }
                default -> {
                    if (isMachineData(data)) {
                        crash("Operand not welcome: %s", describe(data));
                    } else if (data instanceof ANull) {
                        panic("Null operand: %s", describe(not));
                    } else if (isUserData(data)) {
                        typeError(describe(not), data);
                    } else if (isOperator(data)) {
                        crash("Operand unresolved: %s", describe(data));
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }
        }
    }

    public static final class AAnd implements Agent {
        public final Consumer a;
        public final Producer b;
        public final Consumer c;

        public AAnd() {
            this.a = new Consumer(null);
            this.b = new Producer(this);
            this.c = new Consumer(null);
        }

        private void interact() {
            final AAnd and = this;
            final Agent data = and.a.chase();
            switch (data) {
                case ATrue _ -> and.b.forward(and.c.producer());
                case AFalse b -> and.b.forward(b.a);
                case ASuperposition sup -> {
                    final var andx = new AAnd();
                    final var andxx = new AAnd();
                    final var supx = sup; // reuse
                    final var dup = new ADuplicator(sup.label);
                    and.b.forward(supx.a);
                    dup.a.setProducer(and.c.producer());
                    andx.a.setProducer(sup.b.producer());
                    andxx.a.setProducer(sup.c.producer());
                    supx.b.setProducer(andx.b);
                    supx.c.setProducer(andxx.b);
                    andx.c.setProducer(dup.b);
                    andxx.c.setProducer(dup.c);
                }
                default -> {
                    if (isMachineData(data)) {
                        crash("Operand not welcome: %s", describe(data));
                    } else if (data instanceof ANull) {
                        panic("Null operand: %s", describe(and));
                    } else if (isUserData(data)) {
                        typeError(describe(and), data);
                    } else if (isOperator(data)) {
                        crash("Operand unresolved: %s", describe(data));
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }
        }
    }

    public static final class AOr implements Agent {
        public final Consumer a;
        public final Producer b;
        public final Consumer c;

        public AOr() {
            this.a = new Consumer(null);
            this.b = new Producer(this);
            this.c = new Consumer(null);
        }

        private void interact() {
            final AOr or = this;
            final Agent data = or.a.chase();
            switch (data) {
                case ATrue b -> or.b.forward(b.a);
                case AFalse _ -> or.b.forward(or.c.producer());
                case ASuperposition sup -> {
                    final var orx = new AOr();
                    final var orxx = new AOr();
                    final var supx = sup; // reuse
                    final var dup = new ADuplicator(sup.label);
                    or.b.forward(supx.a);
                    dup.a.setProducer(or.c.producer());
                    orx.a.setProducer(sup.b.producer());
                    orxx.a.setProducer(sup.c.producer());
                    supx.b.setProducer(orx.b);
                    supx.c.setProducer(orxx.b);
                    orx.c.setProducer(dup.b);
                    orxx.c.setProducer(dup.c);
                }
                default -> {
                    if (isMachineData(data)) {
                        crash("Operand not welcome: %s", describe(data));
                    } else if (data instanceof ANull) {
                        panic("Null operand: %s", describe(or));
                    } else if (isUserData(data)) {
                        typeError(describe(or), data);
                    } else if (isOperator(data)) {
                        crash("Operand unresolved: %s", describe(data));
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }
        }
    }

    public static final class ADoRange implements Agent {
        public final Consumer a;
        public final Producer b;
        public final Consumer c;
        public final boolean inclusive;

        public ADoRange(final boolean inclusive) {
            this.a = new Consumer(null);
            this.b = new Producer(this);
            this.c = new Consumer(null);
            this.inclusive = inclusive;
        }

        private void interact() {
            final ADoRange doRng = this;
            final Agent left = doRng.a.chase(), right = doRng.c.chase();
            switch (new Operands(left, right)) {
                case Operands(AInteger i1, AInteger i2) when i1.ty() == U64 && i2.ty() == U64 -> {
                    final var rng = new ARange(i1.value(), i2.value(), doRng.inclusive);
                    doRng.b.forward(rng.a);
                }
                case Operands(ASuperposition sup, _) -> {
                    final var doRngx = new ADoRange(doRng.inclusive);
                    final var doRngxx = new ADoRange(doRng.inclusive);
                    final var supx = sup; // reuse
                    final var dup = new ADuplicator(sup.label);
                    doRng.b.forward(supx.a);
                    dup.a.setProducer(doRng.c.producer());
                    doRngx.a.setProducer(sup.b.producer());
                    doRngxx.a.setProducer(sup.c.producer());
                    supx.b.setProducer(doRngx.b);
                    supx.c.setProducer(doRngxx.b);
                    doRngx.c.setProducer(dup.b);
                    doRngxx.c.setProducer(dup.c);
                }
                case Operands(AInteger i, ASuperposition sup) -> {
                    final var doRngx = new ADoRange(doRng.inclusive);
                    final var doRngxx = new ADoRange(doRng.inclusive);
                    final var supx = sup; // reuse
                    doRng.b.forward(supx.a);
                    doRngx.c.setProducer(sup.b.producer());
                    doRngxx.c.setProducer(sup.c.producer());
                    supx.b.setProducer(doRngx.b);
                    supx.c.setProducer(doRngxx.b);
                    doRngx.a.setProducer(i.a);
                    doRngxx.a.setProducer(new AInteger(i.data).a);
                }
                default -> {
                    if (isMachineData(left)) {
                        crash("First operand not welcome: %s", describe(left));
                    } else if (isMachineData(right)) {
                        crash("Second operand not welcome: %s", describe(right));
                    } else if (left instanceof ANull) {
                        panic("Null first operand: %s", describe(doRng));
                    } else if (right instanceof ANull) {
                        panic("Null second operand: %s", describe(doRng));
                    } else if (isUserData(left) || isUserData(right)) {
                        typeError(describe(doRng), left, right);
                    } else if (isOperator(left)) {
                        crash("First operand unresolved: %s", describe(left));
                    } else if (isOperator(right)) {
                        crash("Second operand unresolved: %s", describe(right));
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }
        }
    }

    public static final class ADoRangeFrom implements Agent {
        public final Consumer a;
        public final Producer b;

        public ADoRangeFrom() {
            this.a = new Consumer(null);
            this.b = new Producer(this);
        }

        private void interact() {
            final ADoRangeFrom doRng = this;
            final Agent data = doRng.a.chase();
            switch (data) {
                case AInteger i when i.ty() == U64 -> {
                    final var rng = new ARangeFrom(i.value());
                    doRng.b.forward(rng.a);
                }
                case ASuperposition sup -> {
                    final var doRngx = new ADoRangeFrom();
                    final var doRngxx = new ADoRangeFrom();
                    final var supx = sup; // reuse
                    doRng.b.forward(supx.a);
                    doRngx.a.setProducer(sup.b.producer());
                    doRngxx.a.setProducer(sup.c.producer());
                    supx.b.setProducer(doRngx.b);
                    supx.c.setProducer(doRngxx.b);
                }
                default -> {
                    if (isMachineData(data)) {
                        crash("Operand not welcome: %s", describe(data));
                    } else if (data instanceof ANull) {
                        panic("Null operand: %s", describe(doRng));
                    } else if (isUserData(data)) {
                        typeError(describe(doRng), data);
                    } else if (isOperator(data)) {
                        crash("Operand unresolved: %s", describe(data));
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }
        }
    }

    public static final class ADoRangeTo implements Agent {
        public final Consumer a;
        public final Producer b;
        public final boolean inclusive;

        public ADoRangeTo(final boolean inclusive) {
            this.a = new Consumer(null);
            this.b = new Producer(this);
            this.inclusive = inclusive;
        }

        private void interact() {
            final ADoRangeTo doRng = this;
            final Agent data = doRng.a.chase();
            switch (data) {
                case AInteger i when i.ty() == U64 -> {
                    final var rng = new ARangeTo(i.value(), doRng.inclusive);
                    doRng.b.forward(rng.a);
                }
                case ASuperposition sup -> {
                    final var doRngx = new ADoRangeTo(doRng.inclusive);
                    final var doRngxx = new ADoRangeTo(doRng.inclusive);
                    final var supx = sup; // reuse
                    doRng.b.forward(supx.a);
                    doRngx.a.setProducer(sup.b.producer());
                    doRngxx.a.setProducer(sup.c.producer());
                    supx.b.setProducer(doRngx.b);
                    supx.c.setProducer(doRngxx.b);
                }
                default -> {
                    if (isMachineData(data)) {
                        crash("Operand not welcome: %s", describe(data));
                    } else if (data instanceof ANull) {
                        panic("Null operand: %s", describe(doRng));
                    } else if (isUserData(data)) {
                        typeError(describe(doRng), data);
                    } else if (isOperator(data)) {
                        crash("Operand unresolved: %s", describe(data));
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }
        }
    }

    public static final class AApplicator implements Agent {
        public final Consumer a;
        public final Producer b;
        public final Consumer c;

        public AApplicator() {
            this.a = new Consumer(null);
            this.b = new Producer(this);
            this.c = new Consumer(null);
        }

        private void interact() {
            final AApplicator app = this;
            final Agent data = app.a.chase();
            switch (data) {
                case ALambda lam -> {
                    app.b.forward(lam.c.producer());
                    lam.b.forward(app.c.producer());
                }
                case AIdentity _ -> {
                    app.b.forward(app.c.producer());
                }
                case ASuperposition sup -> {
                    final var appx = new AApplicator();
                    final var appxx = new AApplicator();
                    final var supx = sup; // reuse
                    final var dup = new ADuplicator(sup.label);
                    app.b.forward(supx.a);
                    dup.a.setProducer(app.c.producer());
                    appx.a.setProducer(sup.b.producer());
                    appxx.a.setProducer(sup.c.producer());
                    supx.b.setProducer(appx.b);
                    supx.c.setProducer(appxx.b);
                    appx.c.setProducer(dup.b);
                    appxx.c.setProducer(dup.c);
                }
                default -> {
                    if (isMachineData(data)) {
                        crash("Operand not welcome: %s", describe(data));
                    } else if (data instanceof ANull) {
                        panic("Null operand: %s", describe(app));
                    } else if (isUserData(data)) {
                        typeError(describe(app), data);
                    } else if (isOperator(data)) {
                        crash("Operand unresolved: %s", describe(data));
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }
        }
    }

    public static final class AStrictApplicator implements Agent {
        public final Consumer a;
        public final Producer b;
        public final Consumer c;

        public AStrictApplicator() {
            this.a = new Consumer(null);
            this.b = new Producer(this);
            this.c = new Consumer(null);
        }

        private void interact() {
            final AStrictApplicator sapp = this;
            final Agent data = sapp.a.chase();
            switch (data) {
                case ALambda lam -> {
                    sapp.b.forward(lam.c.producer());
                    lam.b.forward(sapp.c.producer());
                }
                case AIdentity _ -> {
                    sapp.b.forward(sapp.c.producer());
                }
                case ASuperposition sup -> {
                    final var sappx = new AStrictApplicator();
                    final var sappxx = new AStrictApplicator();
                    final var supx = sup; // reuse
                    final var dup = new ADuplicator(sup.label);
                    sapp.b.forward(supx.a);
                    dup.a.setProducer(sapp.c.producer());
                    sappx.a.setProducer(sup.b.producer());
                    sappxx.a.setProducer(sup.c.producer());
                    supx.b.setProducer(sappx.b);
                    supx.c.setProducer(sappxx.b);
                    sappx.c.setProducer(dup.b);
                    sappxx.c.setProducer(dup.c);
                }
                default -> {
                    if (isMachineData(data)) {
                        crash("Operand not welcome: %s", describe(data));
                    } else if (data instanceof ANull) {
                        panic("Null operand: %s", describe(sapp));
                    } else if (isUserData(data)) {
                        typeError(describe(sapp), data);
                    } else if (isOperator(data)) {
                        crash("Operand unresolved: %s", describe(data));
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }
        }
    }

    public static final class AResolver implements Agent {
        public final Consumer a;
        public final Producer b;
        public final Producer c;
        public final Consumer d;

        public AResolver() {
            this.a = new Consumer(null);
            this.b = new Producer(this);
            this.c = new Producer(this);
            this.d = new Consumer(null);
        }

        private void interact() {
            final AResolver res = this;
            final Agent data = res.a.chase();
            switch (data) {
                case AEndOfList _ -> {
                    final var lam = new ALambda();
                    res.b.forward(lam.a);
                    res.c.forward(lam.b);
                    lam.c.setProducer(res.d.producer());
                }
                case ASuperposition sup -> {
                    final var resx = new AResolver();
                    final var resxx = new AResolver();
                    final var supx = sup; // reuse
                    final var supxx = new ASuperposition(sup.label);
                    final var dup = new ADuplicator(sup.label);
                    res.b.forward(supx.a);
                    res.c.forward(supxx.a);
                    dup.a.setProducer(res.d.producer());
                    resx.a.setProducer(sup.b.producer());
                    resxx.a.setProducer(sup.c.producer());
                    supx.b.setProducer(resx.b);
                    supx.c.setProducer(resxx.b);
                    supxx.b.setProducer(resx.c);
                    supxx.c.setProducer(resxx.c);
                    resx.d.setProducer(dup.b);
                    resxx.d.setProducer(dup.c);
                }
                default -> {
                    if (isOperator(data)) {
                        crash("Operand unresolved: %s", describe(data));
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }
        }
    }

    public static final class ACapture implements Agent {
        public final Consumer a;
        public final Producer b;
        public final Producer c;
        public final Consumer d;

        public ACapture() {
            this.a = new Consumer(null);
            this.b = new Producer(this);
            this.c = new Producer(this);
            this.d = new Consumer(null);
        }

        private void interact() {
            final ACapture cap = this;
            final Agent data = cap.a.chase();
            switch (data) {
                case ALambda lam -> {
                    cap.c.forward(lam.a);
                    cap.b.forward(cap.d.producer());
                }
                case ANull mynull -> {
                    cap.c.forward(mynull.a);
                    cap.b.forward(cap.d.producer());
                }
                case ATrue b -> {
                    cap.c.forward(b.a);
                    cap.b.forward(cap.d.producer());
                }
                case AFalse b -> {
                    cap.c.forward(b.a);
                    cap.b.forward(cap.d.producer());
                }
                case AInteger i -> {
                    cap.c.forward(i.a);
                    cap.b.forward(cap.d.producer());
                }
                case ABigInteger i -> {
                    cap.c.forward(i.a);
                    cap.b.forward(cap.d.producer());
                }
                case AString s -> {
                    cap.c.forward(s.a);
                    cap.b.forward(cap.d.producer());
                }
                case AIdentity id -> {
                    cap.c.forward(id.a);
                    cap.b.forward(cap.d.producer());
                }
                case AConstructor ctr -> {
                    cap.c.forward(ctr.a);
                    cap.b.forward(cap.d.producer());
                }
                case ASuperposition sup -> {
                    final var capx = new ACapture();
                    final var capxx = new ACapture();
                    final var supx = sup; // reuse
                    final var supxx = new ASuperposition(sup.label);
                    final var dup = new ADuplicator(sup.label);
                    cap.b.forward(supx.a);
                    cap.c.forward(supxx.a);
                    dup.a.setProducer(cap.d.producer());
                    capx.a.setProducer(sup.b.producer());
                    capxx.a.setProducer(sup.c.producer());
                    supx.b.setProducer(capx.b);
                    supx.c.setProducer(capxx.b);
                    supxx.b.setProducer(capx.c);
                    supxx.c.setProducer(capxx.c);
                    capx.d.setProducer(dup.b);
                    capxx.d.setProducer(dup.c);
                }
                default -> {
                    if (isOperator(data)) {
                        crash("Operand unresolved: %s", describe(data));
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }
        }
    }

    public static final class AFix implements Agent {
        public final Consumer a;
        public final Producer b;

        public AFix() {
            this.a = new Consumer(null);
            this.b = new Producer(this);
        }

        private void interact() {
            final AFix fix = this;
            final Agent data = fix.a.chase();
            switch (data) {
                case ALambda lam -> {
                    final var fixx = new AFix();
                    final var app = new AApplicator();
                    final var appx = new AApplicator();
                    final var res = new AResolver();
                    final var cap = new ACapture();
                    final var dup = new ADuplicator(Label.COPY);
                    fix.b.forward(app.b);
                    app.a.setProducer(dup.b);
                    app.c.setProducer(res.b);
                    cap.a.setProducer(dup.c);
                    res.a.setProducer(cap.b);
                    fixx.a.setProducer(cap.c);
                    cap.d.setProducer(new AEndOfList().a);
                    appx.a.setProducer(fixx.b);
                    res.d.setProducer(appx.b);
                    appx.c.setProducer(res.c);
                    dup.a.setProducer(lam.a);
                }
                case ASuperposition sup -> {
                    final var fixx = new AFix();
                    final var fixxx = new AFix();
                    final var supx = sup; // reuse
                    fix.b.forward(supx.a);
                    fixx.a.setProducer(sup.b.producer());
                    fixxx.a.setProducer(sup.c.producer());
                    supx.b.setProducer(fixx.b);
                    supx.c.setProducer(fixxx.b);
                }
                default -> {
                    if (isMachineData(data)) {
                        crash("Operand not welcome: %s", describe(data));
                    } else if (data instanceof ANull) {
                        panic("Null operand: %s", describe(fix));
                    } else if (isUserData(data)) {
                        typeError(describe(fix), data);
                    } else if (isOperator(data)) {
                        crash("Operand unresolved: %s", describe(data));
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }
        }
    }

    public static final class AMatch implements Agent {
        public final String[] names;
        public final Consumer a;
        public final Producer b;
        public final Consumer[] handlers;

        public AMatch(final String[] names) {
            this.names = names;
            this.a = new Consumer(null);
            this.b = new Producer(this);
            this.handlers = new Consumer[names.length];
            for (int i = 0; i < names.length; i++) {
                handlers[i] = new Consumer(null);
            }
        }

        private void interact() {
            final AMatch match = this;
            final Agent data = match.a.chase();
            switch (data) {
                case AConstructor ctr -> {
                    int index = -1;
                    for (int i = 0; i < match.names.length; i++) {
                        if (match.names[i].equals(ctr.name)) {
                            index = i;
                            break;
                        }
                    }
                    if (index == -1) {
                        panic("No matching case for the constructor `%s`", ctr.name);
                    }
                    Producer result = match.handlers[index].producer();
                    for (final Consumer argument : ctr.arguments) {
                        final var app = new AApplicator();
                        app.a.setProducer(result);
                        app.c.setProducer(argument.producer());
                        result = app.b;
                    }
                    match.b.forward(result);
                }
                case ASuperposition sup -> {
                    final var matchx = new AMatch(match.names);
                    final var matchxx = new AMatch(match.names);
                    final var supx = sup; // reuse
                    match.b.forward(supx.a);
                    matchx.a.setProducer(sup.b.producer());
                    matchxx.a.setProducer(sup.c.producer());
                    supx.b.setProducer(matchx.b);
                    supx.c.setProducer(matchxx.b);
                    for (int i = 0; i < match.names.length; i++) {
                        final var dup = new ADuplicator(sup.label);
                        dup.a.setProducer(match.handlers[i].producer());
                        matchx.handlers[i].setProducer(dup.b);
                        matchxx.handlers[i].setProducer(dup.c);
                    }
                }
                default -> {
                    if (isMachineData(data)) {
                        crash("Operand not welcome: %s", describe(data));
                    } else if (data instanceof ANull) {
                        panic("Null operand: %s", describe(match));
                    } else if (isUserData(data)) {
                        typeError(describe(match), data);
                    } else if (isOperator(data)) {
                        crash("Operand unresolved: %s", describe(data));
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }
        }
    }

    public static final class ADuplicator implements Agent {
        private final AtomicReference<CompletableFuture<Duplicand>> sync = new AtomicReference<>();
        public final Label label;
        public final Consumer a;
        public final Producer b;
        public final Producer c;

        public ADuplicator(final Label label) {
            this.label = label;
            this.a = new Consumer(null);
            this.b = new Producer(this);
            this.c = new Producer(this);
        }

        private Duplicand interact() {
            final ADuplicator dup = this;
            final Agent data = dup.a.chase();
            return switch (data) {
                case AConstructor ctr -> {
                    final var ctrx = ctr; // reuse
                    final var ctrxx = new AConstructor(ctr.name, ctr.arity());
                    for (int i = 0; i < ctr.arity(); i++) {
                        final var dupx = new ADuplicator(dup.label);
                        dupx.a.setProducer(ctr.arguments[i].producer());
                        ctrx.arguments[i].setProducer(dupx.b);
                        ctrxx.arguments[i].setProducer(dupx.c);
                    }
                    yield new Commute(ctrx.a, ctrxx.a);
                }
                case ASuperposition sup when dup.label == sup.label -> {
                    yield new Annihilate(sup.b, sup.c);
                }
                case ASuperposition sup -> {
                    final var supx = sup; // reuse
                    final var supxx = new ASuperposition(sup.label);
                    final var dupx = new ADuplicator(dup.label);
                    final var dupxx = new ADuplicator(dup.label);
                    dupx.a.setProducer(sup.b.producer());
                    dupxx.a.setProducer(sup.c.producer());
                    supx.b.setProducer(dupx.b);
                    supxx.b.setProducer(dupx.c);
                    supx.c.setProducer(dupxx.b);
                    supxx.c.setProducer(dupxx.c);
                    yield new Commute(supx.a, supxx.a);
                }
                case ALambda lam -> {
                    final var lamx = new ALambda();
                    final var lamxx = new ALambda();
                    final var sup = new ASuperposition(Label.DELTA);
                    final var dupx = new ADuplicator(Label.DELTA);
                    lam.b.forward(sup.a);
                    dupx.a.setProducer(lam.c.producer());
                    sup.b.setProducer(lamx.b);
                    sup.c.setProducer(lamxx.b);
                    lamx.c.setProducer(dupx.b);
                    lamxx.c.setProducer(dupx.c);
                    yield new Commute(lamx.a, lamxx.a);
                }
                case AEndOfList end -> new Commute(end.a, new AEndOfList().a);
                case ANull mynull -> new Commute(mynull.a, new ANull().a);
                case ATrue b -> new Commute(b.a, new ATrue().a);
                case AFalse b -> new Commute(b.a, new AFalse().a);
                case AInteger i -> new Commute(i.a, new AInteger(i.data).a);
                case ABigInteger i -> new Commute(i.a, new ABigInteger(i.data).a);
                case AString s -> new Commute(s.a, new AString(s.data).a);
                case ARange rng ->
                    new Commute(rng.a, new ARange(rng.start, rng.end, rng.inclusive).a);
                case ARangeFrom rng -> new Commute(rng.a, new ARangeFrom(rng.start).a);
                case ARangeTo rng -> new Commute(rng.a, new ARangeTo(rng.end, rng.inclusive).a);
                case ARangeFull rng -> new Commute(rng.a, new ARangeFull().a);
                case AIdentity id -> new Commute(id.a, new AIdentity().a);
                default -> {
                    if (isOperator(data)) {
                        yield crash("Operand unresolved: %s", describe(data));
                    } else {
                        throw new IllegalStateException();
                    }
                }
            };
        }
    }

    public static final class ALambda implements Agent {
        public final Producer a;
        public final Producer b;
        public final Consumer c;

        public ALambda() {
            this.a = new Producer(this);
            this.b = new Producer(this);
            this.c = new Consumer(null);
        }
    }

    public static final class AEndOfList implements Agent {
        public final Producer a;

        public AEndOfList() {
            this.a = new Producer(this);
        }
    }

    public static final class ANull implements Agent {
        public final Producer a;

        public ANull() {
            this.a = new Producer(this);
        }
    }

    public static final class ATrue implements Agent {
        public final Producer a;

        public ATrue() {
            this.a = new Producer(this);
        }
    }

    public static final class AFalse implements Agent {
        public final Producer a;

        public AFalse() {
            this.a = new Producer(this);
        }
    }

    public static final class AInteger implements Agent {
        public final Value data;
        public final Producer a;

        public AInteger(final Value data) {
            this.data = data;
            this.a = new Producer(this);
        }

        public IntegerTy ty() {
            return data.ty();
        }

        public long value() {
            return data.a();
        }

        public static AInteger zero(final IntegerTy ty) {
            return new AInteger(ty.zero());
        }

        public static AInteger one(final IntegerTy ty) {
            return new AInteger(ty.one());
        }
    }

    public static final class ABigInteger implements Agent {
        public final MyBigInteger data;
        public final Producer a;

        public ABigInteger(final MyBigInteger data) {
            this.data = data;
            this.a = new Producer(this);
        }

        public static ABigInteger zero() {
            return new ABigInteger(MyBigInteger.zero());
        }

        public static ABigInteger one() {
            return new ABigInteger(MyBigInteger.one());
        }
    }

    public static final class AString implements Agent {
        public final MyString data;
        public final Producer a;

        public AString(final MyString data) {
            this.data = data;
            this.a = new Producer(this);
        }

        public AString(final String s) {
            this(MyString.ofAscii(s));
        }

        public AString slice(final long start, final long end, final boolean inclusive) {
            if (inclusive && end == -1L) {
                return panic("Range out of bounds: %s", SLICE.describe());
            }
            final int i, j;
            try {
                i = U64.toInt(start);
                j = U64.toInt(inclusive ? end + 1 : end);
            } catch (final CheckedInteger.OutOfRange e) {
                return panic("Out of range: %s", Primitives.describe(e.ty));
            }
            try {
                return new AString(this.data.slice(i, j));
            } catch (final IndexOutOfBoundsException _) {
                return panic("Range out of bounds: %s", SLICE.describe());
            }
        }
    }

    public static final class ARange implements Agent {
        public final long start, end;
        public final boolean inclusive;
        public final Producer a;

        public ARange(final long start, final long end, final boolean inclusive) {
            this.start = start;
            this.end = end;
            this.inclusive = inclusive;
            this.a = new Producer(this);
        }
    }

    public static final class ARangeFrom implements Agent {
        public final long start;
        public final Producer a;

        public ARangeFrom(final long start) {
            this.start = start;
            this.a = new Producer(this);
        }
    }

    public static final class ARangeTo implements Agent {
        public final long end;
        public final boolean inclusive;
        public final Producer a;

        public ARangeTo(final long end, final boolean inclusive) {
            this.end = end;
            this.inclusive = inclusive;
            this.a = new Producer(this);
        }
    }

    public static final class ARangeFull implements Agent {
        public final Producer a;

        public ARangeFull() {
            this.a = new Producer(this);
        }
    }

    public static final class AIdentity implements Agent {
        public final Producer a;

        public AIdentity() {
            this.a = new Producer(this);
        }
    }

    public static final class AReference implements Agent {
        public final String name;
        public final Producer a;

        public AReference(final String name) {
            this.name = name;
            this.a = new Producer(this);
        }
    }

    private static final class ASuperposition implements Agent {
        public final Label label;
        public final Producer a;
        public final Consumer b;
        public final Consumer c;

        private ASuperposition(final Label label) {
            this.label = label;
            this.a = new Producer(this);
            this.b = new Consumer(null);
            this.c = new Consumer(null);
        }
    }

    public static final class AConstructor implements Agent {
        public final String name;
        public final Producer a;
        public final Consumer[] arguments;

        public AConstructor(final String name, final int arity) {
            this.name = name;
            this.a = new Producer(this);
            this.arguments = new Consumer[arity];
            for (int i = 0; i < arity; i++) {
                arguments[i] = new Consumer(null);
            }
        }

        public int arity() {
            return arguments.length;
        }

        public boolean isNullary() {
            return arguments.length == 0;
        }
    }

    public enum Label {
        COPY, DELTA
    }

    private enum Origin {
        LEFT, RIGHT
    }

    private sealed interface Duplicand permits Annihilate, Commute {
        void connect(Consumer p, Origin origin);
    }

    private record Annihilate(Consumer left, Consumer right) implements Duplicand {
        public void connect(final Consumer p, final Origin origin) {
            p.setProducer(switch (origin) {
                case LEFT -> left.producer();
                case RIGHT -> right.producer();
            });
        }
    }

    private record Commute(Producer left, Producer right) implements Duplicand {
        public void connect(final Consumer p, final Origin origin) {
            p.setProducer(switch (origin) {
                case LEFT -> left;
                case RIGHT -> right;
            });
        }
    }

    private static boolean isOperator(final Agent agent) {
        return switch (agent) {
            case AStrictOp1 _,AStrictOp2 _,AIfThenElse _,ANot _,AAnd _,AOr _,ADoRange _,ADoRangeFrom _,ADoRangeTo _,AApplicator _,AStrictApplicator _,AResolver _,ACapture _,AFix _,AMatch _,ADuplicator _ ->
                true;
            case ALambda _,AEndOfList _,ANull _,ATrue _,AFalse _,AInteger _,ABigInteger _,AString _,ARange _,ARangeFrom _,ARangeTo _,ARangeFull _,AIdentity _,AReference _,AConstructor _,ASuperposition _ ->
                false;
        };
    }

    private static boolean isUserData(final Agent agent) {
        return switch (agent) {
            case ALambda _,ANull _,ATrue _,AFalse _,AInteger _,ABigInteger _,AString _,ARange _,ARangeFrom _,ARangeTo _,ARangeFull _,AIdentity _,AConstructor _ ->
                true;
            case AStrictOp1 _,AStrictOp2 _,AIfThenElse _,ANot _,AAnd _,AOr _,ADoRange _,ADoRangeFrom _,ADoRangeTo _,AApplicator _,AStrictApplicator _,AResolver _,ACapture _,AFix _,AMatch _,ADuplicator _,AEndOfList _,AReference _,ASuperposition _ ->
                false;
        };
    }

    private static boolean isMachineData(final Agent agent) {
        return !isOperator(agent) && !isUserData(agent);
    }

    private static boolean isPrimitiveData(final Agent agent) {
        return switch (agent) {
            case ANull _,ATrue _,AFalse _,AInteger _,ABigInteger _,AString _,ARange _,ARangeFrom _,ARangeTo _,ARangeFull _ ->
                true;
            case AConstructor ctr -> ctr.isNullary();
            default -> false;
        };
    }

    private static boolean isHsearchData(final Agent agent) {
        return switch (agent) {
            case ATrue _,AFalse _,AInteger _,ABigInteger _,AString _ -> true;
            case AConstructor ctr -> ctr.isNullary();
            default -> false;
        };
    }

    private static boolean isWhnf(final Consumer p) {
        return switch (p.chase()) {
            case ALambda _,AEndOfList _,ANull _,ATrue _,AFalse _,AInteger _,ABigInteger _,AString _,ARange _,ARangeFrom _,ARangeTo _,ARangeFull _,AIdentity _,AConstructor _,ASuperposition _ ->
                true;
            case AStrictOp1 _,AStrictOp2 _,AIfThenElse _,ANot _,AAnd _,AOr _,ADoRange _,ADoRangeFrom _,ADoRangeTo _,AApplicator _,AStrictApplicator _,AResolver _,ACapture _,AFix _,AMatch _,ADuplicator _,AReference _ ->
                false;
        };
    }

    private record HsearchConstructor(String name) {
    }

    private static Object hsearchData(final Agent agent) {
        return switch (agent) {
            case ATrue _ -> Boolean.TRUE;
            case AFalse _ -> Boolean.FALSE;
            case AInteger i -> i.data;
            case ABigInteger i -> i.data;
            case AString s -> s.data;
            case AConstructor ctr when ctr.isNullary() ->
                new HsearchConstructor(ctr.name);
            default -> throw new IllegalStateException();
        };
    }

    private static Producer hsearchAgent(final Object data) {
        return switch (data) {
            case Boolean b -> b ? new ATrue().a : new AFalse().a;
            case Value v -> new AInteger(v).a;
            case MyBigInteger i -> new ABigInteger(i).a;
            case MyString s -> new AString(s).a;
            case HsearchConstructor(var name) ->
                new AConstructor(name, 0).a;
            default -> throw new IllegalStateException();
        };
    }

    private static String describe(final Agent agent) {
        return switch (agent) {
            case AStrictOp1 op1 -> op1.op.describe();
            case AStrictOp2 op2 -> op2.op.describe();
            case AIfThenElse _ -> "an if-then-else expression";
            case ANot _ -> "logical negation";
            case AAnd _ -> "logical conjunction";
            case AOr _ -> "logical disjunction";
            case ADoRange _ -> "bounded-range construction";
            case ADoRangeFrom _ -> "from-range construction";
            case ADoRangeTo _ -> "to-range construction";
            case AApplicator _ -> "an applicator";
            case AStrictApplicator _ -> "a strict applicator";
            case AResolver _ -> "a variable-list resolver";
            case ACapture _ -> "a captured variable";
            case AFix _ -> "the fix operator";
            case AMatch _ -> "a match expression";
            case ADuplicator _ -> "a duplicator";
            case ALambda _ -> "a lambda function";
            case AEndOfList _ -> "an end-of-variables marker";
            case ANull _ -> "the null value";
            case ATrue _ -> "the true value";
            case AFalse _ -> "the false value";
            case AInteger i -> Primitives.describe(i.ty());
            case ABigInteger _ -> "a big integer";
            case AString _ -> "a string";
            case ARange _ -> "a bounded range";
            case ARangeFrom _ -> "a from-range";
            case ARangeTo _ -> "a to-range";
            case ARangeFull _ -> "a full range";
            case AIdentity _ -> "an identity function";
            case AReference ref -> "a reference to `" + ref.name + "`";
            case AConstructor ctr -> "the constructor `" + ctr.name + "`";
            case ASuperposition _ -> "a superposition";
        };
    }

    private static <T> T crash(final String format, final Object... arguments) {
        throw new IllegalStateException(String.format(format, arguments));
    }

    private static <T> T panic(final String format, final Object... arguments) {
        throw new Panic(String.format(format, arguments));
    }

    private static <T> T typeError(final String op, final Agent... arguments) {
        final var message = Arrays.stream(arguments)
                .map(Motor::describe)
                .collect(Collectors.joining(", "));
        return panic("Type error: %s: %s", op, message);
    }
}
