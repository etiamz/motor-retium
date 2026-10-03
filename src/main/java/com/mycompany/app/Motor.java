package com.mycompany.app;

import static com.mycompany.app.CheckedInteger.IntegerTy.*;

import com.mycompany.app.CheckedInteger.IntegerTy;
import com.mycompany.app.CheckedInteger.Value;
import com.mycompany.app.Port.Consumer;
import com.mycompany.app.Port.Producer;
import com.mycompany.app.Primitives.StrictOp1;
import com.mycompany.app.Primitives.StrictOp2;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
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

    private static final boolean STATS = Boolean.getBoolean("motor.stats");

    private static final Producer[] NO_IMPORTS = new Producer[0];

    private static ForkJoinPool POOL;
    private static Map<String, Template> BOOK;
    private static LongAdder NINTERACTIONS;

    public static boolean statsEnabled() {
        return STATS;
    }

    public static long ninteractions() {
        if (!STATS) {
            throw new IllegalStateException(
                    "Statistics are disabled: `-Dmotor.stats=true` is not set");
        }
        return NINTERACTIONS.sum();
    }

    private static void countInteraction() {
        if (STATS) {
            NINTERACTIONS.increment();
        }
    }

    private Motor() {
    }

    public static void initialize(final Map<String, Template> book) {
        if (BOOK != null) {
            throw new IllegalStateException("The machine is already initialized");
        }
        POOL = new ForkJoinPool();
        BOOK = book;
        if (STATS) {
            NINTERACTIONS = new LongAdder();
        }
    }

    // Whether to forke a right operand or reduce it inline depends on whether the work performed by
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

    public static String whnf(final Consumer root) {
        if (BOOK == null) {
            throw new IllegalStateException("The machine is not initialized");
        }
        final Agent result = whnfAsync(root).join();
        return switch (result) {
            case AString s -> s.data.toString();
            default -> panic(
                    "Expected a string program value, got %s; use the `$show` intrinsic for display",
                    describe(result));
        };
    }

    private static CompletableFuture<Agent> whnfAsync(final Consumer root) {
        final var result = new CompletableFuture<Agent>();
        final var heart = new Heart(HEARTBEAT);
        schedule(heart, () -> reduce(root, () -> {
            result.complete(root.chase());
            return Halt.HALT;
        }, heart));
        return result;
    }

    private static void drive(Bounce bounce, final Heart heart) {
        try {
            for (;;) {
                switch (bounce) {
                    case Thunk thunk -> {
                        bounce = thunk.run();
                        beat(heart);
                    }
                    case Await(var future, var resume) when future.isDone() -> {
                        bounce = resume;
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

    private static void schedule(final Heart heart, final Thunk thunk) {
        POOL.execute(() -> drive(thunk, heart));
    }

    // Beat the heart; when the remaining fuel is zero, promote the oldest frame, if it exists. This
    // is where logical threads are spawned.
    private static void beat(final Heart heart) {
        if (--heart.fuel == 0) {
            heart.fuel = HEARTBEAT;
            final Promotable frame = heart.pollOldest();
            if (frame != null) {
                frame.future = whnfAsync(frame.right);
            }
        }
    }

    private static Bounce reduce(final Consumer p, final Thunk thunk, final Heart heart) {
        final Agent agent = p.chase();
        return switch (agent.kind) {
            case K_STRICT_OP1 -> {
                final var rator = (AStrictOp1) agent;
                yield simplex(rator.a, rator::interact, p, thunk, heart);
            }
            case K_IF_THEN_ELSE -> {
                final var rator = (AIfThenElse) agent;
                yield simplex(rator.a, rator::interact, p, thunk, heart);
            }
            case K_NOT -> {
                final var rator = (ANot) agent;
                yield simplex(rator.a, rator::interact, p, thunk, heart);
            }
            case K_AND -> {
                final var rator = (AAnd) agent;
                yield simplex(rator.a, rator::interact, p, thunk, heart);
            }
            case K_OR -> {
                final var rator = (AOr) agent;
                yield simplex(rator.a, rator::interact, p, thunk, heart);
            }
            case K_APPLICATOR -> {
                final var rator = (AApplicator) agent;
                yield simplex(rator.a, rator::interact, p, thunk, heart);
            }
            case K_STRICT_APPLICATOR -> {
                final var rator = (AStrictApplicator) agent;
                yield duplex(rator.a, rator.c, rator::interact, p, thunk, heart);
            }
            case K_RESOLVER -> {
                final var rator = (AResolver) agent;
                yield simplex(rator.a, rator::interact, p, thunk, heart);
            }
            case K_CAPTURE -> {
                final var rator = (ACapture) agent;
                yield duplex(rator.a, rator.d, rator::interact, p, thunk, heart);
            }
            case K_STRICT_OP2 -> {
                final var rator = (AStrictOp2) agent;
                yield duplex(rator.a, rator.c, rator::interact, p, thunk, heart);
            }
            case K_DO_UPDATE -> {
                final var rator = (ADoUpdate) agent;
                yield duplex(rator.a, rator.c, rator::interact, p, thunk, heart);
            }
            case K_DO_INSERT -> {
                final var rator = (ADoInsert) agent;
                yield duplex(rator.a, rator.c, rator::interact, p, thunk, heart);
            }
            case K_DO_RANGE -> {
                final var rator = (ADoRange) agent;
                yield duplex(rator.a, rator.c, rator::interact, p, thunk, heart);
            }
            case K_DO_RANGE_FROM -> {
                final var rator = (ADoRangeFrom) agent;
                yield simplex(rator.a, rator::interact, p, thunk, heart);
            }
            case K_DO_RANGE_TO -> {
                final var rator = (ADoRangeTo) agent;
                yield simplex(rator.a, rator::interact, p, thunk, heart);
            }
            case K_MATCH -> {
                final var rator = (AMatcher) agent;
                yield simplex(rator.a, rator::interact, p, thunk, heart);
            }
            case K_CONSTRUCTOR_RESOLVER -> {
                final var rator = (AConstructorResolver) agent;
                yield simplex(rator.a, rator::interact, p, thunk, heart);
            }
            case K_SELECT -> {
                final var rator = (ASelector) agent;
                yield simplex(rator.a, rator::interact, p, thunk, heart);
            }
            case K_DUPLICATOR -> {
                yield sync((ADuplicator) agent, p, thunk, heart);
            }
            case K_REFERENCE -> {
                final var ref = (AReference) agent;
                yield (Thunk) () -> {
                    countInteraction();
                    ref.interact(p);
                    return reduce(p, thunk, heart);
                };
            }
            case K_EXPANSION -> {
                final var exp = (AExpansion) agent;
                yield (Thunk) () -> {
                    countInteraction();
                    exp.interact(p);
                    return reduce(p, thunk, heart);
                };
            }
            default -> {
                if (isWhnf(agent)) {
                    yield thunk;
                }
                yield crash("No such agent kind: %d", (int) agent.kind);
            }
        };
    }

    private static Thunk simplex(
            final Consumer target,
            final Runnable interactor,
            final Consumer p,
            final Thunk thunk,
            final Heart heart) {
        if (isWhnf(target.chase())) {
            return () -> {
                countInteraction();
                interactor.run();
                return reduce(p, thunk, heart);
            };
        }
        return () -> reduce(target, () -> {
            countInteraction();
            interactor.run();
            return reduce(p, thunk, heart);
        }, heart);
    }

    private static Thunk duplex(
            final Consumer left,
            final Consumer right,
            final Runnable interactor,
            final Consumer p,
            final Thunk thunk,
            final Heart heart) {
        final boolean isLeftWhnf = isWhnf(left.chase());
        final boolean isRightWhnf = isWhnf(right.chase());
        if (isLeftWhnf && isRightWhnf) {
            return () -> {
                countInteraction();
                interactor.run();
                return reduce(p, thunk, heart);
            };
        }
        if (isRightWhnf) {
            return () -> reduce(left, () -> {
                countInteraction();
                interactor.run();
                return reduce(p, thunk, heart);
            }, heart);
        }
        if (isLeftWhnf) {
            return () -> reduce(right, () -> {
                countInteraction();
                interactor.run();
                return reduce(p, thunk, heart);
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
                        countInteraction();
                        interactor.run();
                        return reduce(p, thunk, heart);
                    });
                }
                // The frame has not been promoted; unlinke the frame from the list & reduce the
                // right operand inline.
                heart.unlink(frame);
                return reduce(right, () -> {
                    countInteraction();
                    interactor.run();
                    return reduce(p, thunk, heart);
                }, heart);
            }, heart);
        };
    }

    private static Bounce sync(
            final ADuplicator dup,
            final Consumer p,
            final Thunk thunk,
            final Heart heart) {
        final var mine = new CompletableFuture<Void>();
        // A compare-and-exchange fast path does not deliver a measurable change in wall time.
        final var owner = dup.sync.compareAndExchange(null, mine);
        if (owner == null) {
            return (Thunk) () -> reduce(dup.a, () -> {
                countInteraction();
                dup.interact();
                mine.complete(null);
                return reduce(p, thunk, heart);
            }, heart);
        }
        return new Await(owner, () -> reduce(p, thunk, heart));
    }

    private static final byte
    // Operators.
    K_REFERENCE = 0, K_STRICT_OP1 = 1, K_STRICT_OP2 = 2, K_IF_THEN_ELSE = 3, K_EXPANSION = 4,
            K_NOT = 5, K_AND = 6, K_OR = 7, K_DO_UPDATE = 8, K_DO_INSERT = 9, K_DO_RANGE = 10,
            K_DO_RANGE_FROM = 11, K_DO_RANGE_TO = 12, K_APPLICATOR = 13, K_STRICT_APPLICATOR = 14,
            K_RESOLVER = 15, K_CAPTURE = 16, K_MATCH = 17, K_CONSTRUCTOR_RESOLVER = 18,
            K_SELECT = 19, K_DUPLICATOR = 20,
            // Data.
            K_LAMBDA = 21, K_END_OF_LIST = 22, K_TRUE = 23, K_FALSE = 24, K_INTEGER = 25,
            K_BIG_INTEGER = 26, K_STRING = 27, K_UPDATE = 28, K_INSERT = 29, K_RANGE = 30,
            K_RANGE_FROM = 31, K_RANGE_TO = 32, K_RANGE_FULL = 33, K_IDENTITY = 34,
            K_CONSTRUCTOR = 35, K_SUPERPOSITION = 36;

    public abstract static sealed class Agent permits
            // Operators.
            AReference, AStrictOp1, AStrictOp2, AIfThenElse, AExpansion, ANot, AAnd, AOr, ADoUpdate,
            ADoInsert, ADoRange, ADoRangeFrom, ADoRangeTo, AApplicator, AStrictApplicator,
            AResolver, ACapture, AMatcher, AConstructorResolver, ASelector, ADuplicator,
            // Data.
            ALambda, AEndOfList, ATrue, AFalse, AInteger, ABigInteger, AString, AUpdate, AInsert,
            ARange, ARangeFrom, ARangeTo, ARangeFull, AIdentity, AConstructor, ASuperposition {
        // The agent kind for faster dispatch.
        public final byte kind;

        private Agent(final byte kind) {
            this.kind = kind;
        }
    }

    public static final class AReference extends Agent {
        public final String name;
        public final Producer a;

        public AReference(final String name) {
            super(K_REFERENCE);
            this.name = name;
            this.a = new Producer(this);
        }

        private void interact(final Consumer p) {
            final Template body = BOOK.get(name);
            if (body == null) {
                crash("Cannot resolve a reference to `%s`", name);
            }
            body.materialize(p, NO_IMPORTS);
        }
    }

    public static final class AStrictOp1 extends Agent {
        public final StrictOp1 op;
        public final Consumer a;
        public final Producer b;

        public AStrictOp1(final StrictOp1 op) {
            super(K_STRICT_OP1);
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
                        case STRING_OF -> forwardString(op1.b, "true");
                        case HASH -> forwardInteger(op1.b, U64.one());
                        default -> reject(data);
                    }
                }
                case AFalse _ -> {
                    switch (op1.op) {
                        case STRING_OF -> forwardString(op1.b, "false");
                        case HASH -> forwardInteger(op1.b, U64.zero());
                        default -> reject(data);
                    }
                }
                case AInteger i -> {
                    switch (op1.op) {
                        case STRING_OF -> forwardString(op1.b, i.data.show());
                        case STRING_OF_CHARACTER -> {
                            if (i.ty() == U8) {
                                forwardString(op1.b, MyString.ofByte(i.data.toInt()));
                            } else {
                                reject(data);
                            }
                        }
                        case NEGATE -> forwardInteger(op1.b, i.data.negate());
                        case SIGNUM -> {
                            if (i.ty().isSigned) {
                                forwardInteger(op1.b, i.data.signum());
                            } else {
                                reject(data);
                            }
                        }
                        case ABS -> {
                            if (i.ty().isSigned) {
                                forwardInteger(op1.b, i.data.abs());
                            } else {
                                reject(data);
                            }
                        }
                        case FFS -> forwardInteger(op1.b, i.data.ffs());
                        case CLZ -> forwardInteger(op1.b, i.data.clz());
                        case CTZ -> forwardInteger(op1.b, i.data.ctz());
                        case CLRSB -> forwardInteger(op1.b, i.data.clrsb());
                        case POPCOUNT -> forwardInteger(op1.b, i.data.popcount());
                        case PARITY -> forwardInteger(op1.b, i.data.parity());
                        case LENGTH -> forwardInteger(op1.b, new Value(U64, i.data.bitLength()));
                        case HASH -> forwardInteger(op1.b, new Value(U64, i.data.hash64()));
                        default -> reject(data);
                    }
                }
                case ABigInteger i -> {
                    switch (op1.op) {
                        case STRING_OF -> forwardString(op1.b, i.data.show());
                        case NEGATE -> forwardBigInteger(op1.b, i.data.negate());
                        case SIGNUM -> forwardBigInteger(op1.b, i.data.signum());
                        case ABS -> forwardBigInteger(op1.b, i.data.abs());
                        case POPCOUNT -> forwardInteger(op1.b, new Value(U64, i.data.popcount()));
                        case PARITY -> forwardInteger(op1.b, new Value(U64, i.data.parity()));
                        case LENGTH -> forwardInteger(op1.b, new Value(U64, i.data.bitLength()));
                        case HASH -> forwardInteger(op1.b, new Value(U64, i.data.hash64()));
                        default -> reject(data);
                    }
                }
                case AString s -> {
                    switch (op1.op) {
                        case STRING_OF -> op1.b.forward(s.a);
                        case LENGTH -> forwardInteger(op1.b, new Value(U64, s.data.length()));
                        case PANIC -> panic("User panic: %s", s.data.toString());
                        case HASH -> forwardInteger(op1.b, new Value(U64, s.data.hash64()));
                        default -> reject(data);
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
                default -> {
                    reject(data);
                }
            }
        }

        private void reject(final Agent data) {
            final AStrictOp1 op1 = this;
            if (isMachineData(data)) {
                crash("Operand not welcome: %s", describe(data));
            } else if (isUserData(data)) {
                typeError(op1.op.describe(), data);
            } else if (isOperator(data)) {
                crash("Operand unresolved: %s", describe(data));
            } else {
                throw new IllegalStateException();
            }
        }
    }

    public static final class AStrictOp2 extends Agent {
        public final StrictOp2 op;
        public final Consumer a;
        public final Producer b;
        public final Consumer c;

        public AStrictOp2(final StrictOp2 op) {
            super(K_STRICT_OP2);
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
            } catch (final MyString.LengthTooBig e) {
                panic("Length out of bounds: %s", op.describe());
            } catch (final Primitives.IndexOutOfBounds e) {
                panic("Index out of bounds: %s", op.describe());
            } catch (final Primitives.RangeOutOfBounds e) {
                panic("Range out of bounds: %s", op.describe());
            }
        }

        private void interactAux() {
            final AStrictOp2 op2 = this;
            final Agent left = op2.a.chase();
            final Agent right = op2.c.chase();
            switch (left) {
                case ATrue b1 -> {
                    switch (right) {
                        case ATrue b2 -> interact(b1, b2);
                        case AFalse b2 -> interact(b1, b2);
                        case ASuperposition sup -> interact(b1, sup);
                        default -> reject(left, right);
                    }
                }
                case AFalse b1 -> {
                    switch (right) {
                        case ATrue b2 -> interact(b1, b2);
                        case AFalse b2 -> interact(b1, b2);
                        case ASuperposition sup -> interact(b1, sup);
                        default -> reject(left, right);
                    }
                }
                case AInteger i1 -> {
                    switch (right) {
                        case ATrue b2 -> interact(i1, b2);
                        case AFalse b2 -> interact(i1, b2);
                        case AInteger i2 -> interact(i1, i2);
                        case ABigInteger i2 -> interact(i1, i2);
                        case ARange rng -> interact(i1, rng);
                        case ARangeFrom rng -> interact(i1, rng);
                        case ARangeTo rng -> interact(i1, rng);
                        case ARangeFull rng -> interact(i1, rng);
                        case ASuperposition sup -> interact(i1, sup);
                        default -> reject(left, right);
                    }
                }
                case ABigInteger i1 -> {
                    switch (right) {
                        case ATrue b2 -> interact(i1, b2);
                        case AFalse b2 -> interact(i1, b2);
                        case AInteger i2 -> interact(i1, i2);
                        case ABigInteger i2 -> interact(i1, i2);
                        case ARange rng -> interact(i1, rng);
                        case ARangeFrom rng -> interact(i1, rng);
                        case ARangeTo rng -> interact(i1, rng);
                        case ARangeFull rng -> interact(i1, rng);
                        case ASuperposition sup -> interact(i1, sup);
                        default -> reject(left, right);
                    }
                }
                case AString s1 -> {
                    switch (right) {
                        case AInteger i -> interact(s1, i);
                        case AString s2 -> interact(s1, s2);
                        case AUpdate upd -> interact(s1, upd);
                        case AInsert ins -> interact(s1, ins);
                        case ARange rng -> interact(s1, rng);
                        case ARangeFrom rng -> interact(s1, rng);
                        case ARangeTo rng -> interact(s1, rng);
                        case ARangeFull rng -> interact(s1, rng);
                        case ASuperposition sup -> interact(s1, sup);
                        default -> reject(left, right);
                    }
                }
                case ASuperposition sup -> {
                    interact(sup);
                }
                default -> {
                    reject(left, right);
                }
            }
        }

        private void interact(final ATrue b1, final ATrue b2) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                case EQUALS -> op2.b.forward(ATrue.INSTANCE.a);
                case NOT_EQUALS -> op2.b.forward(AFalse.INSTANCE.a);
                case LESS -> op2.b.forward(AFalse.INSTANCE.a);
                case LESS_OR_EQUALS -> op2.b.forward(ATrue.INSTANCE.a);
                case GREATER -> op2.b.forward(AFalse.INSTANCE.a);
                case GREATER_OR_EQUALS -> op2.b.forward(ATrue.INSTANCE.a);
                case STRICT_OR -> op2.b.forward(ATrue.INSTANCE.a);
                case STRICT_AND -> op2.b.forward(ATrue.INSTANCE.a);
                case STRICT_XOR -> op2.b.forward(AFalse.INSTANCE.a);
                case MIN -> op2.b.forward(ATrue.INSTANCE.a);
                case MAX -> op2.b.forward(ATrue.INSTANCE.a);
                case OFTYPE -> op2.b.forward(b2.a);
                default -> reject(b1, b2);
            }
        }

        private void interact(final ATrue b1, final AFalse b2) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                case EQUALS -> op2.b.forward(AFalse.INSTANCE.a);
                case NOT_EQUALS -> op2.b.forward(ATrue.INSTANCE.a);
                case LESS -> op2.b.forward(AFalse.INSTANCE.a);
                case LESS_OR_EQUALS -> op2.b.forward(AFalse.INSTANCE.a);
                case GREATER -> op2.b.forward(ATrue.INSTANCE.a);
                case GREATER_OR_EQUALS -> op2.b.forward(ATrue.INSTANCE.a);
                case STRICT_OR -> op2.b.forward(ATrue.INSTANCE.a);
                case STRICT_AND -> op2.b.forward(AFalse.INSTANCE.a);
                case STRICT_XOR -> op2.b.forward(ATrue.INSTANCE.a);
                case MIN -> op2.b.forward(AFalse.INSTANCE.a);
                case MAX -> op2.b.forward(ATrue.INSTANCE.a);
                case OFTYPE -> op2.b.forward(b2.a);
                default -> reject(b1, b2);
            }
        }

        private void interact(final ATrue b1, final ASuperposition sup) {
            final AStrictOp2 op2 = this;
            final var op2x = new AStrictOp2(op2.op);
            final var op2xx = new AStrictOp2(op2.op);
            final var supx = sup; // reuse
            op2.b.forward(supx.a);
            op2x.c.setProducer(sup.b.producer());
            op2xx.c.setProducer(sup.c.producer());
            supx.b.setProducer(op2x.b);
            supx.c.setProducer(op2xx.b);
            op2x.a.setProducer(ATrue.INSTANCE.a);
            op2xx.a.setProducer(ATrue.INSTANCE.a);
        }

        private void interact(final AFalse b1, final ATrue b2) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                case EQUALS -> op2.b.forward(AFalse.INSTANCE.a);
                case NOT_EQUALS -> op2.b.forward(ATrue.INSTANCE.a);
                case LESS -> op2.b.forward(ATrue.INSTANCE.a);
                case LESS_OR_EQUALS -> op2.b.forward(ATrue.INSTANCE.a);
                case GREATER -> op2.b.forward(AFalse.INSTANCE.a);
                case GREATER_OR_EQUALS -> op2.b.forward(AFalse.INSTANCE.a);
                case STRICT_OR -> op2.b.forward(ATrue.INSTANCE.a);
                case STRICT_AND -> op2.b.forward(AFalse.INSTANCE.a);
                case STRICT_XOR -> op2.b.forward(ATrue.INSTANCE.a);
                case MIN -> op2.b.forward(AFalse.INSTANCE.a);
                case MAX -> op2.b.forward(ATrue.INSTANCE.a);
                case OFTYPE -> op2.b.forward(b2.a);
                default -> reject(b1, b2);
            }
        }

        private void interact(final AFalse b1, final AFalse b2) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                case EQUALS -> op2.b.forward(ATrue.INSTANCE.a);
                case NOT_EQUALS -> op2.b.forward(AFalse.INSTANCE.a);
                case LESS -> op2.b.forward(AFalse.INSTANCE.a);
                case LESS_OR_EQUALS -> op2.b.forward(ATrue.INSTANCE.a);
                case GREATER -> op2.b.forward(AFalse.INSTANCE.a);
                case GREATER_OR_EQUALS -> op2.b.forward(ATrue.INSTANCE.a);
                case STRICT_OR -> op2.b.forward(AFalse.INSTANCE.a);
                case STRICT_AND -> op2.b.forward(AFalse.INSTANCE.a);
                case STRICT_XOR -> op2.b.forward(AFalse.INSTANCE.a);
                case MIN -> op2.b.forward(AFalse.INSTANCE.a);
                case MAX -> op2.b.forward(AFalse.INSTANCE.a);
                case OFTYPE -> op2.b.forward(b2.a);
                default -> reject(b1, b2);
            }
        }

        private void interact(final AFalse b1, final ASuperposition sup) {
            final AStrictOp2 op2 = this;
            final var op2x = new AStrictOp2(op2.op);
            final var op2xx = new AStrictOp2(op2.op);
            final var supx = sup; // reuse
            op2.b.forward(supx.a);
            op2x.c.setProducer(sup.b.producer());
            op2xx.c.setProducer(sup.c.producer());
            supx.b.setProducer(op2x.b);
            supx.c.setProducer(op2xx.b);
            op2x.a.setProducer(AFalse.INSTANCE.a);
            op2xx.a.setProducer(AFalse.INSTANCE.a);
        }

        private void interact(final AInteger i1, final ATrue b2) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                case OFTYPE -> {
                    forwardInteger(op2.b, i1.ty().one());
                }
                default -> {
                    reject(i1, b2);
                }
            }
        }

        private void interact(final AInteger i1, final AFalse b2) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                case OFTYPE -> {
                    forwardInteger(op2.b, i1.ty().zero());
                }
                default -> {
                    reject(i1, b2);
                }
            }
        }

        private void interact(final AInteger i1, final AInteger i2) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                case OFTYPE -> {
                    if (i1.ty() != i2.ty()) {
                        forwardInteger(op2.b, i2.data.convertTo(i1.ty()));
                    } else {
                        op2.b.forward(i2.a);
                    }
                }
                case INDEX -> {
                    if (i2.ty() != U64) {
                        reject(i1, i2);
                    }
                    forwardBoolean(op2.b, i1.data.at(i2.value()));
                }
                case MAKE8 -> {
                    if (i1.ty() != U64 || i2.ty() != U8) {
                        reject(i1, i2);
                    }
                    final long count = i1.value();
                    final long element = i2.value();
                    forwardString(op2.b, MyString.makePacked8(count, element));
                }
                case MAKE16 -> {
                    if (i1.ty() != U64 || i2.ty() != U16) {
                        reject(i1, i2);
                    }
                    final long count = i1.value();
                    final long element = i2.value();
                    forwardString(op2.b, MyString.makePacked16(count, element));
                }
                case MAKE32 -> {
                    if (i1.ty() != U64 || i2.ty() != U32) {
                        reject(i1, i2);
                    }
                    final long count = i1.value();
                    final long element = i2.value();
                    forwardString(op2.b, MyString.makePacked32(count, element));
                }
                case MAKE64 -> {
                    if (i1.ty() != U64 || i2.ty() != U64) {
                        reject(i1, i2);
                    }
                    final long count = i1.value();
                    final long element = i2.value();
                    forwardString(op2.b, MyString.makePacked64(count, element));
                }
                default -> {
                    if (i1.ty() != i2.ty()) {
                        reject(i1, i2);
                    }
                    final IntegerTy ty = i1.ty();
                    final long x = i1.value(), y = i2.value();
                    switch (op2.op) {
                        case ADD -> forwardInteger(op2.b, ty.add(x, y));
                        case SUBTRACT -> forwardInteger(op2.b, ty.subtract(x, y));
                        case MULTIPLY -> forwardInteger(op2.b, ty.multiply(x, y));
                        case DIVIDE -> forwardInteger(op2.b, ty.divide(x, y));
                        case REMAINDER -> forwardInteger(op2.b, ty.remainder(x, y));
                        case STRICT_OR -> forwardInteger(op2.b, ty.or(x, y));
                        case STRICT_AND -> forwardInteger(op2.b, ty.and(x, y));
                        case STRICT_XOR -> forwardInteger(op2.b, ty.xor(x, y));
                        case SHIFT_LEFT -> forwardInteger(op2.b, ty.shiftLeft(x, y));
                        case SHIFT_RIGHT -> forwardInteger(op2.b, ty.shiftRight(x, y));
                        case EQUALS -> forwardBoolean(op2.b, x == y);
                        case NOT_EQUALS -> forwardBoolean(op2.b, x != y);
                        case LESS -> forwardBoolean(op2.b, ty.compare(x, y) < 0);
                        case LESS_OR_EQUALS -> forwardBoolean(op2.b, ty.compare(x, y) <= 0);
                        case GREATER -> forwardBoolean(op2.b, ty.compare(x, y) > 0);
                        case GREATER_OR_EQUALS -> forwardBoolean(op2.b, ty.compare(x, y) >= 0);
                        case MIN -> op2.b.forward(ty.compare(x, y) <= 0 ? i1.a : i2.a);
                        case MAX -> op2.b.forward(ty.compare(x, y) >= 0 ? i1.a : i2.a);
                        default -> reject(i1, i2);
                    }
                }
            }
        }

        private void interact(final AInteger i1, final ABigInteger i2) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                case OFTYPE -> {
                    forwardInteger(op2.b, i2.data.convertTo(i1.ty()));
                }
                default -> {
                    reject(i1, i2);
                }
            }
        }

        private void interact(final AInteger i1, final ARange rng) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                case SLICE -> {
                    op2.b.forward(i1.slice(rng.start, rng.end, rng.inclusive).a);
                }
                default -> {
                    reject(i1, rng);
                }
            }
        }

        private void interact(final AInteger i1, final ARangeFrom rng) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                case SLICE -> {
                    final boolean inclusive = false;
                    op2.b.forward(i1.slice(rng.start, i1.ty().bits, inclusive).a);
                }
                default -> {
                    reject(i1, rng);
                }
            }
        }

        private void interact(final AInteger i1, final ARangeTo rng) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                case SLICE -> {
                    op2.b.forward(i1.slice(0, rng.end, rng.inclusive).a);
                }
                default -> {
                    reject(i1, rng);
                }
            }
        }

        private void interact(final AInteger i1, final ARangeFull rng) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                case SLICE -> {
                    op2.b.forward(i1.a);
                }
                default -> {
                    reject(i1, rng);
                }
            }
        }

        private void interact(final AInteger i1, final ASuperposition sup) {
            final AStrictOp2 op2 = this;
            final var op2x = new AStrictOp2(op2.op);
            final var op2xx = new AStrictOp2(op2.op);
            final var supx = sup; // reuse
            op2.b.forward(supx.a);
            op2x.c.setProducer(sup.b.producer());
            op2xx.c.setProducer(sup.c.producer());
            supx.b.setProducer(op2x.b);
            supx.c.setProducer(op2xx.b);
            op2x.a.setProducer(i1.a);
            op2xx.a.setProducer(i1.a);
        }

        private void interact(final ABigInteger i1, final ATrue b2) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                case OFTYPE -> {
                    forwardBigInteger(op2.b, MyBigInteger.one());
                }
                default -> {
                    reject(i1, b2);
                }
            }
        }

        private void interact(final ABigInteger i1, final AFalse b2) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                case OFTYPE -> {
                    forwardBigInteger(op2.b, MyBigInteger.zero());
                }
                default -> {
                    reject(i1, b2);
                }
            }
        }

        private void interact(final ABigInteger i1, final AInteger i2) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                case INDEX -> {
                    if (i2.ty() != U64) {
                        reject(i1, i2);
                    }
                    forwardBoolean(op2.b, i1.data.at(i2.value()));
                }
                case OFTYPE -> {
                    forwardBigInteger(op2.b, MyBigInteger.of(i2.data));
                }
                default -> {
                    reject(i1, i2);
                }
            }
        }

        private void interact(final ABigInteger i1, final ABigInteger i2) {
            final AStrictOp2 op2 = this;
            final MyBigInteger x = i1.data, y = i2.data;
            switch (op2.op) {
                case ADD -> forwardBigInteger(op2.b, x.add(y));
                case SUBTRACT -> forwardBigInteger(op2.b, x.subtract(y));
                case MULTIPLY -> forwardBigInteger(op2.b, x.multiply(y));
                case DIVIDE -> forwardBigInteger(op2.b, x.divide(y));
                case REMAINDER -> forwardBigInteger(op2.b, x.remainder(y));
                case STRICT_OR -> forwardBigInteger(op2.b, x.or(y));
                case STRICT_AND -> forwardBigInteger(op2.b, x.and(y));
                case STRICT_XOR -> forwardBigInteger(op2.b, x.xor(y));
                case SHIFT_LEFT -> forwardBigInteger(op2.b, x.shiftLeft(y));
                case SHIFT_RIGHT -> forwardBigInteger(op2.b, x.shiftRight(y));
                case EQUALS -> forwardBoolean(op2.b, x.equals(y));
                case NOT_EQUALS -> forwardBoolean(op2.b, !x.equals(y));
                case LESS -> forwardBoolean(op2.b, x.compareTo(y) < 0);
                case LESS_OR_EQUALS -> forwardBoolean(op2.b, x.compareTo(y) <= 0);
                case GREATER -> forwardBoolean(op2.b, x.compareTo(y) > 0);
                case GREATER_OR_EQUALS -> forwardBoolean(op2.b, x.compareTo(y) >= 0);
                case MIN -> op2.b.forward(x.compareTo(y) <= 0 ? i1.a : i2.a);
                case MAX -> op2.b.forward(x.compareTo(y) >= 0 ? i1.a : i2.a);
                case OFTYPE -> op2.b.forward(i2.a);
                default -> reject(i1, i2);
            }
        }

        private void interact(final ABigInteger i1, final ARange rng) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                case SLICE -> {
                    op2.b.forward(i1.slice(rng.start, rng.end, rng.inclusive).a);
                }
                default -> {
                    reject(i1, rng);
                }
            }
        }

        private void interact(final ABigInteger i1, final ARangeFrom rng) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                case SLICE -> {
                    forwardBigInteger(op2.b, i1.data.slice(rng.start));
                }
                default -> {
                    reject(i1, rng);
                }
            }
        }

        private void interact(final ABigInteger i1, final ARangeTo rng) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                case SLICE -> {
                    op2.b.forward(i1.slice(0, rng.end, rng.inclusive).a);
                }
                default -> {
                    reject(i1, rng);
                }
            }
        }

        private void interact(final ABigInteger i1, final ARangeFull rng) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                case SLICE -> {
                    op2.b.forward(i1.a);
                }
                default -> {
                    reject(i1, rng);
                }
            }
        }

        private void interact(final ABigInteger i1, final ASuperposition sup) {
            final AStrictOp2 op2 = this;
            final var op2x = new AStrictOp2(op2.op);
            final var op2xx = new AStrictOp2(op2.op);
            final var supx = sup; // reuse
            op2.b.forward(supx.a);
            op2x.c.setProducer(sup.b.producer());
            op2xx.c.setProducer(sup.c.producer());
            supx.b.setProducer(op2x.b);
            supx.c.setProducer(op2xx.b);
            op2x.a.setProducer(i1.a);
            op2xx.a.setProducer(i1.a);
        }

        private void interact(final AString s1, final AInteger i) {
            final AStrictOp2 op2 = this;
            switch (op2.op) {
                case INDEX -> {
                    if (i.ty() != U64) {
                        reject(s1, i);
                    }
                    forwardInteger(op2.b, U8.of(s1.data.at(i.value())));
                }
                case STRCHR -> {
                    if (i.ty() != U8) {
                        reject(s1, i);
                    }
                    final int c = i.data.toInt();
                    forwardInteger(op2.b, I64.of(s1.data.strchr(c)));
                }
                case STRRCHR -> {
                    if (i.ty() != U8) {
                        reject(s1, i);
                    }
                    final int c = i.data.toInt();
                    forwardInteger(op2.b, I64.of(s1.data.strrchr(c)));
                }
                case PREPEND8 -> {
                    if (i.ty() != U8) {
                        reject(s1, i);
                    }
                    forwardString(op2.b, s1.data.prependPacked8(i.value()));
                }
                case PREPEND16 -> {
                    if (i.ty() != U16) {
                        reject(s1, i);
                    }
                    forwardString(op2.b, s1.data.prependPacked16(i.value()));
                }
                case PREPEND32 -> {
                    if (i.ty() != U32) {
                        reject(s1, i);
                    }
                    forwardString(op2.b, s1.data.prependPacked32(i.value()));
                }
                case PREPEND64 -> {
                    if (i.ty() != U64) {
                        reject(s1, i);
                    }
                    forwardString(op2.b, s1.data.prependPacked64(i.value()));
                }
                case APPEND8 -> {
                    if (i.ty() != U8) {
                        reject(s1, i);
                    }
                    forwardString(op2.b, s1.data.appendPacked8(i.value()));
                }
                case APPEND16 -> {
                    if (i.ty() != U16) {
                        reject(s1, i);
                    }
                    forwardString(op2.b, s1.data.appendPacked16(i.value()));
                }
                case APPEND32 -> {
                    if (i.ty() != U32) {
                        reject(s1, i);
                    }
                    forwardString(op2.b, s1.data.appendPacked32(i.value()));
                }
                case APPEND64 -> {
                    if (i.ty() != U64) {
                        reject(s1, i);
                    }
                    forwardString(op2.b, s1.data.appendPacked64(i.value()));
                }
                case REMOVE8 -> {
                    if (i.ty() != U64) {
                        reject(s1, i);
                    }
                    forwardString(op2.b, s1.data.removePacked8(i.value()));
                }
                case REMOVE16 -> {
                    if (i.ty() != U64) {
                        reject(s1, i);
                    }
                    forwardString(op2.b, s1.data.removePacked16(i.value()));
                }
                case REMOVE32 -> {
                    if (i.ty() != U64) {
                        reject(s1, i);
                    }
                    forwardString(op2.b, s1.data.removePacked32(i.value()));
                }
                case REMOVE64 -> {
                    if (i.ty() != U64) {
                        reject(s1, i);
                    }
                    forwardString(op2.b, s1.data.removePacked64(i.value()));
                }
                case READ8 -> {
                    if (i.ty() != U64) {
                        reject(s1, i);
                    }
                    forwardInteger(op2.b, U8.of(s1.data.readPacked8(i.value())));
                }
                case READ16 -> {
                    if (i.ty() != U64) {
                        reject(s1, i);
                    }
                    forwardInteger(op2.b, U16.of(s1.data.readPacked16(i.value())));
                }
                case READ32 -> {
                    if (i.ty() != U64) {
                        reject(s1, i);
                    }
                    forwardInteger(op2.b, U32.of(s1.data.readPacked32(i.value())));
                }
                case READ64 -> {
                    if (i.ty() != U64) {
                        reject(s1, i);
                    }
                    forwardInteger(op2.b, U64.of(s1.data.readPacked64(i.value())));
                }
                case FIND8 -> {
                    if (i.ty() != U8) {
                        reject(s1, i);
                    }
                    forwardInteger(op2.b, I64.of(s1.data.findPacked8(i.value())));
                }
                case FIND16 -> {
                    if (i.ty() != U16) {
                        reject(s1, i);
                    }
                    forwardInteger(op2.b, I64.of(s1.data.findPacked16(i.value())));
                }
                case FIND32 -> {
                    if (i.ty() != U32) {
                        reject(s1, i);
                    }
                    forwardInteger(op2.b, I64.of(s1.data.findPacked32(i.value())));
                }
                case FIND64 -> {
                    if (i.ty() != U64) {
                        reject(s1, i);
                    }
                    forwardInteger(op2.b, I64.of(s1.data.findPacked64(i.value())));
                }
                case RFIND8 -> {
                    if (i.ty() != U8) {
                        reject(s1, i);
                    }
                    forwardInteger(op2.b, I64.of(s1.data.rfindPacked8(i.value())));
                }
                case RFIND16 -> {
                    if (i.ty() != U16) {
                        reject(s1, i);
                    }
                    forwardInteger(op2.b, I64.of(s1.data.rfindPacked16(i.value())));
                }
                case RFIND32 -> {
                    if (i.ty() != U32) {
                        reject(s1, i);
                    }
                    forwardInteger(op2.b, I64.of(s1.data.rfindPacked32(i.value())));
                }
                case RFIND64 -> {
                    if (i.ty() != U64) {
                        reject(s1, i);
                    }
                    forwardInteger(op2.b, I64.of(s1.data.rfindPacked64(i.value())));
                }
                default -> reject(s1, i);
            }
        }

        private void interact(final AString s1, final AString s2) {
            final AStrictOp2 op2 = this;
            final MyString x = s1.data, y = s2.data;
            switch (op2.op) {
                case EQUALS -> forwardBoolean(op2.b, x.equals(y));
                case NOT_EQUALS -> forwardBoolean(op2.b, !x.equals(y));
                case LESS -> forwardBoolean(op2.b, x.compareTo(y) < 0);
                case LESS_OR_EQUALS -> forwardBoolean(op2.b, x.compareTo(y) <= 0);
                case GREATER -> forwardBoolean(op2.b, x.compareTo(y) > 0);
                case GREATER_OR_EQUALS -> forwardBoolean(op2.b, x.compareTo(y) >= 0);
                case MIN -> op2.b.forward(x.compareTo(y) <= 0 ? s1.a : s2.a);
                case MAX -> op2.b.forward(x.compareTo(y) >= 0 ? s1.a : s2.a);
                case OFTYPE -> op2.b.forward(s2.a);
                case PLUS_PLUS -> forwardString(op2.b, x.concat(y));
                case STRCMP -> forwardInteger(op2.b, I64.of(x.compareTo(y)));
                case STRSTR -> forwardInteger(op2.b, I64.of(x.strstr(y)));
                case STRSPN -> forwardInteger(op2.b, I64.of(x.strspn(y)));
                case STRCSPN -> forwardInteger(op2.b, I64.of(x.strcspn(y)));
                case STRPBRK -> forwardInteger(op2.b, I64.of(x.strpbrk(y)));
                case STARTSWITH -> forwardBoolean(op2.b, x.startswith(y));
                case ENDSWITH -> forwardBoolean(op2.b, x.endswith(y));
                default -> reject(s1, s2);
            }
        }

        private void interact(final AString s1, final AUpdate upd) {
            final AStrictOp2 op2 = this;
            final long i = upd.index;
            final Value v = upd.value;
            switch (op2.op) {
                case UPDATE8 -> {
                    if (v.ty() != U8) {
                        reject(s1, upd);
                    }
                    forwardString(op2.b, s1.data.updatePacked8(i, v.a()));
                }
                case UPDATE16 -> {
                    if (v.ty() != U16) {
                        reject(s1, upd);
                    }
                    forwardString(op2.b, s1.data.updatePacked16(i, v.a()));
                }
                case UPDATE32 -> {
                    if (v.ty() != U32) {
                        reject(s1, upd);
                    }
                    forwardString(op2.b, s1.data.updatePacked32(i, v.a()));
                }
                case UPDATE64 -> {
                    if (v.ty() != U64) {
                        reject(s1, upd);
                    }
                    forwardString(op2.b, s1.data.updatePacked64(i, v.a()));
                }
                default -> reject(s1, upd);
            }
        }

        private void interact(final AString s1, final AInsert ins) {
            final AStrictOp2 op2 = this;
            final long i = ins.index;
            final Value v = ins.value;
            switch (op2.op) {
                case INSERT8 -> {
                    if (v.ty() != U8) {
                        reject(s1, ins);
                    }
                    forwardString(op2.b, s1.data.insertPacked8(i, v.a()));
                }
                case INSERT16 -> {
                    if (v.ty() != U16) {
                        reject(s1, ins);
                    }
                    forwardString(op2.b, s1.data.insertPacked16(i, v.a()));
                }
                case INSERT32 -> {
                    if (v.ty() != U32) {
                        reject(s1, ins);
                    }
                    forwardString(op2.b, s1.data.insertPacked32(i, v.a()));
                }
                case INSERT64 -> {
                    if (v.ty() != U64) {
                        reject(s1, ins);
                    }
                    forwardString(op2.b, s1.data.insertPacked64(i, v.a()));
                }
                default -> reject(s1, ins);
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
                    reject(s1, ARangeFull.INSTANCE);
                }
            }
        }

        private void interact(final AString s1, final ASuperposition sup) {
            final AStrictOp2 op2 = this;
            final var op2x = new AStrictOp2(op2.op);
            final var op2xx = new AStrictOp2(op2.op);
            final var supx = sup; // reuse
            op2.b.forward(supx.a);
            op2x.c.setProducer(sup.b.producer());
            op2xx.c.setProducer(sup.c.producer());
            supx.b.setProducer(op2x.b);
            supx.c.setProducer(op2xx.b);
            op2x.a.setProducer(s1.a);
            op2xx.a.setProducer(s1.a);
        }

        private void interact(final ASuperposition sup) {
            final AStrictOp2 op2 = this;
            final var op2x = new AStrictOp2(op2.op);
            final var op2xx = new AStrictOp2(op2.op);
            final var supx = sup; // reuse
            final var dup = new ADuplicator(Label.DELTA);
            op2.b.forward(supx.a);
            dup.a.setProducer(op2.c.producer());
            op2x.a.setProducer(sup.b.producer());
            op2xx.a.setProducer(sup.c.producer());
            supx.b.setProducer(op2x.b);
            supx.c.setProducer(op2xx.b);
            op2x.c.setProducer(dup.b);
            op2xx.c.setProducer(dup.c);
        }

        private void reject(final Agent left, final Agent right) {
            final AStrictOp2 op2 = this;
            if (isMachineData(left)) {
                crash("First operand not welcome: %s", describe(left));
            } else if (isMachineData(right) && !(right instanceof ASuperposition)) {
                crash("Second operand not welcome: %s", describe(right));
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

    public static final class AIfThenElse extends Agent {
        public final Consumer a;
        public final Producer b;
        public final Consumer c;
        public final Consumer d;
        public final Consumer[] values;
        public final Producer[][] binders;

        public AIfThenElse(final int nshared) {
            super(K_IF_THEN_ELSE);
            this.a = new Consumer(null);
            this.b = new Producer(this);
            this.c = new Consumer(null);
            this.d = new Consumer(null);
            this.values = new Consumer[nshared];
            this.binders = new Producer[nshared][2];
            for (int i = 0; i < nshared; i++) {
                this.values[i] = new Consumer(null);
                this.binders[i][0] = new Producer(this);
                this.binders[i][1] = new Producer(this);
            }
        }

        private void interact() {
            final AIfThenElse ite = this;
            final Agent data = ite.a.chase();
            switch (data) {
                case ATrue _ -> {
                    for (int i = 0; i < ite.values.length; i++) {
                        ite.binders[i][0].forward(ite.values[i].producer());
                        ite.binders[i][1].erase();
                    }
                    ite.b.forward(ite.d.producer());
                }
                case AFalse _ -> {
                    for (int i = 0; i < ite.values.length; i++) {
                        ite.binders[i][1].forward(ite.values[i].producer());
                        ite.binders[i][0].erase();
                    }
                    ite.b.forward(ite.c.producer());
                }
                case ASuperposition sup -> {
                    final var itex = new AIfThenElse(ite.values.length);
                    final var itexx = new AIfThenElse(ite.values.length);
                    final var supx = sup; // reuse
                    final var dup = new ADuplicator(Label.DELTA);
                    final var dupx = new ADuplicator(Label.DELTA);
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
                    for (int i = 0; i < ite.values.length; i++) {
                        final var dupv = new ADuplicator(Label.DELTA);
                        dupv.a.setProducer(ite.values[i].producer());
                        itex.values[i].setProducer(dupv.b);
                        itexx.values[i].setProducer(dupv.c);
                        for (int k = 0; k < 2; k++) {
                            final var supv = new ASuperposition();
                            ite.binders[i][k].forward(supv.a);
                            supv.b.setProducer(itex.binders[i][k]);
                            supv.c.setProducer(itexx.binders[i][k]);
                        }
                    }
                }
                default -> {
                    if (isMachineData(data)) {
                        crash("Operand not welcome: %s", describe(data));
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

    public static final class AExpansion extends Agent {
        public final Template template;
        public final Producer a;
        public final Consumer[] imports;

        public AExpansion(final Template template) {
            super(K_EXPANSION);
            this.template = template;
            this.a = new Producer(this);
            this.imports = new Consumer[template.nimports()];
            for (int i = 0; i < imports.length; i++) {
                imports[i] = new Consumer(null);
            }
        }

        private void interact(final Consumer p) {
            final var producers = new Producer[imports.length];
            for (int i = 0; i < imports.length; i++) {
                producers[i] = imports[i].producer();
            }
            template.materialize(p, producers);
        }
    }

    public static final class ANot extends Agent {
        public final Consumer a;
        public final Producer b;

        public ANot() {
            super(K_NOT);
            this.a = new Consumer(null);
            this.b = new Producer(this);
        }

        private void interact() {
            final ANot not = this;
            final Agent data = not.a.chase();
            switch (data) {
                case ATrue _ -> not.b.forward(AFalse.INSTANCE.a);
                case AFalse _ -> not.b.forward(ATrue.INSTANCE.a);
                case AInteger i -> forwardInteger(not.b, i.data.not());
                case ABigInteger i -> forwardBigInteger(not.b, i.data.not());
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

    public static final class AAnd extends Agent {
        public final Consumer a;
        public final Producer b;
        public final Consumer c;

        public AAnd() {
            super(K_AND);
            this.a = new Consumer(null);
            this.b = new Producer(this);
            this.c = new Consumer(null);
        }

        private void interact() {
            final AAnd and = this;
            final Agent data = and.a.chase();
            switch (data) {
                case ATrue _ -> and.b.forward(and.c.producer());
                case AFalse _ -> and.b.forward(AFalse.INSTANCE.a);
                case ASuperposition sup -> {
                    final var andx = new AAnd();
                    final var andxx = new AAnd();
                    final var supx = sup; // reuse
                    final var dup = new ADuplicator(Label.DELTA);
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

    public static final class AOr extends Agent {
        public final Consumer a;
        public final Producer b;
        public final Consumer c;

        public AOr() {
            super(K_OR);
            this.a = new Consumer(null);
            this.b = new Producer(this);
            this.c = new Consumer(null);
        }

        private void interact() {
            final AOr or = this;
            final Agent data = or.a.chase();
            switch (data) {
                case ATrue _ -> or.b.forward(ATrue.INSTANCE.a);
                case AFalse _ -> or.b.forward(or.c.producer());
                case ASuperposition sup -> {
                    final var orx = new AOr();
                    final var orxx = new AOr();
                    final var supx = sup; // reuse
                    final var dup = new ADuplicator(Label.DELTA);
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

    public static final class ADoUpdate extends Agent {
        public final Consumer a;
        public final Producer b;
        public final Consumer c;

        public ADoUpdate() {
            super(K_DO_UPDATE);
            this.a = new Consumer(null);
            this.b = new Producer(this);
            this.c = new Consumer(null);
        }

        private void interact() {
            final ADoUpdate doUpd = this;
            final Agent left = doUpd.a.chase();
            final Agent right = doUpd.c.chase();
            if (left instanceof AInteger i1 && right instanceof AInteger i2 && i1.ty() == U64) {
                final var upd = new AUpdate(i1.value(), i2.data);
                doUpd.b.forward(upd.a);
            } else if (left instanceof ASuperposition sup) {
                final var doUpdx = new ADoUpdate();
                final var doUpdxx = new ADoUpdate();
                final var supx = sup;
                final var dup = new ADuplicator(Label.DELTA);
                doUpd.b.forward(supx.a);
                dup.a.setProducer(doUpd.c.producer());
                doUpdx.a.setProducer(sup.b.producer());
                doUpdxx.a.setProducer(sup.c.producer());
                supx.b.setProducer(doUpdx.b);
                supx.c.setProducer(doUpdxx.b);
                doUpdx.c.setProducer(dup.b);
                doUpdxx.c.setProducer(dup.c);
            } else if (left instanceof AInteger i && i.ty() == U64
                    && right instanceof ASuperposition sup) {
                final var doUpdx = new ADoUpdate();
                final var doUpdxx = new ADoUpdate();
                final var supx = sup;
                doUpd.b.forward(supx.a);
                doUpdx.c.setProducer(sup.b.producer());
                doUpdxx.c.setProducer(sup.c.producer());
                supx.b.setProducer(doUpdx.b);
                supx.c.setProducer(doUpdxx.b);
                doUpdx.a.setProducer(i.a);
                doUpdxx.a.setProducer(i.a);
            } else if (isMachineData(left)) {
                crash("First operand not welcome: %s", describe(left));
            } else if (isMachineData(right) && !(right instanceof ASuperposition)) {
                crash("Second operand not welcome: %s", describe(right));
            } else if (isUserData(left) || isUserData(right)) {
                typeError(describe(doUpd), left, right);
            } else if (isOperator(left)) {
                crash("First operand unresolved: %s", describe(left));
            } else if (isOperator(right)) {
                crash("Second operand unresolved: %s", describe(right));
            } else {
                throw new IllegalStateException();
            }
        }
    }

    public static final class ADoInsert extends Agent {
        public final Consumer a;
        public final Producer b;
        public final Consumer c;

        public ADoInsert() {
            super(K_DO_INSERT);
            this.a = new Consumer(null);
            this.b = new Producer(this);
            this.c = new Consumer(null);
        }

        private void interact() {
            final ADoInsert doIns = this;
            final Agent left = doIns.a.chase();
            final Agent right = doIns.c.chase();
            if (left instanceof AInteger i1 && right instanceof AInteger i2 && i1.ty() == U64) {
                final var ins = new AInsert(i1.value(), i2.data);
                doIns.b.forward(ins.a);
            } else if (left instanceof ASuperposition sup) {
                final var doInsx = new ADoInsert();
                final var doInsxx = new ADoInsert();
                final var supx = sup;
                final var dup = new ADuplicator(Label.DELTA);
                doIns.b.forward(supx.a);
                dup.a.setProducer(doIns.c.producer());
                doInsx.a.setProducer(sup.b.producer());
                doInsxx.a.setProducer(sup.c.producer());
                supx.b.setProducer(doInsx.b);
                supx.c.setProducer(doInsxx.b);
                doInsx.c.setProducer(dup.b);
                doInsxx.c.setProducer(dup.c);
            } else if (left instanceof AInteger i && i.ty() == U64
                    && right instanceof ASuperposition sup) {
                final var doInsx = new ADoInsert();
                final var doInsxx = new ADoInsert();
                final var supx = sup;
                doIns.b.forward(supx.a);
                doInsx.c.setProducer(sup.b.producer());
                doInsxx.c.setProducer(sup.c.producer());
                supx.b.setProducer(doInsx.b);
                supx.c.setProducer(doInsxx.b);
                doInsx.a.setProducer(i.a);
                doInsxx.a.setProducer(i.a);
            } else if (isMachineData(left)) {
                crash("First operand not welcome: %s", describe(left));
            } else if (isMachineData(right) && !(right instanceof ASuperposition)) {
                crash("Second operand not welcome: %s", describe(right));
            } else if (isUserData(left) || isUserData(right)) {
                typeError(describe(doIns), left, right);
            } else if (isOperator(left)) {
                crash("First operand unresolved: %s", describe(left));
            } else if (isOperator(right)) {
                crash("Second operand unresolved: %s", describe(right));
            } else {
                throw new IllegalStateException();
            }
        }
    }

    public static final class ADoRange extends Agent {
        public final Consumer a;
        public final Producer b;
        public final Consumer c;
        public final boolean inclusive;

        public ADoRange(final boolean inclusive) {
            super(K_DO_RANGE);
            this.a = new Consumer(null);
            this.b = new Producer(this);
            this.c = new Consumer(null);
            this.inclusive = inclusive;
        }

        private void interact() {
            final ADoRange doRng = this;
            final Agent left = doRng.a.chase();
            final Agent right = doRng.c.chase();
            if (left instanceof AInteger i1 && right instanceof AInteger i2 && i1.ty() == U64
                    && i2.ty() == U64) {
                final var rng = new ARange(i1.value(), i2.value(), doRng.inclusive);
                doRng.b.forward(rng.a);
            } else if (left instanceof ASuperposition sup) {
                final var doRngx = new ADoRange(doRng.inclusive);
                final var doRngxx = new ADoRange(doRng.inclusive);
                final var supx = sup; // reuse
                final var dup = new ADuplicator(Label.DELTA);
                doRng.b.forward(supx.a);
                dup.a.setProducer(doRng.c.producer());
                doRngx.a.setProducer(sup.b.producer());
                doRngxx.a.setProducer(sup.c.producer());
                supx.b.setProducer(doRngx.b);
                supx.c.setProducer(doRngxx.b);
                doRngx.c.setProducer(dup.b);
                doRngxx.c.setProducer(dup.c);
            } else if (left instanceof AInteger i && i.ty() == U64
                    && right instanceof ASuperposition sup) {
                final var doRngx = new ADoRange(doRng.inclusive);
                final var doRngxx = new ADoRange(doRng.inclusive);
                final var supx = sup; // reuse
                doRng.b.forward(supx.a);
                doRngx.c.setProducer(sup.b.producer());
                doRngxx.c.setProducer(sup.c.producer());
                supx.b.setProducer(doRngx.b);
                supx.c.setProducer(doRngxx.b);
                doRngx.a.setProducer(i.a);
                doRngxx.a.setProducer(i.a);
            } else if (isMachineData(left)) {
                crash("First operand not welcome: %s", describe(left));
            } else if (isMachineData(right) && !(right instanceof ASuperposition)) {
                crash("Second operand not welcome: %s", describe(right));
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

    public static final class ADoRangeFrom extends Agent {
        public final Consumer a;
        public final Producer b;

        public ADoRangeFrom() {
            super(K_DO_RANGE_FROM);
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

    public static final class ADoRangeTo extends Agent {
        public final Consumer a;
        public final Producer b;
        public final boolean inclusive;

        public ADoRangeTo(final boolean inclusive) {
            super(K_DO_RANGE_TO);
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

    public static final class AApplicator extends Agent {
        public final Consumer a;
        public final Producer b;
        public final Consumer c;

        public AApplicator() {
            super(K_APPLICATOR);
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
                    final var dup = new ADuplicator(Label.DELTA);
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

    public static final class AStrictApplicator extends Agent {
        public final Consumer a;
        public final Producer b;
        public final Consumer c;

        public AStrictApplicator() {
            super(K_STRICT_APPLICATOR);
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
                    final var dup = new ADuplicator(Label.DELTA);
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

    public static final class AResolver extends Agent {
        public final Consumer a;
        public final Producer b;
        public final Producer c;
        public final Consumer d;

        public AResolver() {
            super(K_RESOLVER);
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
                    final var supxx = new ASuperposition();
                    final var dup = new ADuplicator(Label.DELTA);
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

    public static final class ACapture extends Agent {
        public final Consumer a;
        public final Producer b;
        public final Producer c;
        public final Consumer d;

        public ACapture() {
            super(K_CAPTURE);
            this.a = new Consumer(null);
            this.b = new Producer(this);
            this.c = new Producer(this);
            this.d = new Consumer(null);
        }

        private void interact() {
            final ACapture cap = this;
            final Agent data = cap.a.chase();
            // NOTE: this `switch` must handle all the data agents, except impossible machine data
            // like `AEndOfList`.
            switch (data) {
                case ALambda lam -> {
                    cap.c.forward(lam.a);
                    cap.b.forward(cap.d.producer());
                }
                case ATrue _ -> {
                    cap.c.forward(ATrue.INSTANCE.a);
                    cap.b.forward(cap.d.producer());
                }
                case AFalse _ -> {
                    cap.c.forward(AFalse.INSTANCE.a);
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
                case AUpdate upd -> {
                    cap.c.forward(upd.a);
                    cap.b.forward(cap.d.producer());
                }
                case AInsert ins -> {
                    cap.c.forward(ins.a);
                    cap.b.forward(cap.d.producer());
                }
                case ARange rng -> {
                    cap.c.forward(rng.a);
                    cap.b.forward(cap.d.producer());
                }
                case ARangeFrom rng -> {
                    cap.c.forward(rng.a);
                    cap.b.forward(cap.d.producer());
                }
                case ARangeTo rng -> {
                    cap.c.forward(rng.a);
                    cap.b.forward(cap.d.producer());
                }
                case ARangeFull _ -> {
                    cap.c.forward(ARangeFull.INSTANCE.a);
                    cap.b.forward(cap.d.producer());
                }
                case AIdentity _ -> {
                    cap.c.forward(AIdentity.INSTANCE.a);
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
                    final var supxx = new ASuperposition();
                    final var dup = new ADuplicator(Label.DELTA);
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

    public static final class AMatcher extends Agent {
        public final String[] names; // interned
        public final Consumer a;
        public final Producer b;
        public final Consumer[] handlers;
        public final int[] arities;
        public final Producer[][] parameters;
        public final Consumer[] values;
        public final Producer[][] binders;

        public AMatcher(final String[] names, final int[] arities, final int nshared) {
            super(K_MATCH);
            this.names = names;
            this.a = new Consumer(null);
            this.b = new Producer(this);
            this.handlers = new Consumer[names.length];
            this.arities = arities;
            this.parameters = new Producer[names.length][];
            for (int i = 0; i < names.length; i++) {
                assert names[i] == names[i].intern()
                        : String.format("Match case name not interned: `%s`", names[i]);
                handlers[i] = new Consumer(null);
                parameters[i] = new Producer[arities[i]];
                for (int j = 0; j < arities[i]; j++) {
                    parameters[i][j] = new Producer(this);
                }
            }
            this.values = new Consumer[nshared];
            this.binders = new Producer[nshared][names.length];
            for (int i = 0; i < nshared; i++) {
                this.values[i] = new Consumer(null);
                for (int j = 0; j < names.length; j++) {
                    this.binders[i][j] = new Producer(this);
                }
            }
        }

        private void interact() {
            final AMatcher mat = this;
            final Agent data = mat.a.chase();
            switch (data) {
                case AConstructor ctr -> {
                    int index = -1;
                    for (int i = 0; i < mat.names.length; i++) {
                        if (mat.names[i] == ctr.name) { // both strings are interned
                            index = i;
                            break;
                        }
                    }
                    if (index == -1) {
                        panic("No matching case for the constructor `%s`", ctr.name);
                    }
                    final Producer[] myParameters = mat.parameters[index];
                    if (myParameters.length != ctr.arity()) {
                        crash("Arity mismatch for the constructor `%s`", ctr.name);
                    }
                    for (int i = 0; i < myParameters.length; i++) {
                        myParameters[i].forward(ctr.arguments[i].producer());
                    }
                    for (int i = 0; i < mat.values.length; i++) {
                        mat.binders[i][index].forward(mat.values[i].producer());
                    }
                    mat.b.forward(mat.handlers[index].producer());
                    for (int i = 0; i < mat.names.length; i++) {
                        if (i != index) {
                            for (int j = 0; j < mat.parameters[i].length; j++) {
                                mat.parameters[i][j].erase();
                            }
                            for (int j = 0; j < mat.binders.length; j++) {
                                mat.binders[j][i].erase();
                            }
                        }
                    }
                }
                case ASuperposition sup -> {
                    final var matx = new AMatcher(mat.names, mat.arities, mat.values.length);
                    final var matxx = new AMatcher(mat.names, mat.arities, mat.values.length);
                    final var supx = sup; // reuse
                    mat.b.forward(supx.a);
                    matx.a.setProducer(sup.b.producer());
                    matxx.a.setProducer(sup.c.producer());
                    supx.b.setProducer(matx.b);
                    supx.c.setProducer(matxx.b);
                    for (int i = 0; i < mat.names.length; i++) {
                        final var dup = new ADuplicator(Label.DELTA);
                        dup.a.setProducer(mat.handlers[i].producer());
                        matx.handlers[i].setProducer(dup.b);
                        matxx.handlers[i].setProducer(dup.c);
                        for (int j = 0; j < mat.parameters[i].length; j++) {
                            final var supp = new ASuperposition();
                            mat.parameters[i][j].forward(supp.a);
                            supp.b.setProducer(matx.parameters[i][j]);
                            supp.c.setProducer(matxx.parameters[i][j]);
                        }
                    }
                    for (int i = 0; i < mat.values.length; i++) {
                        final var dupv = new ADuplicator(Label.DELTA);
                        dupv.a.setProducer(mat.values[i].producer());
                        matx.values[i].setProducer(dupv.b);
                        matxx.values[i].setProducer(dupv.c);
                        for (int j = 0; j < mat.names.length; j++) {
                            final var supv = new ASuperposition();
                            mat.binders[i][j].forward(supv.a);
                            supv.b.setProducer(matx.binders[i][j]);
                            supv.c.setProducer(matxx.binders[i][j]);
                        }
                    }
                }
                default -> {
                    if (isMachineData(data)) {
                        crash("Operand not welcome: %s", describe(data));
                    } else if (isUserData(data)) {
                        typeError(describe(mat), data);
                    } else if (isOperator(data)) {
                        crash("Operand unresolved: %s", describe(data));
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }
        }
    }

    public static final class AConstructorResolver extends Agent {
        public final String name; // interned
        public final Consumer a;
        public final Producer b;
        public final Consumer[] arguments;

        public AConstructorResolver(final String name, final int arity) {
            super(K_CONSTRUCTOR_RESOLVER);
            assert name == name.intern()
                    : String.format("Constructor name not interned: `%s`", name);
            this.name = name;
            this.a = new Consumer(null);
            this.b = new Producer(this);
            this.arguments = new Consumer[arity];
            for (int i = 0; i < arity; i++) {
                arguments[i] = new Consumer(null);
            }
        }

        public int arity() {
            return arguments.length;
        }

        private void interact() {
            final AConstructorResolver res = this;
            final Agent data = res.a.chase();
            switch (data) {
                case AEndOfList _ -> {
                    final var ctr = new AConstructor(res.name, res.arity());
                    for (int i = 0; i < res.arity(); i++) {
                        ctr.arguments[i].setProducer(res.arguments[i].producer());
                    }
                    res.b.forward(ctr.a);
                }
                case ASuperposition sup -> {
                    final var resx = new AConstructorResolver(res.name, res.arity());
                    final var resxx = new AConstructorResolver(res.name, res.arity());
                    final var supx = sup; // reuse
                    res.b.forward(supx.a);
                    resx.a.setProducer(sup.b.producer());
                    resxx.a.setProducer(sup.c.producer());
                    supx.b.setProducer(resx.b);
                    supx.c.setProducer(resxx.b);
                    for (int i = 0; i < res.arity(); i++) {
                        final var dup = new ADuplicator(Label.DELTA);
                        dup.a.setProducer(res.arguments[i].producer());
                        resx.arguments[i].setProducer(dup.b);
                        resxx.arguments[i].setProducer(dup.c);
                    }
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

    // For a constructor `name`, forwards the output port to its field at position `index`, checking
    // that the constructor is named as expected.
    public static final class ASelector extends Agent {
        public final String name; // interned
        public final int index;
        public final Consumer a;
        public final Producer b;

        public ASelector(final String name, final int index) {
            super(K_SELECT);
            assert name == name.intern() : String.format("Selector name not interned: `%s`", name);
            this.name = name;
            this.index = index;
            this.a = new Consumer(null);
            this.b = new Producer(this);
        }

        private void interact() {
            final ASelector sel = this;
            final Agent data = sel.a.chase();
            switch (data) {
                case AConstructor ctr -> {
                    if (ctr.name != sel.name) { // both strings are interned
                        panic("No matching case for the constructor `%s`", ctr.name);
                    }
                    sel.b.forward(ctr.arguments[sel.index].producer());
                }
                case ASuperposition sup -> {
                    final var selx = new ASelector(sel.name, sel.index);
                    final var selxx = new ASelector(sel.name, sel.index);
                    final var supx = sup; // reuse
                    sel.b.forward(supx.a);
                    selx.a.setProducer(sup.b.producer());
                    selxx.a.setProducer(sup.c.producer());
                    supx.b.setProducer(selx.b);
                    supx.c.setProducer(selxx.b);
                }
                default -> {
                    if (isMachineData(data)) {
                        crash("Operand not welcome: %s", describe(data));
                    } else if (isUserData(data)) {
                        typeError(describe(sel), data);
                    } else if (isOperator(data)) {
                        crash("Operand unresolved: %s", describe(data));
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }
        }
    }

    public static final class ADuplicator extends Agent {
        private final AtomicReference<CompletableFuture<Void>> sync = new AtomicReference<>();
        public final Label label;
        public final Consumer a;
        public final Producer b;
        public final Producer c;

        public ADuplicator(final Label label) {
            super(K_DUPLICATOR);
            this.label = label;
            this.a = new Consumer(null);
            this.b = new Producer(this);
            this.c = new Producer(this);
        }

        private void interact() {
            final ADuplicator dup = this;
            final Agent data = dup.a.chase();
            // Atomic agents are immutable, so both outputs can share their producer.
            switch (data) {
                case AConstructor ctr -> {
                    if (ctr.isNullary()) {
                        dup.b.forward(ctr.a);
                        dup.c.forward(ctr.a);
                    } else {
                        final var ctrx = ctr; // reuse
                        final var ctrxx = new AConstructor(ctr.name, ctr.arity());
                        for (int i = 0; i < ctr.arity(); i++) {
                            final var dupx = new ADuplicator(dup.label);
                            dupx.a.setProducer(ctr.arguments[i].producer());
                            ctrx.arguments[i].setProducer(dupx.b);
                            ctrxx.arguments[i].setProducer(dupx.c);
                        }
                        dup.b.forward(ctrx.a);
                        dup.c.forward(ctrxx.a);
                    }
                }
                case ASuperposition sup -> {
                    if (dup.label == Label.DELTA) {
                        dup.b.forward(sup.b.producer());
                        dup.c.forward(sup.c.producer());
                    } else {
                        final var supx = sup; // reuse
                        final var supxx = new ASuperposition();
                        final var dupx = new ADuplicator(dup.label);
                        final var dupxx = new ADuplicator(dup.label);
                        dupx.a.setProducer(sup.b.producer());
                        dupxx.a.setProducer(sup.c.producer());
                        supx.b.setProducer(dupx.b);
                        supxx.b.setProducer(dupx.c);
                        supx.c.setProducer(dupxx.b);
                        supxx.c.setProducer(dupxx.c);
                        dup.b.forward(supx.a);
                        dup.c.forward(supxx.a);
                    }
                }
                case ALambda lam -> {
                    final var lamx = new ALambda();
                    final var lamxx = new ALambda();
                    final var sup = new ASuperposition();
                    final var dupx = new ADuplicator(Label.DELTA);
                    lam.b.forward(sup.a);
                    dupx.a.setProducer(lam.c.producer());
                    sup.b.setProducer(lamx.b);
                    sup.c.setProducer(lamxx.b);
                    lamx.c.setProducer(dupx.b);
                    lamxx.c.setProducer(dupx.c);
                    dup.b.forward(lamx.a);
                    dup.c.forward(lamxx.a);
                }
                case AEndOfList _ -> {
                    dup.b.forward(AEndOfList.INSTANCE.a);
                    dup.c.forward(AEndOfList.INSTANCE.a);
                }
                case ATrue _ -> {
                    dup.b.forward(ATrue.INSTANCE.a);
                    dup.c.forward(ATrue.INSTANCE.a);
                }
                case AFalse _ -> {
                    dup.b.forward(AFalse.INSTANCE.a);
                    dup.c.forward(AFalse.INSTANCE.a);
                }
                case AInteger i -> {
                    dup.b.forward(i.a);
                    dup.c.forward(i.a);
                }
                case ABigInteger i -> {
                    dup.b.forward(i.a);
                    dup.c.forward(i.a);
                }
                case AString s -> {
                    dup.b.forward(s.a);
                    dup.c.forward(s.a);
                }
                case AUpdate upd -> {
                    dup.b.forward(upd.a);
                    dup.c.forward(upd.a);
                }
                case AInsert ins -> {
                    dup.b.forward(ins.a);
                    dup.c.forward(ins.a);
                }
                case ARange rng -> {
                    dup.b.forward(rng.a);
                    dup.c.forward(rng.a);
                }
                case ARangeFrom rng -> {
                    dup.b.forward(rng.a);
                    dup.c.forward(rng.a);
                }
                case ARangeTo rng -> {
                    dup.b.forward(rng.a);
                    dup.c.forward(rng.a);
                }
                case ARangeFull _ -> {
                    dup.b.forward(ARangeFull.INSTANCE.a);
                    dup.c.forward(ARangeFull.INSTANCE.a);
                }
                case AIdentity _ -> {
                    dup.b.forward(AIdentity.INSTANCE.a);
                    dup.c.forward(AIdentity.INSTANCE.a);
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

    public static final class ALambda extends Agent {
        public final Producer a;
        public final Producer b;
        public final Consumer c;

        public ALambda() {
            super(K_LAMBDA);
            this.a = new Producer(this);
            this.b = new Producer(this);
            this.c = new Consumer(null);
        }
    }

    public static final class AEndOfList extends Agent {
        public static final AEndOfList INSTANCE = new AEndOfList();

        public final Producer a;

        private AEndOfList() {
            super(K_END_OF_LIST);
            this.a = new Producer(this);
        }
    }

    public static final class ATrue extends Agent {
        public static final ATrue INSTANCE = new ATrue();

        public final Producer a;

        private ATrue() {
            super(K_TRUE);
            this.a = new Producer(this);
        }
    }

    public static final class AFalse extends Agent {
        public static final AFalse INSTANCE = new AFalse();

        public final Producer a;

        private AFalse() {
            super(K_FALSE);
            this.a = new Producer(this);
        }
    }

    public static final class AInteger extends Agent {
        public final Value data;
        public final Producer a;

        public AInteger(final Value data) {
            super(K_INTEGER);
            this.data = data;
            this.a = new Producer(this);
        }

        public IntegerTy ty() {
            return data.ty();
        }

        public long value() {
            return data.a();
        }

        public AInteger slice(final long start, final long end, final boolean inclusive) {
            if (inclusive && end == -1L) {
                throw new Primitives.RangeOutOfBounds();
            }
            return new AInteger(this.data.slice(start, inclusive ? end + 1 : end));
        }
    }

    public static final class ABigInteger extends Agent {
        public final MyBigInteger data;
        public final Producer a;

        public ABigInteger(final MyBigInteger data) {
            super(K_BIG_INTEGER);
            this.data = data;
            this.a = new Producer(this);
        }

        public ABigInteger slice(final long start, final long end, final boolean inclusive) {
            if (inclusive && end == -1L) {
                throw new Primitives.RangeOutOfBounds();
            }
            return new ABigInteger(this.data.slice(start, inclusive ? end + 1 : end));
        }
    }

    public static final class AString extends Agent {
        public final MyString data;
        public final Producer a;

        public AString(final MyString data) {
            super(K_STRING);
            this.data = data;
            this.a = new Producer(this);
        }

        public AString(final String s) {
            this(MyString.ofAscii(s));
        }

        public AString slice(final long start, final long end, final boolean inclusive) {
            if (inclusive && end == -1L) {
                throw new Primitives.RangeOutOfBounds();
            }
            return new AString(this.data.slice(start, inclusive ? end + 1 : end));
        }
    }

    public static final class AUpdate extends Agent {
        public final long index;
        public final Value value;
        public final Producer a;

        public AUpdate(final long index, final Value value) {
            super(K_UPDATE);
            this.index = index;
            this.value = value;
            this.a = new Producer(this);
        }
    }

    public static final class AInsert extends Agent {
        public final long index;
        public final Value value;
        public final Producer a;

        public AInsert(final long index, final Value value) {
            super(K_INSERT);
            this.index = index;
            this.value = value;
            this.a = new Producer(this);
        }
    }

    public static final class ARange extends Agent {
        public final long start, end;
        public final boolean inclusive;
        public final Producer a;

        public ARange(final long start, final long end, final boolean inclusive) {
            super(K_RANGE);
            this.start = start;
            this.end = end;
            this.inclusive = inclusive;
            this.a = new Producer(this);
        }
    }

    public static final class ARangeFrom extends Agent {
        public final long start;
        public final Producer a;

        public ARangeFrom(final long start) {
            super(K_RANGE_FROM);
            this.start = start;
            this.a = new Producer(this);
        }
    }

    public static final class ARangeTo extends Agent {
        public final long end;
        public final boolean inclusive;
        public final Producer a;

        public ARangeTo(final long end, final boolean inclusive) {
            super(K_RANGE_TO);
            this.end = end;
            this.inclusive = inclusive;
            this.a = new Producer(this);
        }
    }

    public static final class ARangeFull extends Agent {
        public static final ARangeFull INSTANCE = new ARangeFull();

        public final Producer a;

        private ARangeFull() {
            super(K_RANGE_FULL);
            this.a = new Producer(this);
        }
    }

    public static final class AIdentity extends Agent {
        public static final AIdentity INSTANCE = new AIdentity();

        public final Producer a;

        private AIdentity() {
            super(K_IDENTITY);
            this.a = new Producer(this);
        }
    }

    private static final class ASuperposition extends Agent {
        // Conceptually, every superposition has label `Label.DELTA`, so there is no technical
        // reason to have this object field.
        // public final Label label;
        public final Producer a;
        public final Consumer b;
        public final Consumer c;

        private ASuperposition() {
            super(K_SUPERPOSITION);
            this.a = new Producer(this);
            this.b = new Consumer(null);
            this.c = new Consumer(null);
        }
    }

    public static final class AConstructor extends Agent {
        public final String name; // interned
        public final Producer a;
        public final Consumer[] arguments;

        public AConstructor(final String name, final int arity) {
            super(K_CONSTRUCTOR);
            assert name == name.intern()
                    : String.format("Constructor name not interned: `%s`", name);
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

    private static boolean isOperator(final Agent agent) {
        return switch (agent) {
            case AReference _,AStrictOp1 _,AStrictOp2 _,AIfThenElse _,AExpansion _,ANot _,AAnd _,AOr _,ADoUpdate _,ADoInsert _,ADoRange _,ADoRangeFrom _,ADoRangeTo _,AApplicator _,AStrictApplicator _,AResolver _,ACapture _,AMatcher _,AConstructorResolver _,ASelector _,ADuplicator _ ->
                true;
            case ALambda _,AEndOfList _,ATrue _,AFalse _,AInteger _,ABigInteger _,AString _,AUpdate _,AInsert _,ARange _,ARangeFrom _,ARangeTo _,ARangeFull _,AIdentity _,AConstructor _,ASuperposition _ ->
                false;
        };
    }

    private static boolean isUserData(final Agent agent) {
        return switch (agent) {
            case ALambda _,ATrue _,AFalse _,AInteger _,ABigInteger _,AString _,AUpdate _,AInsert _,ARange _,ARangeFrom _,ARangeTo _,ARangeFull _,AIdentity _,AConstructor _ ->
                true;
            case AReference _,AStrictOp1 _,AStrictOp2 _,AIfThenElse _,AExpansion _,ANot _,AAnd _,AOr _,ADoUpdate _,ADoInsert _,ADoRange _,ADoRangeFrom _,ADoRangeTo _,AApplicator _,AStrictApplicator _,AResolver _,ACapture _,AMatcher _,AConstructorResolver _,ASelector _,ADuplicator _,AEndOfList _,ASuperposition _ ->
                false;
        };
    }

    private static boolean isMachineData(final Agent agent) {
        return !isOperator(agent) && !isUserData(agent);
    }

    private static boolean isWhnf(final Agent agent) {
        return agent.kind >= K_LAMBDA;
    }

    private static String describe(final Agent agent) {
        return switch (agent) {
            case AReference ref -> "a reference to `" + ref.name + "`";
            case AStrictOp1 op1 -> op1.op.describe();
            case AStrictOp2 op2 -> op2.op.describe();
            case AIfThenElse _ -> "an if-then-else expression";
            case AExpansion _ -> "an unexpanded template";
            case ANot _ -> "logical negation";
            case AAnd _ -> "logical conjunction";
            case AOr _ -> "logical disjunction";
            case ADoUpdate _ -> "update construction";
            case ADoInsert _ -> "insert construction";
            case ADoRange _ -> "bounded-range construction";
            case ADoRangeFrom _ -> "from-range construction";
            case ADoRangeTo _ -> "to-range construction";
            case AApplicator _ -> "an applicator";
            case AStrictApplicator _ -> "a strict applicator";
            case AResolver _ -> "a variable-list resolver";
            case ACapture _ -> "a captured variable";
            case AMatcher _ -> "a case-of expression";
            case AConstructorResolver res ->
                "a variable-list constructor resolver for `" + res.name + "`";
            case ASelector sel ->
                String.format("a field selector for `%s` at %d", sel.name, sel.index);
            case ADuplicator _ -> "a duplicator";
            case ALambda _ -> "a lambda function";
            case AEndOfList _ -> "an end-of-variables marker";
            case ATrue _ -> "the true value";
            case AFalse _ -> "the false value";
            case AInteger i -> Primitives.describe(i.ty());
            case ABigInteger _ -> "a big integer";
            case AString _ -> "a string";
            case AUpdate upd -> String.format(
                    "%s update at index %s with %s",
                    Primitives.describe(upd.value.ty()),
                    Long.toUnsignedString(upd.index),
                    upd.value.show());
            case AInsert ins -> String.format(
                    "%s insert at index %s with %s",
                    Primitives.describe(ins.value.ty()),
                    Long.toUnsignedString(ins.index),
                    ins.value.show());
            case ARange _ -> "a bounded range";
            case ARangeFrom _ -> "a from-range";
            case ARangeTo _ -> "a to-range";
            case ARangeFull _ -> "a full range";
            case AIdentity _ -> "an identity function";
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
        final var message = Arrays.stream(arguments).map(Motor::describe)
                .collect(Collectors.joining(", "));
        return panic("Type error: %s: %s", op, message);
    }

    private static void forwardBoolean(final Producer p, final boolean value) {
        p.forward(value ? ATrue.INSTANCE.a : AFalse.INSTANCE.a);
    }

    private static void forwardInteger(final Producer p, final Value value) {
        p.forward(new AInteger(value).a);
    }

    private static void forwardBigInteger(final Producer p, final MyBigInteger value) {
        p.forward(new ABigInteger(value).a);
    }

    private static void forwardString(final Producer p, final MyString value) {
        p.forward(new AString(value).a);
    }

    private static void forwardString(final Producer p, final String value) {
        p.forward(new AString(value).a);
    }
}
